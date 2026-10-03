package com.frankzhu.xiangqi.core

sealed class PositionException(message: String) : Exception(message) {
    class InvalidFEN(detail: String) : PositionException("Invalid FEN: $detail")
    class InvalidMove(move: String) : PositionException("Invalid move: $move")
    class IllegalMove(move: String) : PositionException("Illegal move: $move")
}

class Position(
    val pieces: Map<Square, Piece>,
    val sideToMove: Side,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1
) {
    fun pieceAt(square: Square): Piece? = pieces[square]

    val fen: String
        get() {
            val rows = (9 downTo 0).map { rank ->
                buildString {
                    var empty = 0
                    for (file in 0..8) {
                        val piece = pieces[Square(file, rank)]
                        if (piece != null) {
                            if (empty > 0) { append(empty); empty = 0 }
                            append(fenCharacter(piece))
                        } else {
                            empty++
                        }
                    }
                    if (empty > 0) append(empty)
                }
            }
            return "${rows.joinToString("/")} ${if (sideToMove == Side.RED) "w" else "b"} - - $halfmoveClock $fullmoveNumber"
        }

    /** Applies [move], optionally verifying it is legal first. */
    fun applying(move: Move, validate: Boolean = true): Position {
        val moving = pieces[move.from]
        if (!move.from.isValid || !move.to.isValid || moving == null) {
            throw PositionException.InvalidMove(move.uci)
        }
        if (validate && move !in legalMoves()) throw PositionException.IllegalMove(move.uci)
        val next = pieces.toMutableMap()
        val captured = next.remove(move.to)
        next.remove(move.from)
        next[move.to] = moving
        return Position(
            pieces = next,
            sideToMove = sideToMove.opponent,
            halfmoveClock = if (captured != null || moving.kind == PieceKind.SOLDIER) 0 else halfmoveClock + 1,
            fullmoveNumber = if (sideToMove == Side.BLACK) fullmoveNumber + 1 else fullmoveNumber
        )
    }

    fun replaying(moves: List<String>): Position {
        var position = this
        for (uci in moves) {
            val move = Move.fromUci(uci) ?: throw PositionException.InvalidMove(uci)
            position = position.applying(move)
        }
        return position
    }

    override fun equals(other: Any?): Boolean =
        other is Position && pieces == other.pieces && sideToMove == other.sideToMove &&
            halfmoveClock == other.halfmoveClock && fullmoveNumber == other.fullmoveNumber

    override fun hashCode(): Int = pieces.hashCode() * 31 + sideToMove.hashCode()

    companion object {
        val standard: Position by lazy { fromFen(GameRecord.STANDARD_FEN) }

        fun fromFen(fen: String): Position {
            val fields = fen.split(' ').filter { it.isNotEmpty() }
            if (fields.size < 2) throw PositionException.InvalidFEN("missing fields")
            val rows = fields[0].split('/')
            if (rows.size != 10) throw PositionException.InvalidFEN("expected 10 ranks")

            val parsed = HashMap<Square, Piece>()
            for ((rowIndex, row) in rows.withIndex()) {
                var file = 0
                val rank = 9 - rowIndex
                for (character in row) {
                    if (character in '0'..'9') {
                        file += character - '0'
                        continue
                    }
                    val identity = identity(character)
                    if (file >= 9 || identity == null) {
                        throw PositionException.InvalidFEN("unknown piece or rank overflow")
                    }
                    parsed[Square(file, rank)] = identity
                    file++
                }
                if (file != 9) throw PositionException.InvalidFEN("rank $rank has $file files")
            }
            if (fields[1] != "w" && fields[1] != "b") throw PositionException.InvalidFEN("side to move")
            val generals = parsed.values.filter { it.kind == PieceKind.GENERAL }
            if (generals.count { it.side == Side.RED } != 1 || generals.count { it.side == Side.BLACK } != 1) {
                throw PositionException.InvalidFEN("expected one general per side")
            }
            return Position(
                pieces = parsed,
                sideToMove = if (fields[1] == "w") Side.RED else Side.BLACK,
                halfmoveClock = fields.getOrNull(4)?.toIntOrNull() ?: 0,
                fullmoveNumber = fields.getOrNull(5)?.toIntOrNull() ?: 1
            )
        }

        private fun identity(character: Char): Piece? {
            val side = if (character.isUpperCase()) Side.RED else Side.BLACK
            val kind = when (character.lowercaseChar()) {
                'k' -> PieceKind.GENERAL
                'a' -> PieceKind.ADVISOR
                'b' -> PieceKind.ELEPHANT
                'n', 'h' -> PieceKind.HORSE
                'r' -> PieceKind.CHARIOT
                'c' -> PieceKind.CANNON
                'p' -> PieceKind.SOLDIER
                else -> null
            }
            return kind?.let { Piece(side, it) }
        }

        private fun fenCharacter(piece: Piece): Char {
            val value = when (piece.kind) {
                PieceKind.GENERAL -> 'k'
                PieceKind.ADVISOR -> 'a'
                PieceKind.ELEPHANT -> 'b'
                PieceKind.HORSE -> 'n'
                PieceKind.CHARIOT -> 'r'
                PieceKind.CANNON -> 'c'
                PieceKind.SOLDIER -> 'p'
            }
            return if (piece.side == Side.RED) value.uppercaseChar() else value
        }
    }
}
