package com.frankzhu.xiangqi.core

class ComputerConfiguration(level: Int, val seed: ULong) {
    /** Always within 1..5, whatever was requested. */
    val level: Int = level.coerceIn(1, 5)
}

interface ComputerPlayerClient {
    val policyID: String

    suspend fun chooseMove(startingFEN: String, moves: List<String>, configuration: ComputerConfiguration): Move

    suspend fun stop() {}
}

/** Adjudicates a game from its full history (Pikafish computer rules). */
interface RulesAdjudicator {
    suspend fun result(startingFEN: String, moves: List<String>): GameResult?
}
