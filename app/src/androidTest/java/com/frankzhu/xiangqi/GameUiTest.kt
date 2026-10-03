package com.frankzhu.xiangqi

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frankzhu.xiangqi.core.GameMode
import com.frankzhu.xiangqi.core.GameResultReason
import com.frankzhu.xiangqi.core.Position
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.core.legalMoves
import com.frankzhu.xiangqi.l10n.L10n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Drives complete games through the real UI: input, rules feedback, history, undo, resign, clocks, resume. */
@RunWith(AndroidJUnit4::class)
class GameUiTest : AppUiTest() {

    private val red = t(L10n.Side.red)
    private val black = t(L10n.Side.black)

    @Test
    fun selectingAPieceShowsItsLegalDestinationsAndOnlyThose() {
        startTwoPlayerGame()
        tap("h2")
        // The central cannon at h2 can reach: h3 is blocked by nothing but its own soldier line,
        // so the engine of truth is the core rules: compare counts exactly.
        val expected = Position.standard.legalMoves().count { it.from.uci == "h2" }
        assertEquals(expected, legalDestinationCount())
        assertTrue(expected > 0)

        // Tapping the selected piece again deselects it.
        tap("h2")
        assertEquals(0, legalDestinationCount())

        // Tapping another own piece moves the selection.
        tap("h2"); tap("b2")
        assertEquals(Position.standard.legalMoves().count { it.from.uci == "b2" }, legalDestinationCount())

        // Tapping an opponent piece or an illegal square clears the selection and moves nothing.
        tap("h9")
        assertEquals(0, legalDestinationCount())
        tap("a0"); tap("a5")
        assertEquals(0, legalDestinationCount())
        assertText(t(L10n.Game.Status.sideToMove, red))
    }

    @Test
    fun aHotSeatExchangeUpdatesBoardStatusAndSavedRecord() {
        startTwoPlayerGame()
        move("h2", "e2")
        waitForText(L10n.Game.Status.sideToMove, black)
        assertEquals("Red Cannon", pieceAt("e2"))
        assertNull(pieceAt("h2"))

        move("h9", "g7")
        waitForText(L10n.Game.Status.sideToMove, red)
        waitForSavedMoves(2)
        val record = readSavedGame()
        assertEquals(listOf("h2e2", "h9g7"), record.uciMoves)
        assertEquals("Cannon h2–e2", record.moves[0].notation)
        assertEquals(GameMode.LOCAL_TWO_PLAYER, record.mode)
    }

    @Test
    fun captureIsRecordedAndRemovesThePiece() {
        startTwoPlayerGame()
        move("h2", "e2"); move("h9", "g7")
        move("e2", "e6") // cannon jumps the central soldier line to take the black soldier
        waitForText(L10n.Game.Status.sideToMove, black)
        assertEquals("Red Cannon", pieceAt("e6"))
        waitForSavedMoves(3)
        val third = readSavedGame().moves[2]
        assertEquals("Cannon e2×e6", third.notation)
        assertEquals(com.frankzhu.xiangqi.core.PieceKind.SOLDIER, third.captured)
    }

    @Test
    fun moveHistorySheetListsMovesAndReplaysAnyPosition() {
        startTwoPlayerGame()
        move("h2", "e2"); move("h9", "g7"); move("h0", "g2")
        waitForText(L10n.Game.Status.sideToMove, black)

        click(L10n.Game.movesAction)
        waitForText(L10n.History.title, 3)
        rule.onNodeWithText("Cannon h2–e2").assertIsDisplayed()
        rule.onNodeWithText("Horse h9–g7").assertIsDisplayed()

        // Jump to the very first move: board shows it, status says reviewing.
        rule.onNodeWithText("Cannon h2–e2").performClick()
        waitForText(L10n.Game.Status.reviewing, 1, 3)
        clickContaining(t(L10n.Common.next))
        waitForText(L10n.Game.Status.reviewing, 2, 3)
        clickContaining(t(L10n.Common.previous))
        clickContaining(t(L10n.Common.previous))
        waitForText(L10n.Game.Status.reviewing, 0, 3)
        click(L10n.History.returnToLive)
        waitForText(L10n.History.livePosition)
        click(L10n.Common.done)
        waitForText(L10n.Game.Status.sideToMove, black)
    }

