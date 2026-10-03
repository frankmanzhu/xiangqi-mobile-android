package com.frankzhu.xiangqi.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** What the status line should say, as a value rather than a sentence. */
sealed class GameStatus {
    data class Win(val winner: Side, val reason: GameResultReason) : GameStatus()
    data class Draw(val reason: GameResultReason) : GameStatus()
    data class Reviewing(val ply: Int, val total: Int) : GameStatus()
    data class Check(val side: Side) : GameStatus()
    object Thinking : GameStatus()
    data class SideToMove(val side: Side) : GameStatus()
    object YourMove : GameStatus()
    object ComputerToMove : GameStatus()
}

/** A transient banner shown over the board. */
sealed class GameMessage {
    object EngineMismatch : GameMessage()
    object NotSaved : GameMessage()
    data class Failure(val error: Throwable) : GameMessage()
}

sealed class HintStage {
    object Available : HintStage()
    object Searching : HintStage()
    data class Source(val move: Move) : HintStage()
    data class Destination(val move: Move) : HintStage()
}

interface GameRepository {
    suspend fun load(): GameRecord?
    suspend fun save(record: GameRecord)
    suspend fun delete()
}

fun interface GameSettings {
    fun confirmMoves(): Boolean
}

/** Everything the UI renders for a game, as one immutable snapshot. */
data class GameState(
    val record: GameRecord,
    val position: Position,
    val selectedSquare: Square? = null,
    val legalDestinations: Set<Square> = emptySet(),
    val isThinking: Boolean = false,
    val hintStage: HintStage = HintStage.Available,
    val pendingMove: Move? = null,
    val replayPly: Int? = null,
    val showResult: Boolean = false,
    val message: GameMessage? = null
) {
    val isReplaying: Boolean get() = replayPly != null

    val isLocalTurn: Boolean
        get() = record.mode == GameMode.LOCAL_TWO_PLAYER || position.sideToMove == record.humanSide

    val canInteract: Boolean
        get() = record.isActive && !isThinking && !isReplaying && pendingMove == null && isLocalTurn

    val lastMove: Move? get() = record.moves.lastOrNull()?.let { Move.fromUci(it.uci) }

    val displayedPosition: Position
        get() {
            val ply = replayPly ?: return position
            return runCatching {
                Position.fromFen(record.startingFEN).replaying(record.uciMoves.take(ply))
            }.getOrDefault(position)
        }

    val status: GameStatus
        get() {
            record.result?.let { result ->
                val winner = result.winner ?: return GameStatus.Draw(result.reason)
                return GameStatus.Win(winner, result.reason)
            }
            replayPly?.let { return GameStatus.Reviewing(it, record.moves.size) }
            if (position.isInCheck(position.sideToMove)) return GameStatus.Check(position.sideToMove)
            if (isThinking) return GameStatus.Thinking
            if (record.mode == GameMode.LOCAL_TWO_PLAYER) return GameStatus.SideToMove(position.sideToMove)
            return if (isLocalTurn) GameStatus.YourMove else GameStatus.ComputerToMove
        }
}

/**
 * One game in progress: input handling, the computer's turns, hints, undo,
 * replay, clocks, and persistence. UI-framework free so it can be tested
 * directly; all state changes happen on the [scope]'s dispatcher.
 */
