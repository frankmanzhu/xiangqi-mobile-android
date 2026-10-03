package com.frankzhu.xiangqi.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.ceil

/**
 * Builds short game sounds as PCM audio, with no bundled assets.
 *
 * The app ships no audio files and makes no network requests; synthesizing the
 * cues keeps them deterministic, auditable, and testable.
 */
object SoundSynthesis {
    const val SAMPLE_RATE = 44_100.0

    /** One additive component of a sound. A null [frequency] is a noise burst. */
    class Partial(
        val start: Double,
        val duration: Double,
        val frequency: Double?,
        val amplitude: Double,
        val decay: Double
    )

    /** Renders partials into a 16-bit mono PCM sample array. */
    fun samples(partials: List<Partial>): ShortArray {
        val total = partials.maxOfOrNull { it.start + it.duration } ?: 0.0
        val frameCount = max(1, ceil(total * SAMPLE_RATE).toInt())
        val samples = DoubleArray(frameCount)
        val noise = NoiseGenerator()

        for (partial in partials) {
            val first = (partial.start * SAMPLE_RATE).toInt()
            val length = (partial.duration * SAMPLE_RATE).toInt()
            // A one-pole lowpass takes the hiss off the noise burst so it reads as wood.
            var filtered = 0.0
            for (offset in 0 until length) {
                val index = first + offset
                if (index < 0 || index >= frameCount) continue
                val time = offset / SAMPLE_RATE
                val envelope = exp(-partial.decay * time)
                val value = if (partial.frequency != null) {
                    sin(2 * PI * partial.frequency * time)
                } else {
                    filtered += 0.28 * (noise.next() - filtered)
                    filtered
                }
                samples[index] += value * partial.amplitude * envelope
            }
        }

        // A short fade-out prevents a click when the buffer ends mid-cycle.
        val fade = min(frameCount, (0.004 * SAMPLE_RATE).toInt())
        for (offset in 0 until fade) {
            samples[frameCount - fade + offset] *= 1 - offset.toDouble() / fade
        }
        return ShortArray(frameCount) { (samples[it].coerceIn(-1.0, 1.0) * 32_767).toInt().toShort() }
    }

    /** Renders partials into a complete mono 16-bit WAV file. */
    fun wav(partials: List<Partial>): ByteArray {
        val pcm = samples(partials)
        val dataSize = pcm.size * 2
        val out = java.io.ByteArrayOutputStream(44 + dataSize)
        fun int32(v: Int) { for (i in 0..3) out.write((v ushr (8 * i)) and 0xff) }
        fun int16(v: Int) { for (i in 0..1) out.write((v ushr (8 * i)) and 0xff) }
        val rate = SAMPLE_RATE.toInt()
        out.write("RIFF".toByteArray()); int32(36 + dataSize)
        out.write("WAVEfmt ".toByteArray()); int32(16)
        int16(1); int16(1); int32(rate); int32(rate * 2); int16(2); int16(16)
        out.write("data".toByteArray()); int32(dataSize)
        for (s in pcm) int16(s.toInt())
        return out.toByteArray()
    }

    /** Deterministic noise, so a given sound renders identically every run. */
    private class NoiseGenerator {
        private var state: Long = -7046029254386353131L // 0x9E3779B97F4A7C15

        fun next(): Double {
            state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            return (state ushr 32).toInt().toDouble() / Int.MAX_VALUE
        }
    }
}

/** The cues the game plays, defined as recipes rather than asset names. */
object GameSoundRecipe {
    private fun p(s: Double, d: Double, f: Double?, a: Double, k: Double) = SoundSynthesis.Partial(s, d, f, a, k)

    /** A piece meeting the board: a filtered noise transient over a low thump. */
    val move = listOf(p(0.0, 0.05, null, 0.5, 90.0), p(0.0, 0.08, 196.0, 0.35, 55.0))

    /** Heavier and lower than a move, with a second thud for the piece removed. */
    val capture = listOf(
        p(0.0, 0.07, null, 0.7, 70.0), p(0.0, 0.12, 130.0, 0.5, 38.0), p(0.05, 0.1, 98.0, 0.32, 42.0)
    )

    /** Two rising tones: attention, not alarm. */
    val check = listOf(p(0.0, 0.09, 880.0, 0.34, 26.0), p(0.09, 0.12, 1_174.0, 0.34, 22.0))

    /** A three-note rise to close the game. */
    val gameEnd = listOf(
        p(0.0, 0.14, 659.0, 0.3, 16.0), p(0.12, 0.14, 880.0, 0.3, 16.0), p(0.24, 0.26, 1_319.0, 0.32, 11.0)
    )

    /** A low, short buzz for a rejected move. */
    val invalidAttempt = listOf(p(0.0, 0.11, 155.0, 0.42, 20.0), p(0.0, 0.11, 233.0, 0.22, 24.0))
}
