package com.frankzhu.xiangqi.core

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

class CCPDCompressionException(message: String) : Exception(message)

/** The 4-byte big-endian length prefix + zlib frame used by the CCPD database blobs. */
object CCPDCompression {
    fun compress(data: ByteArray): ByteArray {
        if (data.isEmpty()) return byteArrayOf(0, 0, 0, 0)
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val size = data.size
        out.write(byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()))
        val buffer = ByteArray(8192)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    fun decompress(data: ByteArray): ByteArray {
        if (data.size < 4) throw CCPDCompressionException("malformed frame")
        val size = ((data[0].toInt() and 0xff) shl 24) or ((data[1].toInt() and 0xff) shl 16) or
            ((data[2].toInt() and 0xff) shl 8) or (data[3].toInt() and 0xff)
        if (size == 0) return ByteArray(0)
        if (size < 0) throw CCPDCompressionException("malformed frame")
        val inflater = Inflater()
        try {
            inflater.setInput(data, 4, data.size - 4)
            val output = ByteArray(size)
            var produced = 0
            while (produced < size) {
                val n = inflater.inflate(output, produced, size - produced)
                if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                produced += n
            }
            if (produced != size) throw CCPDCompressionException("decompressed length mismatch")
            return output
        } catch (e: DataFormatException) {
            throw CCPDCompressionException("decompression failed: ${e.message}")
        } finally {
            inflater.end()
        }
    }
}

sealed class CCPDLibraryException(message: String) : Exception(message) {
    class DatabaseUnavailable(detail: String) : CCPDLibraryException("CCPD library is unavailable: $detail")
    class UnsupportedSchema(version: String?) :
        CCPDLibraryException("Unsupported CCPD library schema: ${version ?: "missing"}")
    class CorruptRecord(detail: String) : CCPDLibraryException("Corrupt CCPD record: $detail")
}

data class CCPDCategorySummary(val id: String, val recordCount: Int)

data class CCPDRecordSummary(
    val id: String,
    val category: String,
    val sourcePath: String,
    val event: String?,
    val dateText: String?,
    val red: String?,
    val black: String?,
    val result: String?,
    val ecco: String?,
    val moveCount: Int
)

data class CCPDRecord(
    val summary: CCPDRecordSummary,
    val sourceEncoding: XiangqiPGNTextEncoding,
    val startingFEN: String,
    val moves: List<NormalizedXiangqiPGNMove>,
    val tags: Map<String, String>
) {
    fun positionAfterPly(ply: Int): Position {
        val clamped = ply.coerceIn(0, moves.size)
        return Position.fromFen(startingFEN).replaying(moves.take(clamped).map { it.uci })
    }
}

/** Read-only access to one CCPD corpus. The SQLite implementation lives in the app. */
interface CCPDLibrary {
    fun validate()
    fun metadata(): Map<String, String>
    fun categories(): List<CCPDCategorySummary>
    fun records(
        category: String? = null,
        query: String? = null,
        sourcePrefix: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<CCPDRecordSummary>
    fun record(id: String): CCPDRecord?
}

/** Splits a packed UCI string ("h2e2h9g7") into its four-character moves. */
fun unpackUciMoves(packed: String, recordId: String): List<String> {
    if (packed.length % 4 != 0 || packed.any { it.code > 127 }) {
        throw CCPDLibraryException.CorruptRecord("$recordId: malformed packed UCI moves")
    }
    return packed.chunked(4)
}

/** Builds a [CCPDRecord] from the raw database columns, validating counts. */
fun decodeCCPDRecord(
    summary: CCPDRecordSummary,
    encodingRaw: String,
    startingFEN: String,
    packedUciMoves: ByteArray,
    packedSourceMoves: ByteArray,
    packedTags: ByteArray
): CCPDRecord {
    val id = summary.id
    val encoding = XiangqiPGNTextEncoding.fromRaw(encodingRaw) ?: throw CCPDLibraryException.CorruptRecord(id)
    val uciText = String(CCPDCompression.decompress(packedUciMoves), Charsets.UTF_8)
    val sourceText = String(CCPDCompression.decompress(packedSourceMoves), Charsets.UTF_8)
    val tagsText = String(CCPDCompression.decompress(packedTags), Charsets.UTF_8)
    val uci = unpackUciMoves(uciText, id)
    val source = if (sourceText.isEmpty()) emptyList() else sourceText.split('\u001F')
    if (uci.size != source.size) {
        throw CCPDLibraryException.CorruptRecord("$id: source/UCI move count mismatch")
    }
    val moves = source.zip(uci).mapIndexed { index, (notation, move) ->
        NormalizedXiangqiPGNMove(index + 1, notation, move)
    }
    val tags = kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(tagsText)
    return CCPDRecord(summary, encoding, startingFEN, moves, tags)
}

/**
 * Presents the immutable shipped corpus and the user's writable corpus as one
 * learning library. Updates replace only the bundled database.
 */
class LearningLibraryStore(val bundled: CCPDLibrary, val user: CCPDLibrary) {
    init {
        bundled.validate()
        user.validate()
    }

    fun metadata(): Map<String, String> {
        val result = bundled.metadata().toMutableMap()
        result["user_record_count"] = user.categories().sumOf { it.recordCount }.toString()
        return result
    }

    fun categories(): List<CCPDCategorySummary> {
        val counts = sortedMapOf<String, Int>()
        for (summary in bundled.categories() + user.categories()) {
            counts[summary.id] = (counts[summary.id] ?: 0) + summary.recordCount
        }
        return counts.map { CCPDCategorySummary(it.key, it.value) }
    }

    fun records(
        category: String? = null,
        query: String? = null,
        sourcePrefix: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<CCPDRecordSummary> {
        val requestedLimit = limit.coerceIn(1, 500)
        val requestedOffset = maxOf(offset, 0)
        val perDatabase = minOf(requestedLimit + requestedOffset, 500)
        val combined = bundled.records(category, query, sourcePrefix, perDatabase, 0) +
            user.records(category, query, sourcePrefix, perDatabase, 0)
        val ordered = combined.sortedWith(
            compareByDescending<CCPDRecordSummary> { it.dateText ?: "" }.thenBy { it.sourcePath }
        )
        return ordered.drop(requestedOffset).take(requestedLimit)
    }

    fun record(id: String): CCPDRecord? {
        if (id.startsWith("user:")) return user.record(id)
        return bundled.record(id) ?: user.record(id)
    }
}
