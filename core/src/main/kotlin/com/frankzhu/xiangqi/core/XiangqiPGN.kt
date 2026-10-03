package com.frankzhu.xiangqi.core

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.abs

sealed class XiangqiPGNException(message: String) : Exception(message) {
    object UnsupportedTextEncoding : XiangqiPGNException("PGN text is neither valid UTF-8 nor Big5.")
    class MalformedTag(line: String) : XiangqiPGNException("Malformed PGN tag: $line")
    object MissingFEN : XiangqiPGNException("PGN does not contain a FEN tag.")
    class InvalidNotation(val notation: String) : XiangqiPGNException("Unsupported Xiangqi notation: $notation")
    class AmbiguousNotation(val notation: String, val candidates: List<String>) :
        XiangqiPGNException("Ambiguous Xiangqi notation $notation: ${candidates.joinToString(", ")}")
    class IllegalMove(val ply: Int, val notation: String, val detail: String) :
        XiangqiPGNException("Illegal move at ply $ply, $notation: $detail")
}

@Serializable
enum class XiangqiPGNTextEncoding {
    @kotlinx.serialization.SerialName("utf8") UTF8,
    @kotlinx.serialization.SerialName("big5") BIG5,
    @kotlinx.serialization.SerialName("big5HKSCS") BIG5_HKSCS;

    /** The raw token stored in the CCPD database `source_encoding` column. */
    val raw: String get() = when (this) { UTF8 -> "utf8"; BIG5 -> "big5"; BIG5_HKSCS -> "big5HKSCS" }

    companion object {
        fun fromRaw(value: String): XiangqiPGNTextEncoding? = entries.firstOrNull { it.raw == value }
    }
}

data class DecodedXiangqiPGN(val text: String, val encoding: XiangqiPGNTextEncoding)

object XiangqiPGNTextDecoder {
    fun decode(data: ByteArray): DecodedXiangqiPGN {
        strict(data, Charsets.UTF_8)?.let {
            return DecodedXiangqiPGN(it.removePrefix("﻿"), XiangqiPGNTextEncoding.UTF8)
        }
        strict(data, Charset.forName("Big5"))?.let {
            return DecodedXiangqiPGN(it, XiangqiPGNTextEncoding.BIG5)
        }
        strict(data, Charset.forName("Big5-HKSCS"))?.let {
            return DecodedXiangqiPGN(it, XiangqiPGNTextEncoding.BIG5_HKSCS)
        }
        throw XiangqiPGNException.UnsupportedTextEncoding
    }

