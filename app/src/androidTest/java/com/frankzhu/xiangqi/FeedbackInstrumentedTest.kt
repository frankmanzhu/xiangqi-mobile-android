package com.frankzhu.xiangqi

import android.media.AudioTrack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frankzhu.xiangqi.core.FeedbackEvent
import com.frankzhu.xiangqi.core.SoundSynthesis
import com.frankzhu.xiangqi.data.AppPreferences
import com.frankzhu.xiangqi.feedback.FeedbackPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the synthesized cues really reach the audio system, and that the toggles silence them. */
@RunWith(AndroidJUnit4::class)
class FeedbackInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Suppress("UNCHECKED_CAST")
    private fun tracks(player: FeedbackPlayer): Map<FeedbackEvent, AudioTrack> {
        val field = FeedbackPlayer::class.java.getDeclaredField("tracks").apply { isAccessible = true }
        return field.get(player) as Map<FeedbackEvent, AudioTrack>
    }

    private fun prefs(sounds: Boolean, haptics: Boolean) =
        AppPreferences(context.getSharedPreferences("feedback-test-${System.nanoTime()}", 0)).apply {
            updateSounds(sounds); updateHaptics(haptics)
        }

    @Test
    fun aMoveCueIsPlayedThroughTheAudioSystem() {
        val player = FeedbackPlayer(context, prefs(sounds = true, haptics = true))
        player.prepare()
        Thread.sleep(500)
        player.play(FeedbackEvent.CAPTURE)
        val expectedFrames = SoundSynthesis.samples(com.frankzhu.xiangqi.core.GameSoundRecipe.capture).size
        // The mixer consumes the clip: the playback head advances towards the clip length.
        var head = 0
        val end = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < end && head < expectedFrames / 2) {
            Thread.sleep(50)
            head = tracks(player)[FeedbackEvent.CAPTURE]?.playbackHeadPosition ?: 0
        }
        assertTrue("capture cue should be consumed by the audio system (head=$head of $expectedFrames)", head >= expectedFrames / 2)
    }

    @Test
    fun selectionIsHapticOnlyAndMutedSettingsPlayNothing() {
        val silent = FeedbackPlayer(context, prefs(sounds = false, haptics = false))
        silent.play(FeedbackEvent.MOVE)
        Thread.sleep(300)
        assertEquals("no track is even created when sounds are off", 0, tracks(silent).size)

        val player = FeedbackPlayer(context, prefs(sounds = true, haptics = false))
        player.play(FeedbackEvent.PIECE_SELECTED) // selecting a piece has no sound by design
        Thread.sleep(300)
        assertTrue(tracks(player)[FeedbackEvent.PIECE_SELECTED] == null)
    }
}
