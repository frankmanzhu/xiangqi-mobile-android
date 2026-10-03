package com.frankzhu.xiangqi.core

import kotlin.math.abs
import kotlin.math.sign

fun Position.legalMoves(): List<Move> =
    pseudoLegalMoves(sideToMove)
        .filter { move -> !applying(move, validate = false).isInCheck(sideToMove) }
        .sortedBy { it.uci }

fun Position.legalMoves(from: Square): List<Move> = legalMoves().filter { it.from == from }

fun Position.isInCheck(side: Side): Boolean {
    val generalSquare = pieces.entries
        .firstOrNull { it.value.side == side && it.value.kind == PieceKind.GENERAL }?.key
        ?: return true
    return pieces.any { (square, piece) ->
        piece.side == side.opponent && attacks(piece, square, generalSquare)
    }
}

/** Basic xiangqi result: mate, stalemate, or threefold repetition when supplied. */
fun Position.result(repetitionCount: Int = 0): GameResult? {
    if (repetitionCount >= 3) return GameResult(null, GameResultReason.REPETITION)
    if (legalMoves().isNotEmpty()) return null
    return GameResult(
        sideToMove.opponent,
        if (isInCheck(sideToMove)) GameResultReason.CHECKMATE else GameResultReason.STALEMATE
    )
}

private fun Position.pseudoLegalMoves(side: Side): List<Move> {
    val result = ArrayList<Move>()
    for ((square, piece) in pieces) {
        if (piece.side != side) continue
        for (rank in 0..9) {
            for (file in 0..8) {
                val target = Square(file, rank)
                if (pieces[target]?.side == side) continue
                if (attacks(piece, square, target)) result.add(Move(square, target))
            }
        }
    }
    return result
}

private fun Position.attacks(piece: Piece, from: Square, target: Square): Boolean {
    if (from == target || !target.isValid) return false
    val dx = target.file - from.file
    val dy = target.rank - from.rank
    val ax = abs(dx)
    val ay = abs(dy)

    return when (piece.kind) {
        PieceKind.GENERAL -> {
            val targetPiece = pieces[target]
            if (target.file == from.file &&
                targetPiece?.kind == PieceKind.GENERAL &&
                targetPiece.side == piece.side.opponent
            ) {
                countBetween(from, target) == 0
            } else {
                ax + ay == 1 && inPalace(target, piece.side)
            }
        }
        PieceKind.ADVISOR -> ax == 1 && ay == 1 && inPalace(target, piece.side)
        PieceKind.ELEPHANT -> {
            if (ax != 2 || ay != 2) return false
            if (piece.side == Side.RED && target.rank > 4) return false
            if (piece.side == Side.BLACK && target.rank < 5) return false
            pieces[Square(from.file + dx / 2, from.rank + dy / 2)] == null
        }
        PieceKind.HORSE -> {
            if (!((ax == 1 && ay == 2) || (ax == 2 && ay == 1))) return false
            val leg = if (ax == 2) {
                Square(from.file + dx.sign, from.rank)
            } else {
                Square(from.file, from.rank + dy.sign)
            }
            pieces[leg] == null
        }
        PieceKind.CHARIOT -> (dx == 0 || dy == 0) && countBetween(from, target) == 0
        PieceKind.CANNON -> {
            if (dx != 0 && dy != 0) return false
            val blockers = countBetween(from, target)
            if (pieces[target] == null) blockers == 0 else blockers == 1
        }
        PieceKind.SOLDIER -> {
            if (dx == 0 && dy == piece.side.forward) return true
            val crossedRiver = if (piece.side == Side.RED) from.rank >= 5 else from.rank <= 4
            crossedRiver && ay == 0 && ax == 1
        }
    }
}

private fun inPalace(square: Square, side: Side): Boolean {
    if (square.file !in 3..5) return false
    return if (side == Side.RED) square.rank in 0..2 else square.rank in 7..9
}

private fun Position.countBetween(from: Square, to: Square): Int {
    if (from.file != to.file && from.rank != to.rank) return Int.MAX_VALUE
    val dx = (to.file - from.file).sign
    val dy = (to.rank - from.rank).sign
    var current = Square(from.file + dx, from.rank + dy)
    var count = 0
    while (current != to) {
        if (pieces[current] != null) count++
        current = Square(current.file + dx, current.rank + dy)
    }
    return count
}
