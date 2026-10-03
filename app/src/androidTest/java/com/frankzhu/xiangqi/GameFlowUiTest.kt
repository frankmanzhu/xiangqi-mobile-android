package com.frankzhu.xiangqi

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import org.junit.After
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** End-to-end smoke test: home → new game → play a move → the real engine answers. */
@RunWith(AndroidJUnit4::class)
class GameFlowUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun resetSavedGame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Pin the settings the assertions depend on, whatever a previous run left behind.
        (context.applicationContext as XiangqiApp).container.preferences.apply {
            updateLanguage(com.frankzhu.xiangqi.l10n.AppLanguage.ENGLISH)
            updateTheme(com.frankzhu.xiangqi.core.ThemeID.CLASSIC)
            updateConfirmMoves(false)
        }
        // Wipe the saved game *before* the activity loads it, or a replace-game dialog appears.
        File(context.filesDir, "XiangqiMobile/active-game.json").delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeActivity() { scenario?.close() }

    @Test
    fun playAMoveAgainstThePikafishEngine() {
        rule.onNodeWithText("Play Computer").performClick()
        rule.onNodeWithText("Start game").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Your move")).fetchSemanticsNodes().isNotEmpty() }

        // Cannon h2 -> e2 (the central cannon opening).
        rule.onNodeWithContentDescription("Red Cannon, h2, selectable").performClick()
        rule.onNodeWithContentDescription("Empty e2").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasContentDescription("Red Cannon, e2, selectable")).fetchSemanticsNodes().isNotEmpty()
        }
        // The engine replies and hands the move back.
        rule.waitUntil(20_000) { rule.onAllNodes(hasText("Your move")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Moves").assertIsDisplayed()
    }

    @Test
    fun twoPlayerGameAcceptsMovesWithoutTheEngine() {
        rule.onNodeWithText("Two Players").performClick()
        rule.onNodeWithText("Start game").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Red to move")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Red Cannon, h2, selectable").performClick()
        rule.onNodeWithContentDescription("Empty e2").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Black to move")).fetchSemanticsNodes().isNotEmpty() }
    }
}
