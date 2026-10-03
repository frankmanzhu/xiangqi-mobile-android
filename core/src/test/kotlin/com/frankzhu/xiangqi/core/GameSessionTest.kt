package com.frankzhu.xiangqi.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryRepository : GameRepository {
    var saved: GameRecord? = null
    var failing = false
    override suspend fun load() = saved
    override suspend fun save(record: GameRecord) {
        if (failing) error("disk full")
        saved = record
    }
    override suspend fun delete() { saved = null }
}

/** Replies with the first legal move of the position reached by the history. */
private class FirstLegalMoveComputer : ComputerPlayerClient {
    override val policyID = "fake"
    var calls = 0
    var override: Move? = null
    override suspend fun chooseMove(startingFEN: String, moves: List<String>, configuration: ComputerConfiguration): Move {
        calls++
        override?.let { return it }
        return Position.fromFen(startingFEN).replaying(moves).legalMoves().first()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameSessionTest {
    private fun sq(uci: String) = Square.parse(uci)!!

    private fun TestScope.session(
        record: GameRecord,
        repository: MemoryRepository = MemoryRepository(),
        computer: ComputerPlayerClient = FirstLegalMoveComputer(),
        confirm: Boolean = false,
        events: MutableList<FeedbackEvent> = mutableListOf()
    ) = GameSession(
        record, repository, computer, rules = null, scope = this,
        settings = { confirm }, feedback = { events.add(it) }
    )

    @Test
    fun tapSelectsThenMovesAndRecordsNotation() = runTest {
        val repo = MemoryRepository()
        val s = session(GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null), repo)
        s.tap(sq("h2"))
        assertEquals(sq("h2"), s.state.value.selectedSquare)
        assertTrue(sq("e2") in s.state.value.legalDestinations)
        s.tap(sq("e2"))
        runCurrent()
        val state = s.state.value
        assertEquals(listOf("h2e2"), state.record.uciMoves)
        assertEquals("Cannon h2–e2", state.record.moves[0].notation)
        assertEquals(Side.BLACK, state.position.sideToMove)
        assertNull(state.selectedSquare)
        assertEquals(listOf("h2e2"), repo.saved?.uciMoves)
        s.close()
    }

    @Test
    fun cannotMovePiecesOfTheWrongSideOrIllegally() = runTest {
        val s = session(GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null))
        s.tap(sq("h9"))
        assertNull(s.state.value.selectedSquare)
        s.tap(sq("a0"))
        s.tap(sq("a5"))
        runCurrent()
        assertTrue(s.state.value.record.moves.isEmpty())
        s.close()
    }

    @Test
    fun computerRepliesAfterHumanMove() = runTest {
        val computer = FirstLegalMoveComputer()
        val s = session(GameRecord.create(GameMode.COMPUTER, Side.RED), computer = computer)
        s.tap(sq("h2")); s.tap(sq("e2"))
        runCurrent()
        val state = s.state.value
        assertEquals(2, state.record.moves.size)
        assertEquals(Side.BLACK, state.record.moves[1].side)
        assertEquals(Side.RED, state.position.sideToMove)
        assertEquals(1, computer.calls)
        assertEquals(false, state.isThinking)
        s.close()
    }

    @Test
    fun computerMovesFirstWhenHumanIsBlack() = runTest {
        val s = session(GameRecord.create(GameMode.COMPUTER, Side.BLACK, orientation = Side.BLACK))
        s.startIfNeeded()
        runCurrent()
        assertEquals(1, s.state.value.record.moves.size)
        assertEquals(Side.RED, s.state.value.record.moves[0].side)
        s.close()
    }

    @Test
    fun repeatedStartRequestsNeverLaunchASecondSearch() = runTest {
        val computer = FirstLegalMoveComputer()
        val s = session(GameRecord.create(GameMode.COMPUTER, Side.BLACK, orientation = Side.BLACK), computer = computer)
        // The screen, the lifecycle observer and the app model all ask for the opening move.
        repeat(5) { s.startIfNeeded() }
        runCurrent()
        assertEquals(1, computer.calls)
        assertEquals(1, s.state.value.record.moves.size)
        // Asking again once the game is the human's turn is a no-op too.
        repeat(3) { s.startIfNeeded() }
        runCurrent()
        assertEquals(1, computer.calls)
        s.close()
    }

    @Test
    fun illegalEngineMoveIsRejectedWithMismatch() = runTest {
        val computer = FirstLegalMoveComputer().apply { override = Move.fromUci("a0a9") }
        val s = session(GameRecord.create(GameMode.COMPUTER, Side.RED), computer = computer)
        s.tap(sq("h2")); s.tap(sq("e2"))
        runCurrent()
        assertEquals(1, s.state.value.record.moves.size)
        assertEquals(GameMessage.EngineMismatch, s.state.value.message)
        s.close()
    }

