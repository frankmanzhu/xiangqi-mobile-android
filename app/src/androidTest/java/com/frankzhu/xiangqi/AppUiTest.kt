package com.frankzhu.xiangqi

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.core.ThemeID
import com.frankzhu.xiangqi.l10n.AppLanguage
import com.frankzhu.xiangqi.l10n.LocalizedKey
import com.frankzhu.xiangqi.l10n.Localizer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.File

/**
 * Shared scaffolding for end-to-end UI tests: every test starts from a known state
 * (English, Classic theme, no saved game) and drives the real app, engine and database.
 */
abstract class AppUiTest {
    @get:Rule val rule: ComposeTestRule = createEmptyComposeRule()

    protected val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    protected val container: AppContainer get() = (context.applicationContext as XiangqiApp).container
    protected val en = Localizer.English
    protected var scenario: ActivityScenario<MainActivity>? = null

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    protected val savedGameFile get() = File(context.filesDir, "XiangqiMobile/active-game.json")

    @Before
    fun resetAppState() {
        container.preferences.apply {
            updateLanguage(AppLanguage.ENGLISH)
            updateTheme(ThemeID.CLASSIC)
            updateConfirmMoves(false)
            updateSounds(false)
            updateHaptics(false)
            updatePieceGlyphs(com.frankzhu.xiangqi.data.PieceGlyphSet.TRADITIONAL)
            updateCoordinates(com.frankzhu.xiangqi.data.CoordinateDisplay.RED_PERSPECTIVE)
        }
        // Wipe the saved game *before* the activity loads it, or a replace-game dialog appears.
        savedGameFile.delete()
        launch()
    }

    @After
    fun closeActivity() {
        scenario?.close()
        savedGameFile.delete()
    }

    protected fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** Recreates the activity (rotation, process-style configuration change) keeping the ViewModel. */
    protected fun recreate() = scenario!!.recreate()

    protected fun t(key: LocalizedKey, vararg args: Any?): String = en(key, *args)

    /** Opens a screen directly through the app's navigation state, for tests that are not about navigation. */
    protected fun navigateTo(route: AppRoute) {
        scenario!!.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[AppModel::class.java].navigate(route)
        }
        rule.waitForIdle()
    }

    // region Finders

    protected fun text(value: String) = hasText(value)

    protected fun click(value: String) = rule.onNodeWithText(value).performClick()

    protected fun click(key: LocalizedKey, vararg args: Any?) = click(t(key, *args))

    /** Clicks the node whose text contains [value] (labels such as "Next ›" carry arrows). */
    protected fun clickContaining(value: String) =
        rule.onNode(hasText(value, substring = true)).performClick()

    /** Clicks the [index]th node with exactly this text, for labels that legitimately repeat. */
    protected fun clickNth(value: String, index: Int) {
        val node = rule.onAllNodes(hasText(value))[index]
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    protected fun waitForTextContaining(value: String, timeoutMs: Long = 20_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodes(hasText(value, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    protected fun waitForText(value: String, timeoutMs: Long = 20_000) =
        rule.waitUntil(timeoutMs) { rule.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty() }

    protected fun waitForText(key: LocalizedKey, vararg args: Any?, timeoutMs: Long = 20_000) =
        waitForText(t(key, *args), timeoutMs)

    protected fun assertText(value: String) {
        check(rule.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty()) { "Expected text on screen: $value" }
    }

    protected fun assertNoText(value: String) {
        check(rule.onAllNodes(hasText(value)).fetchSemanticsNodes().isEmpty()) { "Unexpected text on screen: $value" }
    }

    protected fun hasTextContaining(value: String) = hasText(value, substring = true)

    /** The board point for a UCI square such as `h2`, whatever piece (or none) is on it. */
    protected fun square(uci: String) = SemanticsMatcher("board square $uci") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.any { it.contains(", $uci,") || it.endsWith("Empty $uci") } == true
    }

    protected fun tap(uci: String) = rule.onNode(square(uci)).performClick()

    protected fun move(from: String, to: String) {
        tap(from)
        tap(to)
    }

    protected fun describe(uci: String): String =
        rule.onNode(square(uci)).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull().orEmpty()

    protected fun pieceAt(uci: String): String? =
        describe(uci).takeUnless { it.startsWith("Empty") }?.substringBefore(",")

    protected fun legalDestinationCount() =
        rule.onAllNodes(hasStateDescription(t(com.frankzhu.xiangqi.l10n.L10n.Board.legalDestination))).fetchSemanticsNodes().size

    // endregion

    // region Navigation

    protected fun startTwoPlayerGame(time: LocalizedKey? = null) {
        click(com.frankzhu.xiangqi.l10n.L10n.Mode.localTwoPlayer)
        if (time != null) click(time)
        click(com.frankzhu.xiangqi.l10n.L10n.NewGame.start)
        waitForText(com.frankzhu.xiangqi.l10n.L10n.Game.Status.sideToMove, t(com.frankzhu.xiangqi.l10n.L10n.Side.red))
    }

    protected fun startComputerGame(black: Boolean = false, level: Int = 1) {
        click(com.frankzhu.xiangqi.l10n.L10n.Mode.computer)
        if (black) click(com.frankzhu.xiangqi.l10n.L10n.Side.black)
        click("$level")
        click(com.frankzhu.xiangqi.l10n.L10n.NewGame.start)
    }

    protected fun readSavedGame(): GameRecord = json.decodeFromString(savedGameFile.readText())

    protected fun waitForSavedMoves(count: Int, timeoutMs: Long = 20_000) {
        var lastError: Throwable? = null
        try {
            rule.waitUntil(timeoutMs) {
                runCatching { readSavedGame().moves.size >= count }
                    .onFailure { lastError = it }
                    .getOrDefault(false)
            }
        } catch (timeout: Throwable) {
            val file = if (savedGameFile.exists()) savedGameFile.readText().take(1500) else "<no saved game file>"
            throw AssertionError("Saved game never reached $count moves. Last error: $lastError\n$file", timeout)
        }
    }

    // endregion
}
