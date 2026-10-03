package com.frankzhu.xiangqi

import android.graphics.Bitmap
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frankzhu.xiangqi.core.ThemeID
import com.frankzhu.xiangqi.l10n.AppLanguage
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.ui.screens.CCPDCategory
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Developer tool, not a check: captures the Play Store screenshots from the real app. Run on an
 * emulator already in demo mode with
 * `adb shell am instrument -w -e storeScreenshots true -e class ...StoreScreenshotsTest ...`
 * then `adb pull /sdcard/Android/data/com.frankzhu.xiangqimobile/files/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotsTest : AppUiTest() {

    private val outDir: File by lazy {
        File(context.getExternalFilesDir(null), "screenshots").apply { deleteRecursively(); mkdirs() }
    }

    private fun shoot(name: String) {
        rule.waitForIdle()
        Thread.sleep(900) // let animations settle
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun captureStoreScreenshots() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeScreenshots") == "true")
        val library = container.learningLibrary.load()

        // 1. Home
        waitForText(L10n.Home.title)
        shoot("01-home")

        // 2. A live game against Pikafish, mid-opening, classic theme
        startComputerGame(level = 2)
        waitForText(L10n.Game.Status.yourMove)
        move("h2", "e2")
        waitForSavedMoves(2, timeoutMs = 40_000)
        waitForText(L10n.Game.Status.yourMove)
        move("b0", "c2")
        waitForSavedMoves(4, timeoutMs = 40_000)
        waitForText(L10n.Game.Status.yourMove)
        shoot("02-game")

        // 3. The move list over the live game
        click(L10n.Game.movesAction)
        waitForText(L10n.History.livePosition)
        shoot("03-moves")
        click(L10n.Common.done)

        // 4. A new game in the Tournament (dark) theme; a game keeps the theme it was started with
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } // leave game
        waitForText(L10n.Mode.computer)
        container.preferences.updateTheme(ThemeID.TOURNAMENT)
        rule.waitForIdle()
        startComputerGame(level = 2)
        click(L10n.NewGame.Replace.confirm)
        waitForText(L10n.Game.Status.yourMove)
        move("h2", "e2")
        waitForSavedMoves(2, timeoutMs = 40_000)
        waitForText(L10n.Game.Status.yourMove)
        shoot("04-game-dark")

        // 5. Learning library
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } // leave game
        container.preferences.updateTheme(ThemeID.CLASSIC)
        waitForText(L10n.Mode.computer)
        click(L10n.Home.Mode.Learn.title)
        waitForText(L10n.Learn.Dataset.title, timeoutMs = 60_000)
        shoot("05-learn")

        // 6. Studying a master game
        val record = library.record(library.records(category = "開局", limit = 1).first().id)!!
        navigateTo(AppRoute.StudyRecord(record.summary.id))
        waitForText(L10n.Study.title)
        repeat(8) { clickContaining(t(L10n.Common.next)) }
        shoot("06-study")

        // 7. A mating puzzle
        val puzzle = library.records(category = CCPDCategory.MATING_PRACTICE_ID, limit = 200)
            .first { it.moveCount in 5..9 }
        navigateTo(AppRoute.PracticeRecord(puzzle.id))
        waitForText(L10n.Practice.findRecordedMove, timeoutMs = 30_000)
        shoot("07-practice")

        // 8. Localised home (Traditional Chinese, Calm theme)
        container.preferences.updateLanguage(AppLanguage.TRADITIONAL_CHINESE)
        container.preferences.updateTheme(ThemeID.CALM)
        launch()
        waitForText("人機對弈")
        shoot("08-home-zh-hant")
    }
}
