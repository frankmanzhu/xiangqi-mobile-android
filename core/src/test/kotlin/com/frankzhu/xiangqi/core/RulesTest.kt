package com.frankzhu.xiangqi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {
    @Test
    fun standardPositionRoundTripsAndContainsAllPieces() {
        val position = Position.fromFen(GameRecord.STANDARD_FEN)
        assertEquals(32, position.pieces.size)
        assertEquals(Side.RED, position.sideToMove)
        assertEquals(PieceKind.CHARIOT, position.pieceAt(Square(0, 0))?.kind)
        assertEquals(PieceKind.GENERAL, position.pieceAt(Square(4, 0))?.kind)
        assertEquals(Side.BLACK, position.pieceAt(Square(4, 9))?.side)
        assertEquals(GameRecord.STANDARD_FEN, position.fen)
    }

    @Test
    fun everySquareAndMoveRoundTripsThroughUci() {
        for (rank in 0..9) for (file in 0..8) {
            val square = Square(file, rank)
            assertEquals(square, Square.parse(square.uci))
            val move = Move(square, Square(8 - file, 9 - rank))
            assertEquals(move, Move.fromUci(move.uci))
        }
    }

    @Test
    fun initialLegalMovesIncludeRepresentativePieceRules() {
        val legal = Position.standard.legalMoves().map { it.uci }.toSet()
        assertTrue("a0a1" in legal, "chariot can move one rank")
        assertTrue("b0a2" in legal, "horse can move around an open leg")
        assertTrue("b0c2" in legal)
        assertTrue("c0a2" in legal, "elephant stays on its side of river")
        assertTrue("b2b9" in legal, "cannon captures over exactly one screen")
        assertFalse("a3a2" in legal, "soldier cannot retreat")
        assertEquals(44, legal.size)
    }

    @Test
    fun moveCannotExposeFlyingGenerals() {
        val position = Position(
            pieces = mapOf(
                Square(4, 0) to Piece(Side.RED, PieceKind.GENERAL),
                Square(4, 9) to Piece(Side.BLACK, PieceKind.GENERAL),
                Square(4, 5) to Piece(Side.RED, PieceKind.CHARIOT)
            ),
            sideToMove = Side.RED
        )
        val legal = position.legalMoves().map { it.uci }.toSet()
        assertFalse("e5d5" in legal)
        assertTrue("e5e6" in legal)
    }

    @Test
    fun committedSequenceCanBeReplayedFromPortableRecord() {
        val position = Position.standard.replaying(listOf("h2e2", "h7e7", "h0g2"))
        assertEquals(Side.BLACK, position.sideToMove)
        assertEquals(PieceKind.HORSE, position.pieceAt(Square(6, 2))?.kind)
        assertNull(position.pieceAt(Square(7, 0)))
    }

    @Test
    fun portableRecordUsesOrderedUciMovesAsPrimaryTruth() {
        val record = GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null).let {
            it.copy(moves = listOf(RecordedMove(uci = "h2e2", notation = "Cannon h2–e2", side = Side.RED, hintUsed = false)))
        }
        val decoded = PortableGame.decode(PortableGame.from(record).encoded())
        assertEquals("xiangqi-uci-json", decoded.format)
        assertEquals(listOf("h2e2"), decoded.moves)
        assertEquals(GameRecord.STANDARD_FEN, decoded.startingFEN)
    }

    @Test
    fun checkmateIsDetected() {
        // Fool's-mate style: red general boxed in by a chariot pair.
        val position = Position.fromFen("3k5/9/9/9/9/9/9/9/4R4/r2K1R3 b - - 0 1")
        assertNotNull(position.legalMoves())
        val mated = Position.fromFen("R2k5/R8/9/9/9/9/9/9/9/4K4 b - - 0 1")
        val result = mated.result()
        assertEquals(GameResultReason.CHECKMATE, result?.reason)
        assertEquals(Side.RED, result?.winner)
    }

    @Test
    fun invalidFenIsRejected() {
        assertTrue(runCatching { Position.fromFen("garbage") }.exceptionOrNull() is PositionException.InvalidFEN)
        assertTrue(runCatching { Position.fromFen("9/9/9/9/9/9/9/9/9/9 w") }.exceptionOrNull() is PositionException.InvalidFEN)
    }
}
