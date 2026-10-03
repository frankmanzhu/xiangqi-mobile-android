package com.frankzhu.xiangqi.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
enum class Side {
    @SerialName("red") RED,
    @SerialName("black") BLACK;

    val opponent: Side get() = if (this == RED) BLACK else RED
    val forward: Int get() = if (this == RED) 1 else -1

    /**
     * Name used inside the portable game record. Deliberately not localized:
     * a saved or shared record must read the same in every language.
     */
    val recordName: String get() = if (this == RED) "Red" else "Black"
}

@Serializable
enum class PieceKind(val value: Int) {
    @SerialName("general") GENERAL(10_000),
    @SerialName("advisor") ADVISOR(200),
    @SerialName("elephant") ELEPHANT(200),
    @SerialName("horse") HORSE(400),
    @SerialName("chariot") CHARIOT(900),
    @SerialName("cannon") CANNON(450),
    @SerialName("soldier") SOLDIER(100);

    /** Name used inside the portable game record; never localized. */
    val recordName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class Square(val file: Int, val rank: Int) : Comparable<Square> {
    val isValid: Boolean get() = file in 0..8 && rank in 0..9

    val uci: String
        get() = if (isValid) "${'a' + file}$rank" else "??"

    override fun compareTo(other: Square): Int =
        if (rank == other.rank) file.compareTo(other.file) else rank.compareTo(other.rank)

    companion object {
        fun parse(value: String): Square? {
            if (value.length != 2) return null
            val first = value[0]
            val last = value[1]
            if (first !in 'a'..'i' || last !in '0'..'9') return null
            return Square(first - 'a', last - '0')
        }
    }
}

data class Move(val from: Square, val to: Square) {
    val uci: String get() = from.uci + to.uci

    companion object {
        fun fromUci(uci: String): Move? {
            if (uci.length != 4) return null
            val from = Square.parse(uci.substring(0, 2)) ?: return null
            val to = Square.parse(uci.substring(2, 4)) ?: return null
            return Move(from, to)
        }
    }
}

data class Piece(val side: Side, val kind: PieceKind)

@Serializable
enum class GameMode {
    @SerialName("computer") COMPUTER,
    @SerialName("localTwoPlayer") LOCAL_TWO_PLAYER
}

@Serializable
enum class TimeControl(val seconds: Int?) {
    @SerialName("casual") CASUAL(null),
    @SerialName("tenMinutes") TEN_MINUTES(600),
    @SerialName("fifteenMinutes") FIFTEEN_MINUTES(900)
}

/**
 * Identifies a theme without enumerating which themes exist, so a saved record
 * naming an unknown theme still decodes (the registry substitutes a fallback).
 */
@Serializable(with = ThemeIDSerializer::class)
@JvmInline
value class ThemeID(val rawValue: String) {
    companion object {
        const val STORAGE_KEY = "theme"
        val CLASSIC = ThemeID("classic")
        val TOURNAMENT = ThemeID("tournament")
        val CALM = ThemeID("calm")
    }
}

object ThemeIDSerializer : KSerializer<ThemeID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ThemeID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ThemeID) = encoder.encodeString(value.rawValue)
    override fun deserialize(decoder: Decoder): ThemeID = ThemeID(decoder.decodeString())
}

@Serializable
enum class GameResultReason {
    @SerialName("checkmate") CHECKMATE,
    @SerialName("stalemate") STALEMATE,
    @SerialName("resignation") RESIGNATION,
    @SerialName("timeLoss") TIME_LOSS,
    @SerialName("repetition") REPETITION,
    @SerialName("rulesAdjudication") RULES_ADJUDICATION
}

@Serializable
data class GameResult(val winner: Side? = null, val reason: GameResultReason)

/** Swift's `.iso8601` date strategy: whole seconds, `Z` suffix. Decoding is lenient. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/** Swift encodes `UUID` as an uppercase string. */
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) =
        encoder.encodeString(value.toString().uppercase())

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Serializable
data class RecordedMove(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val uci: String,
    val notation: String,
    val side: Side,
    val captured: PieceKind? = null,
    val hintUsed: Boolean,
    val engineSelectionSeed: ULong? = null,
    @Serializable(with = InstantSerializer::class) val committedAt: Instant = Instant.now()
)

@Serializable
data class GameRecord(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val schemaVersion: Int = SCHEMA_VERSION,
    val rulesPolicyID: String = RULES_POLICY_ID,
    val startingFEN: String = STANDARD_FEN,
    val mode: GameMode,
    val humanSide: Side? = null,
    val computerLevel: Int = 2,
    val timeControl: TimeControl = TimeControl.CASUAL,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    val moves: List<RecordedMove> = emptyList(),
    val result: GameResult? = null,
    val redSecondsRemaining: Int? = timeControl.seconds,
    val blackSecondsRemaining: Int? = timeControl.seconds,
    val elapsedSeconds: Int = 0,
    val orientation: Side = Side.RED,
    val theme: ThemeID = ThemeID.CLASSIC,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = createdAt
) {
    val isActive: Boolean get() = result == null
    val uciMoves: List<String> get() = moves.map { it.uci }

    companion object {
        const val SCHEMA_VERSION = 1
        const val STANDARD_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
        const val LEGACY_RULES_POLICY_ID = "xiangqi-standard-legal@1"
        const val RULES_POLICY_ID = "pikafish-computer-rule@6a59ee2f7b105bff64d9efc2692591107787e2b1"

        /** Creates a fresh record, clamping the level like the iOS initializer does. */
        fun create(
            mode: GameMode,
            humanSide: Side?,
            computerLevel: Int = 2,
            timeControl: TimeControl = TimeControl.CASUAL,
            orientation: Side = Side.RED,
            theme: ThemeID = ThemeID.CLASSIC,
            createdAt: Instant = Instant.now()
        ): GameRecord = GameRecord(
            mode = mode,
            humanSide = humanSide,
            computerLevel = computerLevel.coerceIn(1, 5),
            timeControl = timeControl,
            createdAt = createdAt,
            orientation = orientation,
            theme = theme
        )
    }
}
