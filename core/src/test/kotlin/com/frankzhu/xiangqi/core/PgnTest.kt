package com.frankzhu.xiangqi.core

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PgnTest {
    private val standardFEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

    @Test
    fun decodesCcpdBig5AndNormalizesOpeningMoves() {
        val encoded = "W0dhbWUgIkNoaW5lc2UgQ2hlc3MiXQpbRXZlbnQgIqSkrLa576vMrbewqCJdCltSZXN1bHQgIioiXQpbRkVOICJybmJha2FibnIvOS8xYzVjMS9wMXAxcDFwMXAvOS85L1AxUDFQMVAxUC8xQzVDMS85L1JOQkFLQUJOUiB3IC0gLSAwIDEiXQoKMS4grLakR6WtpK0gsKiit7ZporYKKg=="
        val decoded = XiangqiPGNTextDecoder.decode(Base64.getDecoder().decode(encoded))
        assertEquals(XiangqiPGNTextEncoding.BIG5, decoded.encoding)
        assertTrue("中炮對屏風馬" in decoded.text)

        val game = XiangqiPGNParser.parse(decoded.text)
        assertEquals("中炮對屏風馬", game.tags["Event"])
        assertEquals(listOf("炮二平五", "馬８進７"), game.sourceMoves)

        val normalized = XiangqiPGNNormalizer.normalize(game)
        assertEquals(listOf("h2e2", "h9g7"), normalized.moves.map { it.uci })
    }

    @Test
    fun fallsBackToBig5HkscsWithoutLossyReplacement() {
        // 0x8740 is an HKSCS extension pair and is invalid in strict Big5.
        val decoded = XiangqiPGNTextDecoder.decode(byteArrayOf(0x87.toByte(), 0x40))
        assertEquals(XiangqiPGNTextEncoding.BIG5_HKSCS, decoded.encoding)
        assertFalse("�" in decoded.text)
    }

    @Test
    fun utf8WithBomIsAcceptedAndStripped() {
        val decoded = XiangqiPGNTextDecoder.decode("﻿[Game \"x\"]".toByteArray())
        assertEquals(XiangqiPGNTextEncoding.UTF8, decoded.encoding)
        assertEquals("[Game \"x\"]", decoded.text)
    }

    @Test
    fun parserKeepsCommentsAndIgnoresVariations() {
        val text = """
            [Game "Chinese Chess"]
            [Result "1-0"]
            [FEN "$standardFEN"]

            1. 炮二平五 {central cannon} (1. 馬二進三 馬８進７) 馬８進７
            2. 馬二進三 ; development
            車９平８ 1-0
        """.trimIndent()

        val game = XiangqiPGNParser.parse(text)
        assertEquals(listOf("炮二平五", "馬８進７", "馬二進三", "車９平８"), game.sourceMoves)
        assertEquals(listOf("central cannon", "development"), game.comments)
        assertEquals("1-0", game.result)

        val normalized = XiangqiPGNNormalizer.normalize(game)
        assertEquals(listOf("h2e2", "h9g7", "h0g2", "i9h9"), normalized.moves.map { it.uci })
    }

    @Test
    fun chineseNotationSupportsSimplifiedCharactersAndRelativePieces() {
        val opening = ChineseMoveNotationParser.parse("炮二平五", Position.standard)
        assertEquals("h2e2", opening.uci)

        val afterRed = Position.standard.applying(opening)
        assertEquals("h9g7", ChineseMoveNotationParser.parse("马８进７", afterRed).uci)

        val relative = Position.fromFen("4k4/9/9/9/4R4/9/4R4/9/9/4K4 w - - 0 1")
        assertEquals("e5e6", ChineseMoveNotationParser.parse("前車進一", relative).uci)
        assertEquals("e3e2", ChineseMoveNotationParser.parse("後車退一", relative).uci)
    }

    @Test
    fun normalizerReportsPlyAndSourceNotationOnFailure() {
        val game = XiangqiPGNGame(mapOf("FEN" to standardFEN), listOf("炮二平五", "不是棋步"), emptyList(), "*", "")
        val error = assertFailsWith<XiangqiPGNException.IllegalMove> { XiangqiPGNNormalizer.normalize(game) }
        assertEquals(2, error.ply)
        assertEquals("不是棋步", error.notation)
    }

    @Test
    fun missingFenIsReported() {
        val game = XiangqiPGNGame(emptyMap(), emptyList(), emptyList(), null, "")
        assertFailsWith<XiangqiPGNException.MissingFEN> { XiangqiPGNNormalizer.normalize(game) }
    }

    @Test
    fun normalizesPinnedCcpdMiddlegameFixtureFromArbitraryFen() {
        // CCPD commit 368a47a, Dataset/中局/00000001.pgn after lossless Big5 decoding.
        val text = """
            [Game "Chinese Chess"]
            [Event "北方杯 (1)三軍逼宮"]
            [Date "1982"]
            [Red "徐天利"]
            [Black "呂欽"]
            [Result "0-1"]
            [FEN "4kab2/4a4/2R1b1P2/9/p3p4/5p3/P3P1c2/N2Cr4/4A4/3AK4 b - - 0 1"]

            1. 車５平８
            2. 馬九進七 卒６進１
            3. 馬七進六 車８進２
            4. 仕五退四 炮７進３
            5. 帥五進一 車８退１
            6. 帥五進一 車８退３
            7. 車七退四 卒５進１
            8. 炮六退一 卒５進１
            9. 馬六退五 車８平５
            10. 帥五退一 卒６平５
            11. 車七退一 炮７退５
            12. 帥五退一 卒５進１
            13. 仕四進五 炮７平５
            14. 炮六進五 炮５退１
            15. 帥五平四 車５平６
            16. 帥四平五 車６進２

            0-1
        """.trimIndent()

        val normalized = XiangqiPGNNormalizer.normalize(XiangqiPGNParser.parse(text))
        assertEquals(31, normalized.moves.size)
        assertEquals("e2h2", normalized.moves.first().uci)
        assertEquals("0-1", normalized.result)
        Position.fromFen(normalized.startingFEN).replaying(normalized.moves.map { it.uci })
    }
}
