package com.frankzhu.xiangqi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frankzhu.xiangqi.core.ThemeID
import com.frankzhu.xiangqi.data.CoordinateDisplay
import com.frankzhu.xiangqi.data.PieceGlyphSet
import com.frankzhu.xiangqi.l10n.AppLanguage
import com.frankzhu.xiangqi.l10n.L10n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAndSettingsUiTest : AppUiTest() {

    @Test
    fun homeShowsBrandModesAndAllThemes() {
        for (key in listOf(L10n.Home.title, L10n.Home.Hero.title, L10n.Mode.computer, L10n.Mode.localTwoPlayer, L10n.Home.Mode.Learn.title)) {
            rule.onNodeWithText(t(key)).assertIsDisplayed()
        }
        for (theme in listOf(L10n.Theme.classic, L10n.Theme.tournament, L10n.Theme.calm)) {
            rule.onNodeWithText(t(theme)).assertIsDisplayed()
        }
        assertNoText(t(L10n.Home.Continue.title)) // nothing to resume on a clean install
    }

    @Test
    fun choosingAThemeOnHomeAppliesAndPersists() {
        click(L10n.Theme.tournament)
        rule.waitUntil(5_000) { container.preferences.themeId == ThemeID.TOURNAMENT }
        launch()
        assertEquals(ThemeID.TOURNAMENT, container.preferences.themeId)
        rule.onNodeWithText(t(L10n.Theme.tournament)).assertIsDisplayed()
    }

    @Test
    fun settingsAreReachableAndBackReturnsHome() {
        openSettings()
        rule.onNodeWithText(t(L10n.Settings.title)).assertIsDisplayed()
        rule.onNodeWithText(t(L10n.Settings.appLanguage)).assertIsDisplayed()
        pressBack()
        rule.onNodeWithText(t(L10n.Mode.computer)).assertIsDisplayed()
    }

    @Test
    fun switchingLanguageRelocalizesTheWholeAppImmediately() {
        openSettings()
        clickNth(t(L10n.Settings.Language.traditionalChinese), 0) // the language group comes first
        // The settings screen itself switches without restarting the activity.
        waitForText("設定")
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, container.preferences.language)
        pressBack()
        waitForText("人機對弈")
        rule.onNodeWithText("人機對弈").assertIsDisplayed()

        openSettings(label = "設定")
        clickNth("簡體中文", 0)
        waitForText("设置")
        pressBack()
        waitForText("人机对弈")

        openSettings(label = "设置")
        click("English")
        waitForText(t(L10n.Settings.title))
    }

    @Test
    fun languageChoiceSurvivesRestart() {
        container.preferences.updateLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        launch()
        waitForText("人机对弈")
    }

    @Test
    fun appearanceAndInteractionSettingsPersistAcrossRelaunch() {
        openSettings()
        clickNth(t(L10n.Settings.PieceLabelsOption.simplified), 1) // second group: piece labels
        click(L10n.Common.off)
        rule.onNodeWithText(t(L10n.Settings.confirmMoves)).performScrollTo().performClick()
        rule.onNodeWithText(t(L10n.Settings.sounds)).performScrollTo().performClick()
        rule.onNodeWithText(t(L10n.Settings.haptics)).performScrollTo().performClick()
        rule.waitUntil(5_000) { container.preferences.confirmMoves }

        launch()
        with(container.preferences) {
            assertEquals(PieceGlyphSet.SIMPLIFIED, pieceGlyphs)
            assertEquals(CoordinateDisplay.OFF, coordinates)
            assertTrue(confirmMoves)
            assertTrue(sounds)
            assertTrue(haptics)
        }
    }

    @Test
    fun rulesHelpExplainsEveryPiece() {
        openSettings()
        rule.onNodeWithText(t(L10n.Settings.howToPlay)).performScrollTo().performClick()
        waitForText(L10n.Rules.title)
        for (piece in listOf(L10n.Piece.chariot, L10n.Piece.horse, L10n.Piece.cannon, L10n.Piece.elephant, L10n.Piece.advisor, L10n.Piece.general, L10n.Piece.soldier)) {
            rule.onNodeWithText(t(piece)).performScrollTo().assertIsDisplayed()
        }
        pressBack()
        rule.onNodeWithText(t(L10n.Settings.title)).assertIsDisplayed()
    }

    @Test
    fun licencesScreenShowsEngineDataAndLibraryNotices() {
        openSettings()
        rule.onNodeWithText(t(L10n.Settings.licenses)).performScrollTo().performClick()
        waitForText(L10n.Licenses.title)
        // GPL text is bundled and readable offline.
        rule.onNodeWithText("▸ " + t(L10n.Licenses.gpl)).performScrollTo().performClick()
        waitForTextContaining("GNU GENERAL PUBLIC LICENSE", timeoutMs = 10_000)
        rule.onNodeWithText(t(L10n.Licenses.Ccpd.name)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(t(L10n.Licenses.Libraries.note)).performScrollTo().assertIsDisplayed()
        pressBack()
        rule.onNodeWithText(t(L10n.Settings.title)).assertIsDisplayed()
    }

    @Test
    fun privacyPolicyIsBundledAndReadableOffline() {
        openSettings()
        rule.onNodeWithText(t(L10n.Privacy.title)).performScrollTo().performClick()
        waitForText(L10n.Privacy.summary)
        rule.onNodeWithText(t(L10n.Privacy.Section.storage).uppercase()).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(t(L10n.Privacy.Section.retention).uppercase()).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(t(L10n.Privacy.Section.contact).uppercase()).performScrollTo().assertIsDisplayed()
    }

    private fun openSettings(label: String = t(L10n.Common.settings)) {
        rule.onNode(androidx.compose.ui.test.hasContentDescription(label)).performClick()
        waitForText(if (label == t(L10n.Common.settings)) t(L10n.Settings.title) else label)
    }

    private fun pressBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }
}
