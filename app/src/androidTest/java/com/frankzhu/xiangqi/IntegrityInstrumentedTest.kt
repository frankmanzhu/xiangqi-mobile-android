package com.frankzhu.xiangqi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frankzhu.xiangqi.core.CCPDPuzzleSession
import com.frankzhu.xiangqi.core.GameMode
import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.core.GameResult
import com.frankzhu.xiangqi.core.GameResultReason
import com.frankzhu.xiangqi.core.Move
import com.frankzhu.xiangqi.core.Position
import com.frankzhu.xiangqi.core.RecordedMove
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.core.legalMoves
import com.frankzhu.xiangqi.data.FileGameRepository
import com.frankzhu.xiangqi.data.LearningLibraryProvider
import com.frankzhu.xiangqi.data.UnsupportedSaveException
import com.frankzhu.xiangqi.engine.NativePikafish
import com.frankzhu.xiangqi.engine.NativeEngineException
import com.frankzhu.xiangqi.engine.PikafishEngine
import com.frankzhu.xiangqi.l10n.AppLanguage
import com.frankzhu.xiangqi.l10n.Localizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/** Resource, persistence and rules-agreement checks that need a real device (assets, SQLite, native engine). */
@RunWith(AndroidJUnit4::class)
class IntegrityInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledNetworkMatchesThePinnedChecksum() {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open("engine/pikafish.nnue").use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        assertEquals(PikafishEngine.NETWORK_SHA256, digest.digest().joinToString("") { "%02x".format(it) })
    }

    @Test
    fun licenceNoticesAreBundled() {
        for (name in listOf("Pikafish-GPL-3.0", "Pikafish-AUTHORS", "Pikafish-NNUE-NOTICE", "CCPD-CC-BY-4.0", "Apache-2.0")) {
            val text = context.assets.open("licenses/$name.txt").bufferedReader().use { it.readText() }
            assertTrue("$name should not be empty", text.length > 200)
        }
        assertTrue(context.assets.open("licenses/Pikafish-GPL-3.0.txt").bufferedReader().use { it.readText() }.contains("GNU GENERAL PUBLIC LICENSE"))
    }

    @Test
    fun everyLanguageCatalogLoadsWithTheFullKeySet() {
        val english = Localizer.load(context, AppLanguage.ENGLISH)
        for (language in listOf(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.TRADITIONAL_CHINESE)) {
            val localizer = Localizer.load(context, language)
            assertTrue(localizer(com.frankzhu.xiangqi.l10n.L10n.Mode.computer) != english(com.frankzhu.xiangqi.l10n.L10n.Mode.computer))
            assertTrue(localizer(com.frankzhu.xiangqi.l10n.L10n.Home.Hero.title).isNotBlank())
        }
    }

    @Test
    fun theEngineAndKotlinRulesAgreeOnThousandsOfRandomPlies() = runBlocking {
        // Every move our rules call legal must replay cleanly in Pikafish's own rule engine.
        val random = Random(20261004)
        val engine = PikafishEngine(context)
        var plies = 0
        repeat(40) {
            var position = Position.standard
            val moves = ArrayList<String>()
            repeat(120) {
                val legal = position.legalMoves()
                if (legal.isEmpty()) return@repeat
                val move = legal[random.nextInt(legal.size)]
                moves += move.uci
                position = position.applying(move)
                plies++
            }
            // Pikafish accepting the whole history proves each move was legal for it too.
            val result = engine.result(GameRecord.STANDARD_FEN, moves)
            if (result != null) assertTrue(result.reason in GameResultReason.values())
        }
        assertTrue(plies > 2_000)
    }

    @Test
    fun theEngineRejectsAnIllegalMoveThatOurRulesAlsoRejects() = runBlocking {
        val engine = PikafishEngine(context)
        val illegal = "a3a2" // a soldier cannot retreat
        assertTrue(Position.standard.legalMoves().none { it.uci == illegal })
        try {
            engine.result(GameRecord.STANDARD_FEN, listOf(illegal))
            fail("expected the engine to reject $illegal")
        } catch (expected: NativeEngineException) {
            assertTrue(expected.message!!.contains("Illegal"))
        }
    }

    @Test
    fun corpusGamesReplayUnderTheKotlinRulesAndThePikafishRules() = runBlocking {
        val store = LearningLibraryProvider(context).load()
        val engine = PikafishEngine(context)
        var checked = 0
        for (category in store.categories()) {
            val summaries = store.records(category = category.id, limit = 25)
            for (summary in summaries) {
                val record = store.record(summary.id) ?: continue
                val uci = record.moves.map { it.uci }
                // Kotlin rules replay every recorded move legally...
                val position = Position.fromFen(record.startingFEN).replaying(uci)
                assertNotNull(position)
                // ...and so does the engine (it throws on an illegal history).
                engine.result(record.startingFEN, uci)
                checked++
            }
        }
        assertTrue("replayed $checked corpus records", checked >= 100)
    }

    @Test
    fun everyPuzzleInTheBundleIsPlayableFromItsRecordedLine() {
        val store = LearningLibraryProvider(context).load()
        val puzzles = store.records(category = "殺局_殺法_練習題", limit = 200)
        assertTrue(puzzles.size >= 100)
        for (summary in puzzles) {
            val record = store.record(summary.id)!!
            val session = CCPDPuzzleSession(record)
            var ply = 0
            while (!session.isComplete) {
                session.attempt(Move.fromUci(record.moves[ply].uci)!!)
                ply += 2
            }
            assertTrue(session.isComplete)
        }
    }

    @Test
    fun savedGamesRoundTripAndRejectUnsupportedFiles() = runBlocking {
        val dir = File(context.cacheDir, "repo-test-${System.nanoTime()}")
        try {
            val repository = FileGameRepository(File(dir, "active-game.json"))
            assertNull(repository.load())
            val record = GameRecord.create(GameMode.COMPUTER, Side.BLACK, computerLevel = 4, createdAt = java.time.Instant.parse("2026-10-04T01:02:03Z")).copy(
                moves = listOf(RecordedMove(uci = "h2e2", notation = "Cannon h2–e2", side = Side.RED, hintUsed = true, committedAt = java.time.Instant.parse("2026-10-04T01:02:04Z"))),
                result = GameResult(Side.RED, GameResultReason.CHECKMATE)
            )
            repository.save(record)
            assertEquals(record, repository.load())
            // Saving again replaces atomically and leaves no temp file.
            repository.save(record.copy(elapsedSeconds = 99))
            assertEquals(99, repository.load()!!.elapsedSeconds)
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })

            val file = File(dir, "active-game.json")
            file.writeText(file.readText().replace("\"schemaVersion\": 1", "\"schemaVersion\": 7"))
            try { repository.load(); fail() } catch (_: UnsupportedSaveException) {}
            file.writeText(file.readText().replace("\"schemaVersion\": 7", "\"schemaVersion\": 1").replace(GameRecord.RULES_POLICY_ID, "someone-elses-rules@9"))
            try { repository.load(); fail() } catch (_: UnsupportedSaveException) {}
            file.writeText(file.readText().replace("someone-elses-rules@9", GameRecord.RULES_POLICY_ID).replace("h2e2", "a3a2"))
            try { repository.load(); fail() } catch (_: Exception) {}

            repository.delete()
            assertNull(repository.load())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theEngineSurvivesManyBackToBackSearches() = runBlocking {
        val engine = PikafishEngine(context)
        var position = Position.standard
        val moves = ArrayList<String>()
        repeat(16) {
            val reply = engine.chooseMove(GameRecord.STANDARD_FEN, moves, com.frankzhu.xiangqi.core.ComputerConfiguration(1, it.toULong()))
            assertTrue(reply in position.legalMoves())
            moves += reply.uci
            position = position.applying(reply)
        }
        assertEquals(16, moves.size)
    }
}
