package com.frankzhu.xiangqi.data

import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.core.GameRepository
import com.frankzhu.xiangqi.core.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class UnsupportedSaveException(message: String) : Exception(message)

/**
 * Stores the one active game as JSON, in the same shape the iOS app writes, and
 * replaces the file atomically so an interrupted save never corrupts it.
 */
class FileGameRepository(private val file: File) : GameRepository {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    override suspend fun load(): GameRecord? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val record = json.decodeFromString<GameRecord>(file.readText())
        if (record.schemaVersion != GameRecord.SCHEMA_VERSION) {
            throw UnsupportedSaveException("Unsupported schema ${record.schemaVersion}")
        }
        if (record.rulesPolicyID !in setOf(GameRecord.RULES_POLICY_ID, GameRecord.LEGACY_RULES_POLICY_ID)) {
            throw UnsupportedSaveException("Unsupported rules policy ${record.rulesPolicyID}")
        }
        Position.fromFen(record.startingFEN).replaying(record.uciMoves)
        record
    }

    override suspend fun save(record: GameRecord) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(record))
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Could not replace ${file.path}" }
        }
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
        Unit
    }
}
