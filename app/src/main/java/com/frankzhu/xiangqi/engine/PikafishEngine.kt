package com.frankzhu.xiangqi.engine

import android.content.Context
import com.frankzhu.xiangqi.core.ComputerConfiguration
import com.frankzhu.xiangqi.core.ComputerPlayerClient
import com.frankzhu.xiangqi.core.GameResult
import com.frankzhu.xiangqi.core.GameResultReason
import com.frankzhu.xiangqi.core.Move
import com.frankzhu.xiangqi.core.RulesAdjudicator
import com.frankzhu.xiangqi.core.Side
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

sealed class EngineException(message: String) : Exception(message) {
    class NetworkMissing : EngineException("The Pikafish neural network is missing from this build.")
    class NetworkInvalid : EngineException("The Pikafish neural network failed its integrity check.")
    class InvalidMove(move: String) : EngineException("Pikafish returned an invalid move: $move")
}

/**
 * The on-device Pikafish engine.
 *
 * Implements both the computer opponent and the rules adjudicator, exactly as
 * the iOS app does: a game's result comes from Pikafish's own rule judge so
 * that perpetual check/chase decisions match the engine playing the game.
 */
class PikafishEngine(private val context: Context) : ComputerPlayerClient, RulesAdjudicator {
    override val policyID = "pikafish@$REVISION"

    private val searchLock = Mutex()
    @Volatile private var session: Long = 0L
    private val searchDispatcher = Executors.newSingleThreadExecutor { Thread(it, "pikafish-search") }.asCoroutineDispatcher()

    /** Copies the network out of the APK (verified by SHA-256) and opens a session. */
    private fun ensureSession(): Long {
        if (session != 0L) return session
        val network = networkFile()
        session = NativePikafish.nativeCreate(network.absolutePath)
        return session
    }

    private fun networkFile(): File {
        val target = File(context.filesDir, "engine/pikafish-$NETWORK_SHA256.nnue")
        if (target.isFile && sha256(target) == NETWORK_SHA256) return target
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".partial")
        try {
            context.assets.open("engine/pikafish.nnue").use { input ->
                partial.outputStream().use { input.copyTo(it, 1 shl 20) }
            }
        } catch (_: java.io.FileNotFoundException) {
            throw EngineException.NetworkMissing()
        }
        if (sha256(partial) != NETWORK_SHA256) {
            partial.delete()
            throw EngineException.NetworkInvalid()
        }
        if (!partial.renameTo(target)) throw EngineException.NetworkMissing()
        // Drop networks from older app versions.
        target.parentFile?.listFiles()?.filter { it != target }?.forEach { it.delete() }
        return target
    }

    /** Opens the engine ahead of the first search, so the first move is not the slow one. */
    suspend fun prepare() = withContext(Dispatchers.Default) {
        searchLock.withLock { ensureSession() }
    }

    override suspend fun chooseMove(
        startingFEN: String,
        moves: List<String>,
        configuration: ComputerConfiguration
    ): Move = searchLock.withLock {
        val handle = withContext(Dispatchers.Default) { ensureSession() }
        val budget = budget(configuration.level)
        val uci = coroutineScope {
            // The native search blocks its thread, so it runs on its own and a
            // cancelled caller asks the engine to stop instead of waiting it out.
            val search = async(searchDispatcher) {
                NativePikafish.nativeSetPosition(handle, startingFEN, moves.toTypedArray())
                NativePikafish.nativeBestMove(handle, budget)
            }
            try {
                search.await()
            } catch (e: CancellationException) {
                NativePikafish.nativeStop(handle)
                // Keep the lock until the native call returns so searches never overlap.
                withContext(NonCancellable) { search.join() }
                throw e
            }
        }
        Move.fromUci(uci) ?: throw EngineException.InvalidMove(uci)
    }

    override suspend fun stop() {
        val handle = session
        if (handle != 0L) NativePikafish.nativeStop(handle)
    }

    // The rule judge shares Pikafish's global tables with the search, so it takes the same lock:
    // native calls never overlap, whichever coroutine issues them.
    override suspend fun result(startingFEN: String, moves: List<String>): GameResult? =
        searchLock.withLock { withContext(Dispatchers.Default) {
            val (outcome, reason) = NativePikafish.nativeRulesResult(startingFEN, moves.toTypedArray()).let { it[0] to it[1] }
            if (outcome == 0) return@withContext null
            val winner = when (outcome) { 2 -> Side.RED; 3 -> Side.BLACK; else -> null }
            val resultReason = when (reason) {
                1 -> GameResultReason.CHECKMATE
                2 -> GameResultReason.STALEMATE
                else -> GameResultReason.RULES_ADJUDICATION
            }
            GameResult(winner, resultReason)
        } }

    companion object {
        const val REVISION = "6a59ee2f7b105bff64d9efc2692591107787e2b1"
        const val NETWORK_SHA256 = "7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e"

        /** Milliseconds of thinking per strength level, matching the iOS app. */
        fun budget(level: Int): Int = when (level) {
            1 -> 150
            2 -> 350
            3 -> 750
            4 -> 1_500
            else -> 3_000
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
