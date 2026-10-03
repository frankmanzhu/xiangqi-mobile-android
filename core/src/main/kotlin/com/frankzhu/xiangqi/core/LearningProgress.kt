package com.frankzhu.xiangqi.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

@Serializable
data class LearningItemProgress(
    val isBookmarked: Boolean = false,
    val lastPly: Int = 0,
    val attempts: Int = 0,
    val completions: Int = 0,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now()
)

@Serializable
data class LearningProgressSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val items: Map<String, LearningItemProgress> = emptyMap()
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

class UnsupportedLearningSchemaException(val version: Int) : Exception("Unsupported learning progress schema: $version")

/** Persists learning progress as JSON in the same shape the iOS app writes. */
class LearningProgressStore(private val file: File) {
    private var cached: LearningProgressSnapshot? = null
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun snapshot(): LearningProgressSnapshot {
        cached?.let { return it }
        if (!file.exists()) return LearningProgressSnapshot().also { cached = it }
        val decoded = json.decodeFromString<LearningProgressSnapshot>(file.readText())
        if (decoded.schemaVersion != LearningProgressSnapshot.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedLearningSchemaException(decoded.schemaVersion)
        }
        cached = decoded
        return decoded
    }

    fun progress(id: String): LearningItemProgress = snapshot().items[id] ?: LearningItemProgress()

    @Synchronized
    fun toggleBookmark(id: String): LearningItemProgress =
        update(id) { it.copy(isBookmarked = !it.isBookmarked) }

    @Synchronized
    fun recordOpened(id: String, lastPly: Int = 0) {
        update(id) { it.copy(attempts = it.attempts + 1, lastPly = maxOf(0, lastPly)) }
    }

    @Synchronized
    fun updateLastPly(ply: Int, id: String) {
        update(id) { it.copy(lastPly = maxOf(0, ply)) }
    }

    @Synchronized
    fun recordCompletion(id: String, finalPly: Int) {
        update(id) { it.copy(completions = it.completions + 1, lastPly = maxOf(0, finalPly)) }
    }

    @Synchronized
    fun bookmarkedIds(): List<String> = snapshot().items.entries
        .filter { it.value.isBookmarked }
        .sortedByDescending { it.value.updatedAt }
        .map { it.key }

    private fun update(id: String, change: (LearningItemProgress) -> LearningItemProgress): LearningItemProgress {
        val state = snapshot()
        val item = change(state.items[id] ?: LearningItemProgress()).copy(updatedAt = Instant.now())
        save(state.copy(items = state.items + (id to item)))
        return item
    }

    private fun save(snapshot: LearningProgressSnapshot) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(snapshot))
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Could not replace ${file.path}" }
        }
        cached = snapshot
    }
}
