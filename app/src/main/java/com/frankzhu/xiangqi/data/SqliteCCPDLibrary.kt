package com.frankzhu.xiangqi.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.frankzhu.xiangqi.core.CCPDCategorySummary
import com.frankzhu.xiangqi.core.CCPDLibrary
import com.frankzhu.xiangqi.core.CCPDLibraryException
import com.frankzhu.xiangqi.core.CCPDRecord
import com.frankzhu.xiangqi.core.CCPDRecordSummary
import com.frankzhu.xiangqi.core.decodeCCPDRecord
import java.io.File

/** A read-only view of one CCPD SQLite corpus, using the same schema as the iOS app. */
class SqliteCCPDLibrary(
    private val databaseFile: File,
    private val chinese: ChineseVariants = ChineseVariants.None
) : CCPDLibrary {

    override fun validate() {
        val version = metadata()["schema_version"]
        if (version != "2") throw CCPDLibraryException.UnsupportedSchema(version)
    }

    override fun metadata(): Map<String, String> = withDatabase { db ->
        db.rawQuery("SELECT key, value FROM metadata ORDER BY key", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
    }

    override fun categories(): List<CCPDCategorySummary> = withDatabase { db ->
        db.rawQuery("SELECT category, COUNT(*) FROM records GROUP BY category ORDER BY category", null).use { c ->
            buildList { while (c.moveToNext()) add(CCPDCategorySummary(c.getString(0), c.getInt(1))) }
        }
    }

    override fun records(
        category: String?,
        query: String?,
        sourcePrefix: String?,
        limit: Int,
        offset: Int
    ): List<CCPDRecordSummary> = withDatabase { db ->
        val predicates = ArrayList<String>()
        val bindings = ArrayList<String>()
        if (!category.isNullOrEmpty()) {
            predicates += "category = ?"; bindings += category
        }
        if (!sourcePrefix.isNullOrEmpty()) {
            predicates += "source_path LIKE ? ESCAPE '\\'"
            bindings += escapedLike(sourcePrefix) + "%"
        }
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            val columns = listOf("event", "red", "black", "ecco", "date_text", "result", "source_path")
            val variants = chinese.variants(trimmed)
            val clause = columns.joinToString(" OR ", "(", ")") { "$it LIKE ? ESCAPE '\\'" }
            predicates += variants.joinToString(" OR ", "(", ")") { clause }
            for (variant in variants) repeat(columns.size) { bindings += "%${escapedLike(variant)}%" }
        }
        val where = if (predicates.isEmpty()) "" else " WHERE " + predicates.joinToString(" AND ")
        val sql = """
            SELECT id, category, source_path, event, date_text, red, black, result, ecco, move_count
            FROM records$where
            ORDER BY date_text DESC, source_path ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        bindings += limit.coerceIn(1, 500).toString()
        bindings += maxOf(offset, 0).toString()
        db.rawQuery(sql, bindings.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) summary(c)?.let(::add) }
        }
    }

    override fun record(id: String): CCPDRecord? = withDatabase { db ->
        val sql = """
            SELECT id, category, source_path, event, date_text, red, black, result, ecco,
                   move_count, source_encoding, starting_fen, uci_moves, source_moves, tags_json
            FROM records WHERE id = ? LIMIT 1
        """.trimIndent()
        db.rawQuery(sql, arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return@use null
            val summary = summary(c) ?: throw CCPDLibraryException.CorruptRecord(id)
            val encoding = c.getString(10)
            val fen = c.getString(11)
            val uci = c.getBlob(12)
            val source = c.getBlob(13)
            val tags = c.getBlob(14)
            if (encoding == null || fen == null || uci == null || source == null || tags == null) {
                throw CCPDLibraryException.CorruptRecord(id)
            }
            decodeCCPDRecord(summary, encoding, fen, uci, source, tags)
        }
    }

    private fun summary(c: Cursor): CCPDRecordSummary? {
        val id = c.getString(0) ?: return null
        val category = c.getString(1) ?: return null
        val path = c.getString(2) ?: return null
        return CCPDRecordSummary(
            id, category, path,
            c.getString(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8),
            c.getInt(9)
        )
    }

    private fun <T> withDatabase(block: (SQLiteDatabase) -> T): T {
        val db = try {
            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw CCPDLibraryException.DatabaseUnavailable(e.message ?: databaseFile.path)
        }
        try {
            return block(db)
        } catch (e: CCPDLibraryException) {
            throw e
        } catch (e: android.database.SQLException) {
            throw CCPDLibraryException.DatabaseUnavailable(e.message ?: "query failed")
        } finally {
            db.close()
        }
    }

    private fun escapedLike(value: String) =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        /** Creates the writable database used for user-imported games, if absent. */
        fun createEmptyUserDatabase(file: File) {
            if (file.exists()) return
            file.parentFile?.mkdirs()
            val db = try {
                SQLiteDatabase.openOrCreateDatabase(file, null)
            } catch (e: Exception) {
                throw CCPDLibraryException.DatabaseUnavailable(e.message ?: file.path)
            }
            try {
                db.execSQL("PRAGMA user_version = 2")
                db.execSQL("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS records (
                        id TEXT PRIMARY KEY NOT NULL, category TEXT NOT NULL, source_path TEXT NOT NULL,
                        source_encoding TEXT NOT NULL, event TEXT, date_text TEXT, site TEXT, red TEXT,
                        black TEXT, result TEXT, ecco TEXT, starting_fen TEXT NOT NULL,
                        move_count INTEGER NOT NULL, uci_moves BLOB NOT NULL, source_moves BLOB NOT NULL,
                        tags_json BLOB NOT NULL)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS records_category_idx ON records(category)")
                db.execSQL("INSERT OR IGNORE INTO metadata (key, value) VALUES ('schema_version', '2')")
                db.execSQL("INSERT OR IGNORE INTO metadata (key, value) VALUES ('source_name', 'User-imported games')")
                db.execSQL("INSERT OR IGNORE INTO metadata (key, value) VALUES ('license', 'User-managed records')")
            } finally {
                db.close()
            }
        }
    }
}
