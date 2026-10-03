package com.frankzhu.xiangqi.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.frankzhu.xiangqi.core.FeedbackEvent
import com.frankzhu.xiangqi.core.FeedbackSink
import com.frankzhu.xiangqi.core.SoundSynthesis
import com.frankzhu.xiangqi.data.AppPreferences
import java.util.concurrent.Executors

/**
 * Plays the synthesized sound and the haptic for a game event, honouring the
 * Settings toggles. Audio work stays off the main thread: building an
 * AudioTrack can block until the audio server answers.
 */
class FeedbackPlayer(context: Context, private val preferences: AppPreferences) : FeedbackSink {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "xiangqi-audio") }
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Executor-confined. */
    private val tracks = HashMap<FeedbackEvent, AudioTrack>()

    /** Renders the cues ahead of the first move so it is not the one that stutters. */
    fun prepare() {
        if (!preferences.sounds) return
        executor.execute { FeedbackEvent.entries.forEach { track(it) } }
    }

    override fun play(event: FeedbackEvent) {
        if (preferences.sounds && event.recipe != null) {
            executor.execute {
                val track = track(event) ?: return@execute
                runCatching {
                    track.stop()
                    track.reloadStaticData()
                    track.play()
                }
            }
        }
        if (preferences.haptics) vibrate(event)
    }

    private fun vibrate(event: FeedbackEvent) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val effect = when (event) {
                    FeedbackEvent.PIECE_SELECTED -> VibrationEffect.EFFECT_TICK
                    FeedbackEvent.MOVE -> VibrationEffect.EFFECT_CLICK
                    FeedbackEvent.CAPTURE -> VibrationEffect.EFFECT_HEAVY_CLICK
                    FeedbackEvent.CHECK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    FeedbackEvent.GAME_END -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    FeedbackEvent.INVALID_ATTEMPT -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
                vibrator.vibrate(VibrationEffect.createPredefined(effect))
            } else {
                val ms = when (event) {
                    FeedbackEvent.PIECE_SELECTED -> 8L
                    FeedbackEvent.MOVE -> 15L
                    else -> 30L
                }
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun track(event: FeedbackEvent): AudioTrack? {
        tracks[event]?.let { return it }
        val recipe = event.recipe ?: return null
        val pcm = SoundSynthesis.samples(recipe)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SoundSynthesis.SAMPLE_RATE.toInt())
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
                .also { it.write(pcm, 0, pcm.size) }
        }.getOrNull() ?: return null
        tracks[event] = track
        return track
    }
}
