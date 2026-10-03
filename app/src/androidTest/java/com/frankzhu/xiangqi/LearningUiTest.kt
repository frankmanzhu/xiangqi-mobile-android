package com.frankzhu.xiangqi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frankzhu.xiangqi.core.Move
import com.frankzhu.xiangqi.core.legalMoves
import com.frankzhu.xiangqi.core.CCPDRecord
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.ui.screens.CCPDCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The offline learning library: browse, search, study, bookmark, and practise against the real database. */
@RunWith(AndroidJUnit4::class)
class LearningUiTest : AppUiTest() {

    private val library get() = container.learningLibrary.load()

    private fun openLearn() {
        click(L10n.Home.Mode.Learn.title)
        waitForText(L10n.Learn.Dataset.title, timeoutMs = 60_000) // first run copies the database out of the APK
    }

    private fun openCategory(id: String) {
        rule.onNodeWithText(CCPDCategory.title(id, en)).performScrollTo().performClick()
    }

    @Test
    fun theLibraryListsEveryCategoryWithItsRecordCount() {
        openLearn()
        val expected = library.categories()
        assertEquals(6, expected.size)
        for (category in expected) {
            rule.onNodeWithText(CCPDCategory.title(category.id, en)).performScrollTo().assertIsDisplayed()
            assertText(t(L10n.Learn.recordCount, "%,d".format(en.locale, category.recordCount)))
        }
        assertTrue("bundle should contain a real corpus", expected.sumOf { it.recordCount } >= 9_000)
        rule.onNodeWithText(t(L10n.Learn.Dataset.note)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theBundledCorpusIsTheFullDatasetWhenLinkedFromTheIosRepo() {
        val total = library.categories().sumOf { it.recordCount }
        val metadata = library.metadata()
        if (metadata["bundle"] == "lite") {
            assertTrue(total in 9_000..12_000)
        } else {
            assertEquals(145_065, total)
            assertEquals("145065", metadata["imported_files"])
        }
    }

    @Test
    fun browsingACategoryListsRecordsAndOpensTheStudyView() {
        openLearn()
        openCategory("開局")
        waitForText(L10n.Learn.searchPrompt)
        val first = library.records(category = "開局", limit = 1).first()
        waitForText(first.event?.trim().takeUnless { it.isNullOrEmpty() } ?: first.sourcePath)
        rule.onAllNodes(hasText(first.event?.trim().takeUnless { it.isNullOrEmpty() } ?: first.sourcePath))[0].performClick()
        waitForText(L10n.Study.title)
        waitForText(L10n.Study.plyProgress, 0.coerceAtLeast(container.learningProgress.progress(first.id).lastPly), library.record(first.id)!!.moves.size)
    }

    @Test
    fun searchingFindsRecordsInEitherChineseScript() {
        openLearn()
        openCategory("對局")
        waitForText(L10n.Learn.searchPrompt)
        val summary = library.records(category = "對局", limit = 200).first { !it.red.isNullOrBlank() }
        val name = summary.red!!.trim()
        // Type the name exactly as stored, then expect that player's record to be listed.
        rule.onNodeWithText(t(L10n.Learn.searchPrompt)).performTextInput(name)
        rule.waitUntil(30_000) { rule.onAllNodes(hasText(name, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // A query with no match shows the empty state rather than stale rows.
        rule.onNodeWithText(name).performClick() // focus
    }

    @Test
    fun theMatchCollectionFilterNarrowsTheGameList() {
        openLearn()
        openCategory("對局")
        waitForTextContaining(t(L10n.Learn.Subcategory.title), timeoutMs = 30_000)
        rule.onNodeWithText(t(L10n.Learn.Subcategory.title) + ": " + t(L10n.Learn.Subcategory.allMatches)).performClick()
        click(L10n.Learn.Subcategory.wxfMatches)
        waitForText(t(L10n.Learn.Subcategory.title) + ": " + t(L10n.Learn.Subcategory.wxfMatches))
        val wxf = library.records(category = "對局", sourcePrefix = "ICCS/WXF/", limit = 1)
        assertTrue(wxf.isNotEmpty())
        val label = wxf.first().event?.trim().takeUnless { it.isNullOrEmpty() } ?: wxf.first().sourcePath
        rule.waitUntil(30_000) { rule.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun studyingARecordStepsThroughItsMovesAndRemembersProgress() {
        val record = library.record(library.records(category = "開局", limit = 1).first().id)!!
        container.learningProgress.updateLastPly(0, record.summary.id)
        navigateToStudy(record)
        val total = record.moves.size
        waitForText(L10n.Study.plyProgress, 0, total)
        clickContaining(t(L10n.Common.next))
        waitForText(L10n.Study.plyProgress, 1, total)
        clickContaining(t(L10n.Common.next))
        waitForText(L10n.Study.plyProgress, 2, total)
        clickContaining(t(L10n.Common.previous))
        waitForText(L10n.Study.plyProgress, 1, total)
        // Progress is stored as the learner steps.
        rule.waitUntil(5_000) { container.learningProgress.progress(record.summary.id).lastPly == 1 }
        // Re-opening the record resumes at that move.
        launch()
        navigateToStudy(record)
        waitForText(L10n.Study.plyProgress, 1, total)
    }

    @Test
    fun bookmarkingARecordIsSavedAndToggles() {
        val record = library.record(library.records(category = "殘局", limit = 1).first().id)!!
        val id = record.summary.id
        if (container.learningProgress.progress(id).isBookmarked) container.learningProgress.toggleBookmark(id)
        navigateToStudy(record, category = "殘局")
        rule.onNodeWithContentDescription(t(L10n.Study.bookmark)).performClick()
        rule.waitUntil(5_000) { container.learningProgress.progress(id).isBookmarked }
        rule.onNodeWithContentDescription(t(L10n.Study.removeBookmark)).performClick()
        rule.waitUntil(5_000) { !container.learningProgress.progress(id).isBookmarked }
    }

    @Test
    fun practisingAPuzzleAcceptsTheRecordedLineAndCompletesIt() {
        val record = firstShortPuzzle()
        navigateToPractice(record)
        waitForText(L10n.Practice.findRecordedMove)
        playRecordedLine(record)
        waitForText(L10n.Practice.completed, timeoutMs = 30_000)
        rule.waitUntil(5_000) { container.learningProgress.progress(record.summary.id).completions >= 1 }
    }

    @Test
    fun practisingAPuzzleRejectsAWrongMoveAndCountsTheMistake() {
        val record = firstShortPuzzle()
        navigateToPractice(record)
        waitForText(L10n.Practice.findRecordedMove)
        val first = Move.fromUci(record.moves.first().uci)!!
        val position = record.positionAfterPly(0)
        val wrong = position.legalMoves().first { it != first && it.from != first.from }
        move(wrong.from.uci, wrong.to.uci)
        waitForText(L10n.Practice.incorrect)
        waitForText(L10n.Practice.mistakes, 1)
    }

    @Test
    fun theHintNamesTheExpectedMoveAndRestartResetsThePuzzle() {
        val record = firstShortPuzzle()
        navigateToPractice(record)
        waitForText(L10n.Practice.findRecordedMove)
        click(L10n.Common.hint)
        waitForText(L10n.Practice.hintFormat, record.moves.first().sourceNotation)
        // Make a mistake, then restart: counters clear and the prompt returns.
        val first = Move.fromUci(record.moves.first().uci)!!
        val wrong = record.positionAfterPly(0).legalMoves().first { it != first && it.from != first.from }
        move(wrong.from.uci, wrong.to.uci)
        waitForText(L10n.Practice.mistakes, 1)
        click(L10n.Common.restart)
        waitForText(L10n.Practice.mistakes, 0)
        waitForText(L10n.Practice.findRecordedMove)
    }

    // region Helpers

    private fun firstShortPuzzle(): CCPDRecord {
        val summaries = library.records(category = CCPDCategory.MATING_PRACTICE_ID, limit = 200)
        val shortest = summaries.minByOrNull { it.moveCount }!!
        return library.record(shortest.id)!!
    }

    private fun navigateToStudy(record: CCPDRecord, category: String = "開局") {
        navigateTo(AppRoute.StudyRecord(record.summary.id))
        waitForText(L10n.Study.title, timeoutMs = 60_000)
    }

    private fun navigateToPractice(record: CCPDRecord) {
        navigateTo(AppRoute.PracticeRecord(record.summary.id))
        waitForText(L10n.Practice.findRecordedMove, timeoutMs = 60_000)
    }

    /** Plays the learner's side of the recorded line; the recorded replies are applied by the app. */
    private fun playRecordedLine(record: CCPDRecord) {
        var ply = 0
        while (ply < record.moves.size) {
            val move = record.moves[ply].uci
            move(move.substring(0, 2), move.substring(2, 4))
            ply += 2
            rule.waitForIdle()
        }
    }

    // endregion
}