    @Test
    fun undoRemovesPairAgainstComputerAndSingleMoveInHotSeat() = runTest {
        val s = session(GameRecord.create(GameMode.COMPUTER, Side.RED))
        s.tap(sq("h2")); s.tap(sq("e2"))
        runCurrent()
        s.undo()
        assertTrue(s.state.value.record.moves.isEmpty())
        assertEquals(Position.standard.fen, s.state.value.position.fen)
        s.close()

        val hotSeat = session(GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null))
        hotSeat.tap(sq("h2")); hotSeat.tap(sq("e2")); runCurrent()
        hotSeat.undo()
        assertTrue(hotSeat.state.value.record.moves.isEmpty())
        hotSeat.close()
    }

    @Test
    fun undoIsDisabledUnderTimeControl() = runTest {
        val s = session(GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null, timeControl = TimeControl.TEN_MINUTES))
        s.tap(sq("h2")); s.tap(sq("e2")); runCurrent()
        s.undo()
        assertEquals(1, s.state.value.record.moves.size)
        s.close()
    }

    @Test
    fun confirmMovesDefersCommitUntilConfirmed() = runTest {
        val s = session(GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null), confirm = true)
        s.tap(sq("h2")); s.tap(sq("e2")); runCurrent()
        assertEquals(Move.fromUci("h2e2"), s.state.value.pendingMove)
        assertTrue(s.state.value.record.moves.isEmpty())
        s.cancelPendingMove()
        assertNull(s.state.value.pendingMove)
        // The selection survives a cancelled proposal, so the destination can be tapped again.
        s.tap(sq("e2")); s.confirmPendingMove(); runCurrent()
        assertEquals(1, s.state.value.record.moves.size)
        s.close()
    }

    @Test
    fun hintRevealsSourceThenDestinationAndFlagsTheMove() = runTest {
        val computer = FirstLegalMoveComputer().apply { override = Move.fromUci("h2e2") }
        val s = session(GameRecord.create(GameMode.COMPUTER, Side.RED), computer = computer)
        s.hint(); runCurrent()
        assertEquals(HintStage.Source(Move.fromUci("h2e2")!!), s.state.value.hintStage)
        s.hint()
        assertEquals(HintStage.Destination(Move.fromUci("h2e2")!!), s.state.value.hintStage)
        computer.override = null
        s.tap(sq("h2")); s.tap(sq("e2")); runCurrent()
        assertTrue(s.state.value.record.moves[0].hintUsed)
        assertEquals(HintStage.Available, s.state.value.hintStage)
        s.close()
    }

    @Test
    fun resignEndsGameWithOpponentWinning() = runTest {
        val repo = MemoryRepository()
        val s = session(GameRecord.create(GameMode.COMPUTER, Side.RED), repo)
        s.resign()
        val result = s.state.value.record.result
        assertEquals(GameResult(Side.BLACK, GameResultReason.RESIGNATION), result)
        assertTrue(s.state.value.showResult)
        assertEquals(result, repo.saved?.result)
        s.close()
    }

    @Test
    fun replayShowsEarlierPositionsAndBlocksInput() = runTest {
        val s = session(GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null))
        s.tap(sq("h2")); s.tap(sq("e2")); runCurrent()
        s.showReplay(0)
        assertEquals(Position.standard.fen, s.state.value.displayedPosition.fen)
        assertIs<GameStatus.Reviewing>(s.state.value.status)
        s.tap(sq("h9"))
        assertNull(s.state.value.selectedSquare)
        s.stepReplay(1)
        assertEquals(1, s.state.value.replayPly)
        s.returnToLive()
        assertNull(s.state.value.replayPly)
        s.close()
    }

    @Test
    fun clockCountsDownAndFlagFallLosesTheGame() = runTest {
        val record = GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null, timeControl = TimeControl.TEN_MINUTES)
            .copy(redSecondsRemaining = 2)
        val s = session(record)
        advanceTimeBy(1_100)
        assertEquals(1, s.state.value.record.redSecondsRemaining)
        advanceTimeBy(1_000)
        runCurrent()
        val result = s.state.value.record.result
        assertEquals(GameResult(Side.BLACK, GameResultReason.TIME_LOSS), result)
        s.close()
    }

    @Test
    fun persistenceFailureSurfacesNotSavedBanner() = runTest {
        val repo = MemoryRepository().apply { failing = true }
        val s = session(GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null), repo)
        s.tap(sq("h2")); s.tap(sq("e2")); runCurrent()
        assertEquals(GameMessage.NotSaved, s.state.value.message)
        s.close()
    }

    @Test
    fun feedbackFiresOneCuePerMove() = runTest {
        val events = mutableListOf<FeedbackEvent>()
        val s = session(GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null), events = events)
        s.tap(sq("h2")); s.tap(sq("e2")); runCurrent()
        assertEquals(listOf(FeedbackEvent.PIECE_SELECTED, FeedbackEvent.MOVE), events)
        s.close()
    }

    @Test
    fun checkmateEndsGameUsingBuiltInRules() = runTest {
        val record = GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null)
            .copy(startingFEN = "3k5/R8/9/9/9/9/9/9/9/1R2K4 w - - 0 1")
        val s = session(record)
        s.tap(sq("b0")); s.tap(sq("b9")); runCurrent()
        val result = s.state.value.record.result
        assertEquals(GameResult(Side.RED, GameResultReason.CHECKMATE), result)
        assertTrue(s.state.value.showResult)
        s.close()
    }
}
