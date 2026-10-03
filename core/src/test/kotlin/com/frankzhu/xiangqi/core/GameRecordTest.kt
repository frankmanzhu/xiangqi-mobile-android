package com.frankzhu.xiangqi.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Decodes a record in the exact shape the iOS app writes (Swift `Codable`, sorted keys, ISO-8601 dates). */
class GameRecordTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private val iosRecord = """
        {
          "blackSecondsRemaining" : 600,
          "computerLevel" : 3,
          "createdAt" : "2026-09-23T04:11:35Z",
          "elapsedSeconds" : 12,
          "humanSide" : "red",
          "id" : "6F1E6F5A-8F0B-4B63-9D9E-4D2F4E0E4A11",
          "mode" : "computer",
          "moves" : [
            {
              "captured" : "soldier",
              "committedAt" : "2026-09-23T04:11:40Z",
              "hintUsed" : true,
              "id" : "0B1E6F5A-8F0B-4B63-9D9E-4D2F4E0E4A12",
              "notation" : "Cannon h2–e2",
              "side" : "red",
              "uci" : "h2e2"
            },
            {
              "committedAt" : "2026-09-23T04:11:42Z",
              "engineSelectionSeed" : 18446744073709551615,
              "hintUsed" : false,
              "id" : "0B1E6F5A-8F0B-4B63-9D9E-4D2F4E0E4A13",
              "notation" : "Horse h9–g7",
              "side" : "black",
              "uci" : "h9g7"
            }
          ],
          "orientation" : "red",
          "redSecondsRemaining" : 600,
          "rulesPolicyID" : "pikafish-computer-rule@6a59ee2f7b105bff64d9efc2692591107787e2b1",
          "schemaVersion" : 1,
          "startingFEN" : "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1",
          "theme" : "someFutureTheme",
          "timeControl" : "tenMinutes",
          "updatedAt" : "2026-09-23T04:11:42Z"
        }
    """.trimIndent()

    @Test
    fun decodesIosRecordIncludingUnknownThemeAndMaxSeed() {
        val record = json.decodeFromString<GameRecord>(iosRecord)
        assertEquals(GameMode.COMPUTER, record.mode)
        assertEquals(Side.RED, record.humanSide)
        assertEquals(TimeControl.TEN_MINUTES, record.timeControl)
        assertEquals(ThemeID("someFutureTheme"), record.theme)
        assertEquals(listOf("h2e2", "h9g7"), record.uciMoves)
        assertEquals(PieceKind.SOLDIER, record.moves[0].captured)
        assertEquals(ULong.MAX_VALUE, record.moves[1].engineSelectionSeed)
        assertEquals(Instant.parse("2026-09-23T04:11:35Z"), record.createdAt)
        assertTrue(record.isActive)
        // The moves replay under our rules, so a record from iOS is playable here.
        Position.fromFen(record.startingFEN).replaying(record.uciMoves)
    }

    @Test
    fun roundTripsThroughJsonWithoutLosingFields() {
        val original = json.decodeFromString<GameRecord>(iosRecord)
        val again = json.decodeFromString<GameRecord>(json.encodeToString(original))
        assertEquals(original, again)
    }

    @Test
    fun encodesUuidUppercaseAndDatesWithoutFractionalSeconds() {
        val record = GameRecord.create(GameMode.LOCAL_TWO_PLAYER, null, createdAt = Instant.parse("2026-01-02T03:04:05.678Z"))
            .copy(id = UUID.fromString("6f1e6f5a-8f0b-4b63-9d9e-4d2f4e0e4a11"))
        val text = json.encodeToString(record)
        assertTrue("\"6F1E6F5A-8F0B-4B63-9D9E-4D2F4E0E4A11\"" in text)
        assertTrue("\"2026-01-02T03:04:05Z\"" in text)
        assertFalse("humanSide" in text, "nil optionals are omitted like Swift's encodeIfPresent")
    }

    @Test
    fun newRecordClampsLevelAndSeedsClocks() {
        val record = GameRecord.create(GameMode.COMPUTER, Side.BLACK, computerLevel = 99, timeControl = TimeControl.FIFTEEN_MINUTES)
        assertEquals(5, record.computerLevel)
        assertEquals(900, record.redSecondsRemaining)
        assertEquals(900, record.blackSecondsRemaining)
        assertEquals(record.createdAt, record.updatedAt)
    }

    @Test
    fun portableGameMatchesIosKeys() {
        val record = GameRecord.create(GameMode.COMPUTER, Side.RED)
        val text = PortableGame.from(record).encoded()
        for (key in listOf("format", "mode", "moves", "rulesPolicyID", "schemaVersion", "startingFEN")) {
            assertTrue("\"$key\"" in text, "missing $key")
        }
        assertTrue("\"computer\"" in text)
    }
}
