package com.frankzhu.xiangqi.engine

/** Raised by the native bridge for any engine failure; the message is developer detail. */
class NativeEngineException(message: String) : RuntimeException(message)

/** Thin JNI surface over `PikafishBridge`. Handles are opaque `PFPikafishSession*` values. */
object NativePikafish {
    init {
        System.loadLibrary("pikafish_jni")
    }

    /** Returns `[outcome, reason]` — see `PFRuleResult` in the bridge header. */
    external fun nativeRulesResult(fen: String, moves: Array<String>): IntArray
    external fun nativeCreate(networkPath: String): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeSetPosition(handle: Long, fen: String, moves: Array<String>)
    external fun nativeBestMove(handle: Long, moveTimeMs: Int): String
    external fun nativeStop(handle: Long)
    external fun nativeRevision(): String
}
