package com.frankzhu.xiangqi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frankzhu.xiangqi.core.ComputerConfiguration
import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.core.GameResultReason
import com.frankzhu.xiangqi.core.Position
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.core.legalMoves
import com.frankzhu.xiangqi.engine.NativePikafish
import com.frankzhu.xiangqi.engine.PikafishEngine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real native Pikafish build, ported from the iOS bridge smoke test. */
@RunWith(AndroidJUnit4::class)
class EngineInstrumentedTest {
    private val start = GameRecord.STANDARD_FEN
    private val engine by lazy { PikafishEngine(InstrumentationRegistry.getInstrumentation().targetContext) }

    private fun rules(fen: String, vararg moves: String) = runBlocking { engine.result(fen, moves.toList()) }

    @Test
    fun revisionMatchesPinnedPikafish() {
        assertEquals(PikafishEngine.REVISION, NativePikafish.nativeRevision())
    }

    @Test
    fun rulesFixturesMatchTheIosEngineBehaviour() {
        assertNull(rules(start))
        assertNull(rules(start, "b0c2", "b9c7", "c2b0", "c7b9"))
        // Repetition without checks is a draw by the computer rule.
        val draw = rules(start, "b0c2", "b9c7", "c2b0", "c7b9", "b0c2", "b9c7", "c2b0", "c7b9")
        assertNotNull(draw); assertNull(draw!!.winner); assertEquals(GameResultReason.RULES_ADJUDICATION, draw.reason)
        // Perpetual check loses for the checking side (red keeps checking, so black wins).
        val perpetual = rules("4k4/9/4R4/9/9/9/9/9/9/5K3 b - - 0 1",
            "e9d9", "e7d7", "d9e9", "d7e7", "e9d9", "e7d7", "d9e9", "d7e7")
        assertEquals(Side.BLACK, perpetual?.winner)
        // Checkmate, stalemate and bare kings.
        assertEquals(GameResultReason.CHECKMATE, rules("4k4/3RRP3/9/9/9/9/9/9/9/3K5 b - - 0 1")?.reason)
        assertEquals(GameResultReason.STALEMATE, rules("4k4/3R1R3/9/9/9/9/9/9/9/3K5 b - - 0 1")?.reason)
        assertNull(rules("4k4/9/9/9/9/9/9/9/9/5K3 w - - 0 1")!!.winner)
    }

    @Test
    fun illegalRulesHistoryIsReportedNotCrashed() {
        val failure = runCatching { rules(start, "a3a2") }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test
    fun enginePlaysALegalReplyAndRespectsHistory() = runBlocking {
        val first = engine.chooseMove(start, listOf("a3a4"), ComputerConfiguration(1, 1uL))
        val position = Position.standard.replaying(listOf("a3a4"))
        assertTrue("engine move $first must be legal", first in position.legalMoves())

        val second = engine.chooseMove(start, listOf("a3a4", first.uci), ComputerConfiguration(1, 2uL))
        assertTrue(second in position.applying(first).legalMoves())
    }

    @Test
    fun cancellingASearchStopsPromptlyAndEngineStaysUsable() = runBlocking {
        val long = async(start = CoroutineStart.DEFAULT) {
            engine.chooseMove(start, emptyList(), ComputerConfiguration(5, 3uL)) // 3 s budget
        }
        delay(300)
        val began = System.nanoTime()
        long.cancel()
        long.join()
        assertTrue("cancellation took too long", (System.nanoTime() - began) / 1_000_000 < 1500)
        val next = withTimeout(10_000) { engine.chooseMove(start, emptyList(), ComputerConfiguration(1, 4uL)) }
        assertTrue(next in Position.standard.legalMoves())
    }
}
