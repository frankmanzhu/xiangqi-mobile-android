package com.frankzhu.xiangqi

import com.frankzhu.xiangqi.data.TableChineseVariants
import com.frankzhu.xiangqi.data.CoordinateDisplay
import com.frankzhu.xiangqi.data.PieceGlyphSet
import com.frankzhu.xiangqi.engine.PikafishEngine
import com.frankzhu.xiangqi.core.Piece
import com.frankzhu.xiangqi.core.PieceKind
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.l10n.AppLanguage
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.Localizer
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppUnitTest {
    private val tsv = File("src/main/assets/learning/chinese-variants.tsv")

    @Test
    fun tableVariantsFoldBothScripts() {
        val variants = TableChineseVariants { tsv.readLines() }
        assertEquals(listOf("刘忆慈", "劉憶慈"), variants.variants("刘忆慈"))
        assertEquals(listOf("劉憶慈", "刘忆慈"), variants.variants("劉憶慈"))
        assertEquals(listOf("abc 123"), variants.variants("abc 123"))
    }

    @Test
    fun systemLanguageResolvesFromTheDeviceLocale() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.SYSTEM.resolved(Locale.US))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.SYSTEM.resolved(Locale.SIMPLIFIED_CHINESE))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.SYSTEM.resolved(Locale.TRADITIONAL_CHINESE))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.SYSTEM.resolved(Locale.forLanguageTag("zh-HK")))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, AppLanguage.SYSTEM.resolved(Locale.forLanguageTag("zh-Hant-CN")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.ENGLISH.resolved(Locale.TRADITIONAL_CHINESE))
    }

    @Test
    fun storedPreferencesFallBackToDefaults() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored("nonsense"))
        assertEquals(PieceGlyphSet.SIMPLIFIED, PieceGlyphSet.fromStored("Simplified"))
        assertEquals(PieceGlyphSet.TRADITIONAL, PieceGlyphSet.fromStored(null))
        assertEquals(CoordinateDisplay.OFF, CoordinateDisplay.fromStored("Off"))
        assertEquals(CoordinateDisplay.RED_PERSPECTIVE, CoordinateDisplay.fromStored("Red perspective"))
    }

    @Test
    fun glyphsDifferByScriptAndSide() {
        assertEquals("帥", PieceGlyphSet.TRADITIONAL.glyph(Piece(Side.RED, PieceKind.GENERAL)))
        assertEquals("帅", PieceGlyphSet.SIMPLIFIED.glyph(Piece(Side.RED, PieceKind.GENERAL)))
        assertEquals("卒", PieceGlyphSet.SIMPLIFIED.glyph(Piece(Side.BLACK, PieceKind.SOLDIER)))
    }

    @Test
    fun localizerFormatsAndFallsBackToEnglish() {
        val english = Localizer.English
        assertEquals("Reviewing move 3 of 10", english(L10n.Game.Status.reviewing, 3, 10))
        assertEquals("Hint: x", english(L10n.Practice.hintFormat, "x"))
        val custom = Localizer(AppLanguage.ENGLISH, mapOf(L10n.Common.cancel.key to "Abbrechen"))
        assertEquals("Abbrechen", custom(L10n.Common.cancel))
        assertEquals("Confirm", custom(L10n.Common.confirm))
    }

    @Test
    fun everyCatalogCoversEveryKeyWithMatchingPlaceholders() {
        val json = kotlinx.serialization.json.Json
        fun catalog(code: String) = json.decodeFromString<Map<String, String>>(File("src/main/assets/l10n/$code.json").readText())
        val en = catalog("en")
        val placeholder = Regex("%(\\d+\\$)?[sd]")
        for (code in listOf("zh-Hans", "zh-Hant")) {
            val other = catalog(code)
            assertEquals(en.keys, other.keys, "$code key set")
            for ((key, value) in en) {
                assertEquals(placeholder.findAll(value).count(), placeholder.findAll(other.getValue(key)).count(), "$code $key")
            }
        }
        assertTrue(en.size > 250)
    }

    @Test
    fun thinkingBudgetGrowsWithLevel() {
        val budgets = (1..5).map(PikafishEngine::budget)
        assertEquals(budgets.sorted(), budgets)
        assertEquals(listOf(150, 350, 750, 1500, 3000), budgets)
    }

    @Test
    fun hotDevicesGetShorterSearches() {
        for (level in 1..5) {
            assertEquals(PikafishEngine.budget(level), PikafishEngine.scaledBudget(level, 0))
            assertEquals(PikafishEngine.budget(level), PikafishEngine.scaledBudget(level, 2))
            assertEquals(PikafishEngine.budget(level) / 2, PikafishEngine.scaledBudget(level, 3))
            assertTrue(PikafishEngine.scaledBudget(level, 5) <= maxOf(50, PikafishEngine.budget(level) / 4))
            assertTrue(PikafishEngine.scaledBudget(level, 5) >= 50)
        }
    }
}
