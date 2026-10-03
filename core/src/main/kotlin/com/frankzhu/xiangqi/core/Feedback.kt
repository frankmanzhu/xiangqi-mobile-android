package com.frankzhu.xiangqi.core

/** Something worth telling the player about through sound or touch. */
enum class FeedbackEvent { PIECE_SELECTED, MOVE, CAPTURE, CHECK, GAME_END, INVALID_ATTEMPT;

    /** Selecting a piece is haptic-only: a sound on every touch is too chatty. */
    val recipe: List<SoundSynthesis.Partial>?
        get() = when (this) {
            PIECE_SELECTED -> null
            MOVE -> GameSoundRecipe.move
            CAPTURE -> GameSoundRecipe.capture
            CHECK -> GameSoundRecipe.check
            GAME_END -> GameSoundRecipe.gameEnd
            INVALID_ATTEMPT -> GameSoundRecipe.invalidAttempt
        }
}

fun interface FeedbackSink {
    fun play(event: FeedbackEvent)

    companion object { val None = FeedbackSink { } }
}