    private fun strict(data: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

data class XiangqiPGNGame(
    val tags: Map<String, String>,
    val sourceMoves: List<String>,
    val comments: List<String>,
    val result: String?,
    val rawMovetext: String
)

object XiangqiPGNParser {
    private val resultTokens = setOf("1-0", "0-1", "1/2-1/2", "*")
    private val lineBreak = Regex("\r\n|[\n\r\u0085  ]")

    fun parse(text: String): XiangqiPGNGame {
        val tags = LinkedHashMap<String, String>()
        val movetextLines = ArrayList<String>()
        var inTagSection = true

        for (rawLine in text.split(lineBreak)) {
            val line = rawLine.trim()
            if (inTagSection && line.startsWith("[")) {
                val (key, value) = parseTag(line)
                tags[key] = value
            } else {
                if (line.isNotEmpty()) inTagSection = false
                movetextLines.add(rawLine)
            }
        }

        val rawMovetext = movetextLines.joinToString("\n")
        val scanned = scanMainline(rawMovetext)
        val moves = ArrayList<String>()
        var result = tags["Result"]

        for (rawToken in scanned.first) {
            val token = moveToken(rawToken) ?: continue
            if (token in resultTokens) {
                result = token
            } else if (!token.startsWith("$")) {
                moves.add(token)
            }
        }
        return XiangqiPGNGame(tags, moves, scanned.second, result, rawMovetext)
    }

    private fun parseTag(line: String): Pair<String, String> {
        if (line.first() != '[' || line.last() != ']') throw XiangqiPGNException.MalformedTag(line)
        val body = line.substring(1, line.length - 1)
        val separator = body.indexOfFirst { it.isWhitespace() }
        if (separator < 0) throw XiangqiPGNException.MalformedTag(line)
        val key = body.substring(0, separator)
        val remainder = body.substring(separator).trim(' ', '\t')
        if (remainder.length < 2 || remainder.first() != '"' || remainder.last() != '"') {
            throw XiangqiPGNException.MalformedTag(line)
        }
        val value = remainder.substring(1, remainder.length - 1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        return key to value
    }

    private fun scanMainline(text: String): Pair<List<String>, List<String>> {
        val visible = StringBuilder()
        val comments = ArrayList<String>()
        val comment = StringBuilder()
        var braceDepth = 0
        var variationDepth = 0
        var semicolonComment = false

        for (character in text) {
            if (semicolonComment) {
                if (character == '\n') {
                    semicolonComment = false
                    if (comment.isNotEmpty()) comments.add(comment.toString().trim(' ', '\t'))
                    comment.clear()
                    visible.append(' ')
                } else {
                    comment.append(character)
                }
                continue
            }
            if (braceDepth > 0) {
                when (character) {
                    '{' -> braceDepth++
                    '}' -> {
                        braceDepth--
                        if (braceDepth == 0) {
                            val value = comment.toString().trim()
                            if (value.isNotEmpty()) comments.add(value)
                            comment.clear()
                            visible.append(' ')
                        }
                    }
                    else -> comment.append(character)
                }
                continue
            }
            if (variationDepth > 0) {
                if (character == '(') variationDepth++ else if (character == ')') variationDepth--
                continue
            }
            when (character) {
                '{' -> braceDepth = 1
                '(' -> variationDepth = 1
                ';' -> semicolonComment = true
                else -> visible.append(character)
            }
        }
        if (comment.isNotEmpty()) comments.add(comment.toString().trim())
        return visible.toString().split(Regex("\\s+")).filter { it.isNotEmpty() } to comments
    }

    private fun isNumberCharacter(c: Char): Boolean {
        if (Character.isDigit(c)) return true
        val type = Character.getType(c)
        return type == Character.LETTER_NUMBER.toInt() || type == Character.OTHER_NUMBER.toInt()
    }

    private fun moveToken(rawToken: String): String? {
        var token = rawToken.trim()
        if (token.isEmpty()) return null

        val dot = token.indexOf('.')
        if (dot >= 0 && token.substring(0, dot).all(::isNumberCharacter)) {
            token = token.substring(dot + 1).trimStart('.')
        }
        if (token.isEmpty()) return null

        token = token.trim('!', '?', '+', '#')
        return token.ifEmpty { null }
    }
}

@Serializable
data class NormalizedXiangqiPGNMove(val ply: Int, val sourceNotation: String, val uci: String)

@Serializable
data class NormalizedXiangqiPGNGame(
    val tags: Map<String, String>,
    val startingFEN: String,
    val moves: List<NormalizedXiangqiPGNMove>,
    val result: String? = null
)

object XiangqiPGNNormalizer {
    fun normalize(game: XiangqiPGNGame): NormalizedXiangqiPGNGame {
        val startingFEN = game.tags["FEN"] ?: throw XiangqiPGNException.MissingFEN
        var position = Position.fromFen(startingFEN)
        val normalized = ArrayList<NormalizedXiangqiPGNMove>()

        for ((index, notation) in game.sourceMoves.withIndex()) {
            try {
                val move = ChineseMoveNotationParser.parse(notation, position)
                position = position.applying(move)
                normalized.add(NormalizedXiangqiPGNMove(index + 1, notation, move.uci))
            } catch (error: XiangqiPGNException) {
                throw XiangqiPGNException.IllegalMove(
                    index + 1, notation, "${error.message}; position ${position.fen}"
                )
            } catch (error: PositionException) {
                throw XiangqiPGNException.IllegalMove(
                    index + 1, notation, "${error.message}; position ${position.fen}"
                )
            }
        }
        return NormalizedXiangqiPGNGame(game.tags, startingFEN, normalized, game.result)
    }
}

object ChineseMoveNotationParser {
    private enum class Direction { ADVANCE, RETREAT, HORIZONTAL }
    private enum class Relative { FRONT, MIDDLE, BACK }

    fun parse(source: String, position: Position): Move {
        val characters = normalize(source).toList()
        val direction = characters.getOrNull(2)?.let(::direction)
        val destination = characters.getOrNull(3)?.let(::number)
        if (characters.size != 4 || direction == null || destination == null) {
            throw XiangqiPGNException.InvalidNotation(source)
        }

        val side = position.sideToMove
        val kind: PieceKind
        val candidates: List<Square>

        val directKind = pieceKind(characters[0])
        val sourceFileNumber = number(characters[1])
        val relative = relative(characters[0])
        val relativeKind = pieceKind(characters[1])
        if (directKind != null && sourceFileNumber != null) {
            kind = directKind
            val file = boardFile(sourceFileNumber, side)
            candidates = position.pieces.mapNotNull { (square, piece) ->
                if (piece.side == side && piece.kind == kind && square.file == file) square else null
            }
        } else if (relative != null && relativeKind != null) {
            kind = relativeKind
            val all = position.pieces.mapNotNull { (square, piece) ->
                if (piece.side == side && piece.kind == kind) square else null
            }
            candidates = relativeCandidates(all, relative, side)
        } else {
            throw XiangqiPGNException.InvalidNotation(source)
        }

        val matches = position.legalMoves().filter { move ->
            move.from in candidates && matches(move, kind, direction, destination, side)
        }
        if (matches.size != 1) {
            if (matches.isEmpty()) throw XiangqiPGNException.InvalidNotation(source)
            throw XiangqiPGNException.AmbiguousNotation(source, matches.map { it.uci })
        }
        return matches[0]
    }

    private fun normalize(source: String): String = source.trim()
        .replace("进", "進").replace("后", "後").replace("车", "車")
        .replace("马", "馬").replace("帅", "帥").replace("将", "將")

    private fun pieceKind(c: Char): PieceKind? = when (c) {
        '帥', '將' -> PieceKind.GENERAL
        '仕', '士' -> PieceKind.ADVISOR
        '相', '象' -> PieceKind.ELEPHANT
        '馬', '傌' -> PieceKind.HORSE
        '車', '俥' -> PieceKind.CHARIOT
        '炮', '砲' -> PieceKind.CANNON
        '兵', '卒' -> PieceKind.SOLDIER
        else -> null
    }

    private fun direction(c: Char): Direction? = when (c) {
        '進' -> Direction.ADVANCE
        '退' -> Direction.RETREAT
        '平' -> Direction.HORIZONTAL
        else -> null
    }

    private fun relative(c: Char): Relative? = when (c) {
        '前' -> Relative.FRONT
        '中' -> Relative.MIDDLE
        '後' -> Relative.BACK
        else -> null
    }

    private fun number(c: Char): Int? = when (c) {
        '一', '１', '1' -> 1
        '二', '２', '2' -> 2
        '三', '３', '3' -> 3
        '四', '４', '4' -> 4
        '五', '５', '5' -> 5
        '六', '６', '6' -> 6
        '七', '７', '7' -> 7
        '八', '８', '8' -> 8
        '九', '９', '9' -> 9
        else -> null
    }

    private fun boardFile(number: Int, side: Side): Int = if (side == Side.RED) 9 - number else number - 1

    private fun matches(move: Move, kind: PieceKind, direction: Direction, destination: Int, side: Side): Boolean {
        val rankDelta = move.to.rank - move.from.rank
        return when (direction) {
            Direction.HORIZONTAL -> rankDelta == 0 && boardFile(destination, side) == move.to.file
            Direction.ADVANCE, Direction.RETREAT -> {
                val isAdvance = rankDelta * side.forward > 0
                if (isAdvance != (direction == Direction.ADVANCE)) return false
                when (kind) {
                    PieceKind.HORSE, PieceKind.ELEPHANT, PieceKind.ADVISOR ->
                        boardFile(destination, side) == move.to.file
                    PieceKind.GENERAL, PieceKind.CHARIOT, PieceKind.CANNON, PieceKind.SOLDIER ->
                        abs(rankDelta) == destination
                }
            }
        }
    }

    private fun relativeCandidates(squares: List<Square>, relative: Relative, side: Side): List<Square> =
        squares.groupBy { it.file }.values.filter { it.size > 1 }.mapNotNull { group ->
            val ordered = if (side == Side.RED) group.sortedByDescending { it.rank } else group.sortedBy { it.rank }
            when (relative) {
                Relative.FRONT -> ordered.first()
                Relative.BACK -> ordered.last()
                Relative.MIDDLE -> if (ordered.size % 2 == 1) ordered[ordered.size / 2] else null
            }
        }
}
