package com.frankzhu.xiangqi.core

sealed class CCPDPuzzleAttempt {
    data class Incorrect(val expected: Move) : CCPDPuzzleAttempt()
    data class Correct(val reply: Move?) : CCPDPuzzleAttempt()
    object Completed : CCPDPuzzleAttempt()
}

/**
 * A deterministic practice session built from a validated CCPD main line.
 * The learner plays the starting side and the recorded reply is applied
 * automatically, keeping practice faithful to the source record.
 */
class CCPDPuzzleSession(record: CCPDRecord) {
    val recordID: String = record.summary.id
    private val startingFEN: String = record.startingFEN
    private val line: List<Move> = record.moves.map {
        Move.fromUci(it.uci)
            ?: throw CCPDLibraryException.CorruptRecord("${record.summary.id}: malformed move ${it.uci}")
    }

    var position: Position = Position.fromFen(record.startingFEN)
        private set
    val practiceSide: Side = position.sideToMove
    var currentPly: Int = 0
        private set
    var lastMove: Move? = null
        private set
    var mistakes: Int = 0
        private set

    val expectedMove: Move? get() = line.getOrNull(currentPly)
    val isComplete: Boolean get() = currentPly >= line.size

    fun attempt(move: Move): CCPDPuzzleAttempt {
        val expected = expectedMove ?: return CCPDPuzzleAttempt.Completed
        if (move != expected) {
            mistakes++
            return CCPDPuzzleAttempt.Incorrect(expected)
        }
        position = position.applying(move)
        currentPly++
        lastMove = move
        if (currentPly >= line.size) return CCPDPuzzleAttempt.Completed

        val reply = line[currentPly]
        position = position.applying(reply)
        currentPly++
        lastMove = reply
        return if (currentPly >= line.size) CCPDPuzzleAttempt.Completed else CCPDPuzzleAttempt.Correct(reply)
    }

    fun restart() {
        position = Position.fromFen(startingFEN)
        currentPly = 0
        lastMove = null
        mistakes = 0
    }
}