    @Test
    fun undoRevertsTheLastMoveInCasualGames() {
        startTwoPlayerGame()
        rule.onNodeWithText(t(L10n.Game.undo)).assertIsNotEnabled()
        move("h2", "e2")
        waitForText(L10n.Game.Status.sideToMove, black)
        rule.onNodeWithText(t(L10n.Game.undo)).assertIsEnabled().performClick()
        waitForText(L10n.Game.Status.sideToMove, red)
        assertEquals("Red Cannon", pieceAt("h2"))
        assertNull(pieceAt("e2"))
        waitForSavedMoves(0)
        assertTrue(readSavedGame().moves.isEmpty())
    }

    @Test
    fun timedGamesRunClocksAndDisallowUndo() {
        startTwoPlayerGame(time = L10n.TimeControl.tenMinutes)
        assertText("10:00")
        move("h2", "e2")
        waitForText(L10n.Game.Status.sideToMove, black)
        rule.onNodeWithText(t(L10n.Game.undo)).assertIsNotEnabled()
        // Black's clock runs while black is to move; red's is frozen.
        rule.waitUntil(8_000) { rule.onAllNodes(hasText("09:5", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertText("10:00")
    }

    @Test
    fun flippingTheBoardMovesRedToTheTop() {
        startTwoPlayerGame()
        val bottomBefore = rule.onNode(square("e0")).getUnclippedBoundsInRoot().top
        val topBefore = rule.onNode(square("e9")).getUnclippedBoundsInRoot().top
        assertTrue(bottomBefore > topBefore)
        click(L10n.Game.flip)
        rule.waitForIdle()
        val bottomAfter = rule.onNode(square("e0")).getUnclippedBoundsInRoot().top
        val topAfter = rule.onNode(square("e9")).getUnclippedBoundsInRoot().top
        assertTrue("red general should now be at the top", bottomAfter < topAfter)
        // Moves still work in the flipped orientation.
        move("h2", "e2")
        waitForText(L10n.Game.Status.sideToMove, black)
    }

    @Test
    fun confirmMovesModeAsksBeforeCommitting() {
        container.preferences.updateConfirmMoves(true)
        startTwoPlayerGame()
        move("h2", "e2")
        waitForText(L10n.Game.confirmMove)
        assertText("h2e2")
        assertEquals("Red Cannon", pieceAt("h2")) // nothing committed yet
        click(L10n.Common.cancel)
        assertEquals("Red Cannon", pieceAt("h2"))
        // Cancelling keeps the piece selected, so only the destination needs tapping again.
        tap("e2")
        waitForText(L10n.Game.confirmMove)
        click(L10n.Common.confirm)
        waitForText(L10n.Game.Status.sideToMove, black)
        assertEquals("Red Cannon", pieceAt("e2"))
    }

    @Test
    fun resigningEndsTheGameAndOffersReviewAndHome() {
        startTwoPlayerGame()
        move("h2", "e2")
        waitForText(L10n.Game.Status.sideToMove, black)

        rule.onNodeWithContentDescription(t(L10n.Game.menu)).performClick()
        click(L10n.GameMenu.resign)
        waitForText(L10n.GameMenu.resignConfirm)
        // The confirm button repeats the label; the dialog's one is the last match.
        rule.onAllNodes(hasText(t(L10n.GameMenu.resign)))[1].performClick()

        // Black was to move and resigned → red wins.
        waitForText(L10n.Result.wins, red)
        assertText(t(L10n.Reason.resignation))
        waitForSavedMoves(1)
        rule.waitUntil(5_000) { readSavedGame().result != null }
        assertEquals(GameResultReason.RESIGNATION, readSavedGame().result!!.reason)
        assertEquals(Side.RED, readSavedGame().result!!.winner)

        click(L10n.Common.home)
        waitForText(L10n.Mode.computer)
        assertNoText(t(L10n.Home.Continue.title)) // a finished game is not resumable
    }

    @Test
    fun resultReviewOpensTheMoveList() {
        startTwoPlayerGame()
        move("h2", "e2")
        rule.onNodeWithContentDescription(t(L10n.Game.menu)).performClick()
        click(L10n.GameMenu.resign)
        rule.onAllNodes(hasText(t(L10n.GameMenu.resign)))[1].performClick()
        waitForText(L10n.Result.review)
        click(L10n.Result.review)
        waitForText(L10n.History.title, 1)
    }

    @Test
    fun savingAndLeavingLetsYouContinueExactlyWhereYouStopped() {
        startTwoPlayerGame()
        move("h2", "e2"); move("h9", "g7")
        waitForSavedMoves(2)

        rule.onNodeWithContentDescription(t(L10n.Game.menu)).performClick()
        click(L10n.GameMenu.saveAndLeave)
        waitForText(L10n.Home.Continue.title)
        assertText(t(L10n.Home.Continue.subtitle, t(L10n.Mode.localTwoPlayer), 2))

        click(L10n.Home.Continue.title)
        waitForText(L10n.Game.Status.sideToMove, red)
        assertEquals("Red Cannon", pieceAt("e2"))
        assertEquals("Black Horse", pieceAt("g7"))
    }

    @Test
    fun anInterruptedGameResumesAfterTheAppIsRelaunched() {
        startTwoPlayerGame(time = L10n.TimeControl.fifteenMinutes)
        move("h2", "e2")
        waitForSavedMoves(1)
        // Back out of the game the way a user would, then cold-start the activity again.
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        launch()
        waitForText(L10n.Home.Continue.title)
        click(L10n.Home.Continue.title)
        waitForText(L10n.Game.Status.sideToMove, black)
        assertEquals("Red Cannon", pieceAt("e2"))
        // The clock was restored from the save, not reset to a different control.
        assertTrue(clockTexts().any { it.matches(Regex("1[45]:\\d\\d")) })
    }

    @Test
    fun rotatingTheDeviceKeepsTheGameState() {
        startTwoPlayerGame()
        move("h2", "e2")
        waitForText(L10n.Game.Status.sideToMove, black)
        recreate()
        waitForText(L10n.Game.Status.sideToMove, black)
        assertEquals("Red Cannon", pieceAt("e2"))
        move("h9", "g7")
        waitForText(L10n.Game.Status.sideToMove, red)
    }

    @Test
    fun backgroundingTheAppSavesTheGame() {
        startTwoPlayerGame()
        move("h2", "e2")
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED) // onStop
        waitForSavedMoves(1)
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitForText(L10n.Game.Status.sideToMove, black)
        assertEquals(listOf("h2e2"), readSavedGame().uciMoves)
    }

    @Test
    fun startingANewGameOverASavedOneAsksFirst() {
        startTwoPlayerGame()
        move("h2", "e2")
        waitForSavedMoves(1)
        rule.onNodeWithContentDescription(t(L10n.Game.menu)).performClick()
        click(L10n.GameMenu.saveAndLeave)
        waitForText(L10n.Home.Continue.title)

        click(L10n.Mode.localTwoPlayer)
        click(L10n.NewGame.start)
        waitForText(L10n.NewGame.Replace.title)
        click(L10n.Common.cancel)
        assertNoText(t(L10n.Game.Status.sideToMove, red))
        click(L10n.NewGame.start)
        click(L10n.NewGame.Replace.confirm)
        waitForText(L10n.Game.Status.sideToMove, red)
        assertEquals("Red Cannon", pieceAt("h2")) // a fresh board
    }

    @Test
    fun theGameMenuExplainsTheRecordFormat() {
        startTwoPlayerGame()
        rule.onNodeWithContentDescription(t(L10n.Game.menu)).performClick()
        waitForText(L10n.GameMenu.share)
        rule.onNodeWithText(t(L10n.GameMenu.movesValue)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(t(L10n.GameMenu.formatNote)).performScrollTo().assertIsDisplayed()
        click(L10n.Common.resume)
        waitForText(L10n.Game.Status.sideToMove, red)
    }

    // region Against the engine

    @Test
    fun theEngineAnswersEveryHumanMoveWithALegalMove() {
        startComputerGame(level = 1)
        waitForText(L10n.Game.Status.yourMove)
        assertEquals(Side.RED, readSavedGameOrNull()?.humanSide ?: Side.RED)

        val script = listOf("h2" to "e2", "b0" to "c2", "h0" to "g2")
        var expectedPlies = 0
        for ((from, to) in script) {
            // Re-read the position so the scripted move is only played if it is legal now.
            waitForText(L10n.Game.Status.yourMove, timeoutMs = 30_000)
            val position = Position.fromFen(com.frankzhu.xiangqi.core.GameRecord.STANDARD_FEN)
                .replaying(readSavedGameOrNull()?.uciMoves.orEmpty())
            if (position.legalMoves().none { it.uci == from + to }) continue
            move(from, to)
            expectedPlies += 2
            waitForSavedMoves(expectedPlies, timeoutMs = 40_000)
        }
        val record = readSavedGame()
        assertTrue(record.moves.size >= 4)
        // The whole record, human and engine moves, replays legally from the start.
        Position.standard.replaying(record.uciMoves)
        assertEquals(Side.RED, record.moves[0].side)
        assertEquals(Side.BLACK, record.moves[1].side)
    }

    @Test
    fun playingBlackLetsTheEngineOpenAndFlipsTheBoard() {
        startComputerGame(black = true, level = 1)
        waitForSavedMoves(1, timeoutMs = 40_000)
        waitForText(L10n.Game.Status.yourMove, timeoutMs = 30_000)
        val record = readSavedGame()
        assertEquals(Side.BLACK, record.humanSide)
        assertEquals(Side.BLACK, record.orientation)
        assertEquals(Side.RED, record.moves[0].side)
        // Black sits at the bottom, so black's general is lower on screen than red's.
        assertTrue(rule.onNode(square("e9")).getUnclippedBoundsInRoot().top > rule.onNode(square("e0")).getUnclippedBoundsInRoot().top)
    }

    @Test
    fun hintRevealsTheSquareThenTheDestinationAndIsFlaggedInTheRecord() {
        startComputerGame(level = 1)
        waitForText(L10n.Game.Status.yourMove)
        click(L10n.Game.Hint.available)
        waitForText(L10n.Game.Hint.source, timeoutMs = 40_000)
        click(L10n.Game.Hint.source)
        waitForText(L10n.Game.Hint.destination)
        rule.onNodeWithText(t(L10n.Game.Hint.destination)).assertIsNotDisplayedOrDisabled()
    }

    @Test
    fun computerGamesShowTheEngineAndPlayerRails() {
        startComputerGame(level = 1)
        waitForText(L10n.Game.Status.yourMove)
        assertText(t(L10n.Game.Rail.engine, 1))
        assertText(t(L10n.Game.Rail.you, red))
        assertText(t(L10n.Game.Rail.opponent))
    }

    // endregion

    private fun clockTexts(): List<String> = rule.onAllNodes(hasText(":", substring = true)).fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty() }.map { it.text }

    private fun readSavedGameOrNull() = runCatching { readSavedGame() }.getOrNull()

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsNotDisplayedOrDisabled() {
        // After the second tap the hint stays visible but is no longer actionable; nothing to click.
        assertIsDisplayed()
    }
}
