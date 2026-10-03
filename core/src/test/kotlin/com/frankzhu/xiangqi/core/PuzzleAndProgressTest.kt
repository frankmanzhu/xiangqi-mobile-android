package com.frankzhu.xiangqi.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PuzzleAndProgressTest {
    private fun fixture(moves: List<String>) = CCPDRecord(
        summary = CCPDRecordSummary("fixture", "殺局_殺法_練習題", "fixture.pgn", null, null, null, null, null, null, moves.size),
        sourceEncoding = XiangqiPGNTextEncoding.UTF8,
        startingFEN = Position.standard.fen,
        moves = moves.mapIndexed { i, m -> NormalizedXiangqiPGNMove(i + 1, m, m) },
        tags = emptyMap()
    )

    @Test
    fun rejectsWrongMoveAndAutomaticallyPlaysRecordedReply() {
        val puzzle = CCPDPuzzleSession(fixture(listOf("b2e2", "b7e7", "b0c2")))
        assertEquals(CCPDPuzzleAttempt.Incorrect(Move.fromUci("b2e2")!!), puzzle.attempt(Move.fromUci("h2e2")!!))
        assertEquals(0, puzzle.currentPly)
        assertEquals(1, puzzle.mistakes)

        assertEquals(CCPDPuzzleAttempt.Correct(Move.fromUci("b7e7")), puzzle.attempt(Move.fromUci("b2e2")!!))
        assertEquals(2, puzzle.currentPly)
        assertEquals(Side.RED, puzzle.position.sideToMove)
    }

    @Test
    fun completesOddLengthLineAndRestarts() {
        val puzzle = CCPDPuzzleSession(fixture(listOf("b2e2", "b7e7", "b0c2")))
        puzzle.attempt(Move.fromUci("b2e2")!!)
        assertEquals(CCPDPuzzleAttempt.Completed, puzzle.attempt(Move.fromUci("b0c2")!!))
        assertTrue(puzzle.isComplete)

        puzzle.restart()
        assertFalse(puzzle.isComplete)
        assertEquals(0, puzzle.currentPly)
        assertNull(puzzle.lastMove)
    }

    @Test
    fun progressPersistsAcrossStoreInstances() {
        val dir = Files.createTempDirectory("progress").toFile()
        try {
            val file = dir.resolve("progress.json")
            val first = LearningProgressStore(file)
            first.recordOpened("ccpd:開局/fixture")
            first.updateLastPly(7, "ccpd:開局/fixture")
            assertTrue(first.toggleBookmark("ccpd:開局/fixture").isBookmarked)
            first.recordCompletion("ccpd:開局/fixture", 12)

            val restored = LearningProgressStore(file).progress("ccpd:開局/fixture")
            assertEquals(1, restored.attempts)
            assertEquals(1, restored.completions)
            assertEquals(12, restored.lastPly)
            assertTrue(restored.isBookmarked)
            assertEquals(listOf("ccpd:開局/fixture"), LearningProgressStore(file).bookmarkedIds())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnknownFutureSchema() {
        val dir = Files.createTempDirectory("progress").toFile()
        try {
            val file = dir.resolve("progress.json")
            file.writeText("{\"schemaVersion\":99,\"items\":{}}")
            val error = assertFailsWith<UnsupportedLearningSchemaException> { LearningProgressStore(file).snapshot() }
            assertEquals(99, error.version)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun compressionRoundTripsAndMatchesIosFraming() {
        val text = "h2e2h9g7b0c2".toByteArray()
        val packed = CCPDCompression.compress(text)
        assertEquals(listOf<Byte>(0, 0, 0, 12), packed.take(4))
        assertEquals(text.toList(), CCPDCompression.decompress(packed).toList())
        assertEquals(listOf<Byte>(0, 0, 0, 0), CCPDCompression.compress(ByteArray(0)).toList())
        assertEquals(0, CCPDCompression.decompress(byteArrayOf(0, 0, 0, 0)).size)
        assertFailsWith<CCPDCompressionException> { CCPDCompression.decompress(byteArrayOf(1, 2)) }
    }

    @Test
    fun computerConfigurationClampsLevel() {
        assertEquals(1, ComputerConfiguration(-1, 1uL).level)
        assertEquals(3, ComputerConfiguration(3, 1uL).level)
        assertEquals(5, ComputerConfiguration(99, 1uL).level)
        assertEquals(12_345uL, ComputerConfiguration(2, 12_345uL).seed)
    }

    @Test
    fun learningStoreCombinesAndOrdersLibraries() {
        fun summary(id: String, date: String, path: String) =
            CCPDRecordSummary(id, "開局", path, null, date, null, null, null, null, 1)

        class Fake(val items: List<CCPDRecordSummary>) : CCPDLibrary {
            override fun validate() {}
            override fun metadata() = mapOf("license" to "x")
            override fun categories() = listOf(CCPDCategorySummary("開局", items.size))
            override fun records(category: String?, query: String?, sourcePrefix: String?, limit: Int, offset: Int) = items
            override fun record(id: String): CCPDRecord? = null
        }

        val store = LearningLibraryStore(
            Fake(listOf(summary("a", "2020", "p1"), summary("b", "2022", "p2"))),
            Fake(listOf(summary("user:c", "2021", "p3")))
        )
        assertEquals(listOf(CCPDCategorySummary("開局", 3)), store.categories())
        assertEquals(listOf("b", "user:c", "a"), store.records().map { it.id })
        assertEquals(listOf("user:c"), store.records(limit = 1, offset = 1).map { it.id })
        assertEquals("1", store.metadata()["user_record_count"])
    }
}
