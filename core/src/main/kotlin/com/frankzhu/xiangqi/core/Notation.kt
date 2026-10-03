package com.frankzhu.xiangqi.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object MoveNotation {
    fun display(move: Move, position: Position): String {
        val piece = position.pieceAt(move.from) ?: return move.uci
        val capture = if (position.pieceAt(move.to) == null) "–" else "×"
        return "${piece.kind.recordName} ${move.from.uci}$capture${move.to.uci}"
    }

    fun accessibility(move: Move, position: Position): String {
        val piece = position.pieceAt(move.from) ?: return move.uci
        return "${piece.side.recordName} ${piece.kind.recordName} from ${move.from.uci} to ${move.to.uci}"
    }
}

/** The interchange format shared with the iOS app: starting FEN plus ordered UCI moves. */
@Serializable
data class PortableGame(
    val format: String,
    val schemaVersion: Int,
    val rulesPolicyID: String,
    val startingFEN: String,
    val mode: GameMode,
    val moves: List<String>,
    val result: GameResult? = null
) {
    fun encoded(pretty: Boolean = true): String =
        (if (pretty) prettyJson else compactJson).encodeToString(this)

    companion object {
        const val FORMAT = "xiangqi-uci-json"

        fun from(record: GameRecord) = PortableGame(
            format = FORMAT,
            schemaVersion = record.schemaVersion,
            rulesPolicyID = record.rulesPolicyID,
            startingFEN = record.startingFEN,
            mode = record.mode,
            moves = record.uciMoves,
            result = record.result
        )

        fun decode(text: String): PortableGame = compactJson.decodeFromString(text)

        private val compactJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
        private val prettyJson = Json {
            ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; prettyPrint = true
        }
    }
}
