package com.frankzhu.xiangqi.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundSynthesisTest {
    private val headerBytes = 44

    @Test
    fun rendersAWellFormedWavHeader() {
        val data = SoundSynthesis.wav(GameSoundRecipe.move)
        assertTrue(data.size > headerBytes)
        assertEquals("RIFF", String(data, 0, 4))
        assertEquals("WAVE", String(data, 8, 4))
        assertEquals("data", String(data, 36, 4))
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(data.size - 8, buffer.getInt(4), "RIFF size must cover everything after it")
        assertEquals(data.size - headerBytes, buffer.getInt(40))
    }

    @Test
    fun everyCueProducesAudibleAudio() {
        val cues = mapOf(
            "move" to GameSoundRecipe.move, "capture" to GameSoundRecipe.capture, "check" to GameSoundRecipe.check,
            "gameEnd" to GameSoundRecipe.gameEnd, "invalidAttempt" to GameSoundRecipe.invalidAttempt
        )
        for ((name, recipe) in cues) {
            val (peak, seconds) = peakAndLength(recipe)
            assertTrue(peak > 0.05, "$name is inaudibly quiet")
            assertTrue(peak <= 1.0, "$name clips")
            assertTrue(seconds > 0.03, "$name is too short to hear")
            assertTrue(seconds < 1.0, "$name outlasts the action")
        }
    }

    @Test
    fun renderingIsDeterministic() {
        assertContentEquals(SoundSynthesis.wav(GameSoundRecipe.capture), SoundSynthesis.wav(GameSoundRecipe.capture))
    }

    @Test
    fun captureIsWeightierThanAPlainMove() {
        val move = peakAndLength(GameSoundRecipe.move)
        val capture = peakAndLength(GameSoundRecipe.capture)
        assertTrue(capture.first > move.first)
        assertTrue(capture.second > move.second)
    }

    private fun peakAndLength(recipe: List<SoundSynthesis.Partial>): Pair<Double, Double> {
        val samples = SoundSynthesis.samples(recipe)
        val peak = samples.maxOf { abs(it / 32_767.0) }
        return peak to samples.size / SoundSynthesis.SAMPLE_RATE
    }
}
