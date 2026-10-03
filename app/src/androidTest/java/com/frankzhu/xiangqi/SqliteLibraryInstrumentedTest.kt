package com.frankzhu.xiangqi

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frankzhu.xiangqi.core.CCPDCategorySummary
import com.frankzhu.xiangqi.core.CCPDCompression
import com.frankzhu.xiangqi.core.CCPDPuzzleSession
import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.core.LearningLibraryStore
import com.frankzhu.xiangqi.core.PieceKind
import com.frankzhu.xiangqi.core.Square
import com.frankzhu.xiangqi.data.ChineseVariants
import com.frankzhu.xiangqi.data.LearningLibraryProvider
import com.frankzhu.xiangqi.data.SqliteCCPDLibrary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Ported from the iOS `CCPDLibraryTests`, against Android's SQLite. */
@RunWith(AndroidJUnit4::class)
class SqliteLibraryInstrumentedTest {
    private lateinit var dir: File
    private lateinit var fixture: File
    private val chinese by lazy { ChineseVariants.forContext(InstrumentationRegistry.getInstrumentation().targetContext) }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dir = File(context.cacheDir, "ccpd-test-${System.nanoTime()}").also { it.mkdirs() }
        fixture = File(dir, "fixture.sqlite3")
        createFixture(fixture)
    }

    @After
    fun tearDown() { dir.deleteRecursively() }

    @Test
    fun validatesMetadataAndListsCategories() {
        val library = SqliteCCPDLibrary(fixture)
        library.validate()
        assertEquals("fixture-revision", library.metadata()["source_revision"])
        assertEquals(listOf(CCPDCategorySummary("開局", 1)), library.categories())
    }

    @Test
    fun searchesMetadataAcrossScriptsAndLoadsReplayableRecord() {
        val library = SqliteCCPDLibrary(fixture, chinese)
        val summaries = library.records(category = "開局", query = "測試")
        assertEquals(1, summaries.size)
        assertEquals("劉憶慈", summaries[0].red)
        assertEquals(1, summaries[0].moveCount)

        val record = library.record("ccpd:開局/fixture")!!
        assertEquals(listOf("h2e2"), record.moves.map { it.uci })
        assertEquals("測試對局", record.tags["Event"])
        assertEquals(PieceKind.CANNON, record.positionAfterPly(1).pieceAt(Square(4, 2))?.kind)

        // A Simplified-Chinese query finds Traditional records, and vice versa.
        assertEquals(listOf("ccpd:開局/fixture"), library.records(query = "刘忆慈").map { it.id })
        assertEquals(listOf("ccpd:開局/fixture"), library.records(query = "2026").map { it.id })
        // LIKE wildcards in user input are treated literally.
        assertTrue(library.records(query = "%").isEmpty())
        assertTrue(library.records(query = "_").isEmpty())
    }

    @Test
    fun filtersBySourcePrefixAndReturnsNullForUnknownRecord() {
        val library = SqliteCCPDLibrary(fixture)
        assertEquals(listOf("ccpd:開局/fixture"), library.records(sourcePrefix = "開局/").map { it.id })
        assertTrue(library.records(sourcePrefix = "對局/").isEmpty())
        assertNull(library.record("missing"))
    }

    @Test
    fun learningStoreCreatesSeparateUserDatabaseAndCombinesLibraries() {
        val userFile = File(dir, "user-games.sqlite3")
        SqliteCCPDLibrary.createEmptyUserDatabase(userFile)
        assertTrue(userFile.exists())
        val store = LearningLibraryStore(SqliteCCPDLibrary(fixture), SqliteCCPDLibrary(userFile))
        assertEquals(listOf(CCPDCategorySummary("開局", 1)), store.categories())
        assertEquals(listOf("ccpd:開局/fixture"), store.records(category = "開局", query = "測試").map { it.id })
        assertEquals("ccpd:開局/fixture", store.record("ccpd:開局/fixture")?.summary?.id)
    }

    @Test
    fun bundledCorpusMetadataSearchAndReplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LearningLibraryProvider(context).load()
        val metadata = store.metadata()
        assertEquals("368a47a947773dd8692c026e286dd19b6277b993", metadata["source_revision"])
        // Lite subset ~9.5k records; the full corpus (linked from the iOS repo) is 145,065.
        assertTrue(store.categories().sumOf { it.recordCount } >= 9_000)
        assertFalse(store.records(query = "刘").isEmpty())

        val summary = store.records(category = "殺局_殺法_練習題", limit = 1).first()
        val record = assertNotNull(store.record(summary.id)).let { store.record(summary.id)!! }
        assertEquals(summary.moveCount, record.moves.size)
        record.positionAfterPly(record.moves.size)
        // Every puzzle in the bundle must be playable from its recorded line.
        CCPDPuzzleSession(record)
    }

    private fun assertNotNull(value: Any?): Any = org.junit.Assert.assertNotNull(value).let { value!! }

    private fun createFixture(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE records (id TEXT PRIMARY KEY NOT NULL, category TEXT NOT NULL, source_path TEXT NOT NULL,
               source_encoding TEXT NOT NULL, event TEXT, date_text TEXT, site TEXT, red TEXT, black TEXT, result TEXT,
               ecco TEXT, starting_fen TEXT NOT NULL, move_count INTEGER NOT NULL, uci_moves BLOB NOT NULL,
               source_moves BLOB NOT NULL, tags_json BLOB NOT NULL)"""
        )
        db.execSQL("INSERT INTO metadata VALUES ('schema_version', '2')")
        db.execSQL("INSERT INTO metadata VALUES ('source_revision', 'fixture-revision')")
        val values = android.content.ContentValues().apply {
            put("id", "ccpd:開局/fixture"); put("category", "開局"); put("source_path", "開局/fixture.pgn")
            put("source_encoding", "big5"); put("event", "測試對局"); put("date_text", "2026"); put("site", "悉尼")
            put("red", "劉憶慈"); put("black", "黑方"); put("result", "*"); put("ecco", "C00")
            put("starting_fen", GameRecord.STANDARD_FEN); put("move_count", 1)
            put("uci_moves", CCPDCompression.compress("h2e2".toByteArray()))
            put("source_moves", CCPDCompression.compress("炮二平五".toByteArray()))
            put("tags_json", CCPDCompression.compress("""{"Event":"測試對局","Red":"劉憶慈","Black":"黑方"}""".toByteArray()))
        }
        db.insertOrThrow("records", null, values)
        db.close()
    }
}