class GameSession(
    record: GameRecord,
    private val repository: GameRepository,
    private val computer: ComputerPlayerClient,
    private val rules: RulesAdjudicator?,
    private val scope: CoroutineScope,
    private val settings: GameSettings = GameSettings { false },
    private val feedback: FeedbackSink = FeedbackSink.None,
    private val now: () -> Instant = Instant::now
) {
    private val _state = MutableStateFlow(
        GameState(
            record = record,
            position = runCatching {
                Position.fromFen(record.startingFEN).replaying(record.uciMoves)
            }.getOrDefault(Position.standard),
            showResult = record.result != null
        )
    )
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var positionVersion = 0L
    private var hintUsedForCurrentPly = false
    private var clockJob: Job? = null

    private val current: GameState get() = _state.value
    val record: GameRecord get() = current.record

    init {
        startClock()
    }

    fun startIfNeeded() {
        startClock()
        val s = current
        if (s.record.mode == GameMode.COMPUTER && !s.isLocalTurn && s.record.isActive) startComputerTurn()
    }

    /** Stops searching and the clock, then writes the record to disk. */
    suspend fun pause() {
        cancelSearch()
        clockJob?.cancel()
        clockJob = null
        _state.update { it.copy(record = it.record.copy(updatedAt = now())) }
        persist()
    }

    /** Releases the clock and any search; call when the session is discarded. */
    fun close() {
        searchJob?.cancel()
        clockJob?.cancel()
    }

    fun tap(square: Square) {
        val s = current
        if (!s.canInteract) return
        val selected = s.selectedSquare
        if (selected != null && square in s.legalDestinations) {
            proposeOrCommit(Move(selected, square))
            return
        }
        if (selected == square) {
            clearSelection()
            return
        }
        val piece = s.position.pieceAt(square)
        if (piece != null && piece.side == s.position.sideToMove) {
            val moves = s.position.legalMoves(square)
            _state.update { it.copy(selectedSquare = square, legalDestinations = moves.map { m -> m.to }.toSet()) }
            feedback.play(FeedbackEvent.PIECE_SELECTED)
        } else {
            clearSelection()
        }
    }

    fun drag(from: Square, to: Square) {
        val s = current
        if (!s.canInteract ||
            s.position.pieceAt(from)?.side != s.position.sideToMove ||
            s.position.legalMoves(from).none { it.to == to }
        ) return
        proposeOrCommit(Move(from, to))
    }

    fun confirmPendingMove() {
        val pending = current.pendingMove ?: return
        _state.update { it.copy(pendingMove = null) }
        scope.launch { commit(pending, null) }
    }

    fun cancelPendingMove() = _state.update { it.copy(pendingMove = null) }

    fun hint() {
        val s = current
        if (s.record.mode != GameMode.COMPUTER || !s.canInteract) return
        when (val stage = s.hintStage) {
            is HintStage.Source -> _state.update { it.copy(hintStage = HintStage.Destination(stage.move)) }
            is HintStage.Destination, HintStage.Searching -> Unit
            HintStage.Available -> {
                _state.update { it.copy(hintStage = HintStage.Searching) }
                val snapshotFen = s.position.fen
                val moves = s.record.uciMoves
                val seed = seedForCurrentPosition(0x48494E54uL)
                scope.launch {
                    try {
                        val move = computer.chooseMove(s.record.startingFEN, moves, ComputerConfiguration(3, seed))
                        if (snapshotFen != current.position.fen) {
                            _state.update { it.copy(hintStage = HintStage.Available) }
                            return@launch
                        }
                        hintUsedForCurrentPly = true
                        _state.update { it.copy(hintStage = HintStage.Source(move)) }
                    } catch (e: CancellationException) {
                        _state.update { it.copy(hintStage = HintStage.Available) }
                        throw e
                    } catch (e: Exception) {
                        _state.update { it.copy(hintStage = HintStage.Available, message = GameMessage.Failure(e)) }
                    }
                }
            }
        }
    }

    suspend fun undo() {
        val s = current
        if (s.record.timeControl != TimeControl.CASUAL || s.record.moves.isEmpty() || s.record.result != null) return
        cancelSearch()
        var removeCount = 1
        if (s.record.mode == GameMode.COMPUTER && s.record.moves.last().side != s.record.humanSide) {
            removeCount = minOf(2, s.record.moves.size)
        }
        val remaining = s.record.moves.dropLast(removeCount)
        val updated = s.record.copy(moves = remaining, updatedAt = now())
        positionVersion++
        _state.update {
            it.copy(
                record = updated,
                position = runCatching {
                    Position.fromFen(updated.startingFEN).replaying(updated.uciMoves)
                }.getOrDefault(Position.standard)
            ).clearedTransient()
        }
        hintUsedForCurrentPly = false
        persist()
    }

    suspend fun resign() {
        val s = current
        if (s.record.result != null) return
        cancelSearch()
        val winner = if (s.record.mode == GameMode.COMPUTER) s.record.humanSide?.opponent else s.position.sideToMove.opponent
        _state.update {
            it.copy(
                record = it.record.copy(result = GameResult(winner, GameResultReason.RESIGNATION), updatedAt = now()),
                showResult = true
            )
        }
        feedback.play(FeedbackEvent.GAME_END)
        persist()
    }

    fun flip() {
        _state.update { it.copy(record = it.record.copy(orientation = it.record.orientation.opponent)) }
        scope.launch { persist() }
    }

    fun showReplay(ply: Int) {
        _state.update {
            it.copy(
                selectedSquare = null,
                legalDestinations = emptySet(),
                replayPly = ply.coerceIn(0, it.record.moves.size)
            )
        }
    }

    fun stepReplay(delta: Int) = showReplay((current.replayPly ?: current.record.moves.size) + delta)

    fun returnToLive() = _state.update { it.copy(replayPly = null) }

    fun dismissResult() = _state.update { it.copy(showResult = false) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun shareText(): String = runCatching { PortableGame.from(current.record).encoded() }.getOrDefault("")

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        _state.update { it.copy(isThinking = false) }
        scope.launch { computer.stop() }
    }

    // region Moves

    private fun proposeOrCommit(move: Move) {
        if (settings.confirmMoves()) {
            _state.update { it.copy(pendingMove = move) }
        } else {
            scope.launch { commit(move, null) }
        }
    }

    private suspend fun commit(move: Move, computerSeed: ULong?) {
        val s = current
        if (s.record.result != null || move !in s.position.legalMoves()) return
        val movingPiece = s.position.pieceAt(move.from) ?: return
        val captured = s.position.pieceAt(move.to)?.kind
        val next = runCatching { s.position.applying(move) }.getOrNull() ?: return
        val recorded = RecordedMove(
            uci = move.uci,
            notation = MoveNotation.display(move, s.position),
            side = movingPiece.side,
            captured = captured,
            hintUsed = if (computerSeed == null) hintUsedForCurrentPly else false,
            engineSelectionSeed = computerSeed,
            committedAt = now()
        )
        positionVersion++
        var updated = s.record.copy(moves = s.record.moves + recorded, updatedAt = now())
        hintUsedForCurrentPly = false
        _state.update { it.copy(record = updated, position = next).clearedTransient() }

        var failure: Throwable? = null
        try {
            val result = if (updated.rulesPolicyID == GameRecord.LEGACY_RULES_POLICY_ID || rules == null) {
                // Preserve the rules explicitly recorded by pre-release saves.
                next.result(repetitionCount(updated, normalizedKey(next)))
            } else {
                rules.result(updated.startingFEN, updated.uciMoves)
            }
            if (result != null) {
                updated = updated.copy(result = result)
                _state.update { it.copy(record = updated, showResult = true) }
            }
        } catch (e: Exception) {
            failure = e
        }
        if (failure != null) _state.update { it.copy(message = GameMessage.Failure(failure)) }

        // One cue per move, so a capture that gives check does not fire twice.
        when {
            updated.result != null -> feedback.play(FeedbackEvent.GAME_END)
            next.isInCheck(next.sideToMove) -> feedback.play(FeedbackEvent.CHECK)
            else -> feedback.play(if (captured == null) FeedbackEvent.MOVE else FeedbackEvent.CAPTURE)
        }
        persist()

        val latest = current
        if (latest.record.mode == GameMode.COMPUTER && !latest.isLocalTurn && latest.record.isActive) {
            startComputerTurn()
        }
    }

    private fun startComputerTurn() {
        val s = current
        if (s.record.mode != GameMode.COMPUTER || s.isLocalTurn || !s.record.isActive || s.isThinking) return
        val version = positionVersion
        val snapshotFen = s.position.fen
        val moves = s.record.uciMoves
        val level = s.record.computerLevel
        val seed = seedForCurrentPosition(s.record.moves.size.toULong())
        searchJob?.cancel()
        _state.update { it.copy(isThinking = true) }
        val job = scope.launch {
            try {
                val move = computer.chooseMove(s.record.startingFEN, moves, ComputerConfiguration(level, seed))
                if (positionVersion != version) return@launch
                _state.update { it.copy(isThinking = false) }
                val now = current
                if (snapshotFen != now.position.fen || move !in now.position.legalMoves()) {
                    _state.update { it.copy(message = GameMessage.EngineMismatch) }
                    return@launch
                }
                commit(move, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (positionVersion != version) return@launch
                _state.update { it.copy(isThinking = false, message = GameMessage.Failure(e)) }
            }
        }
        searchJob = job
    }

    // endregion

    private suspend fun persist() {
        try {
            repository.save(current.record)
            _state.update { it.copy(message = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(message = GameMessage.NotSaved) }
        }
    }

    private fun clearSelection() =
        _state.update { it.copy(selectedSquare = null, legalDestinations = emptySet()) }

    private fun GameState.clearedTransient() = copy(
        selectedSquare = null,
        legalDestinations = emptySet(),
        pendingMove = null,
        hintStage = HintStage.Available,
        replayPly = null
    )

    private fun normalizedKey(position: Position): String = position.fen.split(' ').take(2).joinToString(" ")

    private fun repetitionCount(record: GameRecord, key: String): Int {
        var replay = runCatching { Position.fromFen(record.startingFEN) }.getOrDefault(Position.standard)
        var count = if (normalizedKey(replay) == key) 1 else 0
        for (uci in record.uciMoves) {
            val move = Move.fromUci(uci) ?: break
            replay = runCatching { replay.applying(move) }.getOrNull() ?: break
            if (normalizedKey(replay) == key) count++
        }
        return count
    }

    private fun seedForCurrentPosition(salt: ULong): ULong {
        val mixed = (current.record.id.hashCode().toLong() * 31L + positionVersion) * -7046029254386353131L
        return mixed.toULong() xor salt
    }

    // region Clock

    private fun startClock() {
        val s = current
        if (s.record.timeControl == TimeControl.CASUAL || s.record.result != null) return
        clockJob?.cancel()
        clockJob = scope.launch {
            while (true) {
                delay(1000)
                if (current.record.result != null) return@launch
                tickClock()
            }
        }
    }

    private fun tickClock() {
        val s = current
        var record = s.record.copy(elapsedSeconds = s.record.elapsedSeconds + 1)
        var loser: Side? = null
        if (s.position.sideToMove == Side.RED) {
            val remaining = maxOf(0, (record.redSecondsRemaining ?: 0) - 1)
            record = record.copy(redSecondsRemaining = remaining)
            if (remaining == 0) loser = Side.RED
        } else {
            val remaining = maxOf(0, (record.blackSecondsRemaining ?: 0) - 1)
            record = record.copy(blackSecondsRemaining = remaining)
            if (remaining == 0) loser = Side.BLACK
        }
        _state.update { it.copy(record = record) }
        if (loser != null) {
            finishOnTime(loser)
        } else if (record.elapsedSeconds % 10 == 0) {
            scope.launch { persist() }
        }
    }

    private fun finishOnTime(loser: Side) {
        cancelSearch()
        _state.update {
            it.copy(
                record = it.record.copy(result = GameResult(loser.opponent, GameResultReason.TIME_LOSS), updatedAt = now()),
                showResult = true
            )
        }
        feedback.play(FeedbackEvent.GAME_END)
        scope.launch { persist() }
    }

    // endregion
}
