package com.frankzhu.xiangqi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frankzhu.xiangqi.AppModel
import com.frankzhu.xiangqi.AppRoute
import com.frankzhu.xiangqi.core.CCPDCategorySummary
import com.frankzhu.xiangqi.core.CCPDPuzzleAttempt
import com.frankzhu.xiangqi.core.CCPDPuzzleSession
import com.frankzhu.xiangqi.core.CCPDRecord
import com.frankzhu.xiangqi.core.CCPDRecordSummary
import com.frankzhu.xiangqi.core.FeedbackEvent
import com.frankzhu.xiangqi.core.Move
import com.frankzhu.xiangqi.core.Position
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.core.Square
import com.frankzhu.xiangqi.core.legalMoves
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalLocalizer
import com.frankzhu.xiangqi.l10n.LocalizedKey
import com.frankzhu.xiangqi.l10n.Localizer
import com.frankzhu.xiangqi.l10n.UserFacingError
import com.frankzhu.xiangqi.l10n.titleKey
import com.frankzhu.xiangqi.ui.components.BoardMarker
import com.frankzhu.xiangqi.ui.components.BoardView
import com.frankzhu.xiangqi.ui.components.Footnote
import com.frankzhu.xiangqi.ui.components.LabeledRow
import com.frankzhu.xiangqi.ui.components.LoadingBox
import com.frankzhu.xiangqi.ui.components.MarkerStyle
import com.frankzhu.xiangqi.ui.components.ScreenScaffold
import com.frankzhu.xiangqi.ui.components.SettingsSection
import com.frankzhu.xiangqi.ui.components.UnavailableBox
import com.frankzhu.xiangqi.ui.components.nilIfEmpty
import com.frankzhu.xiangqi.ui.theme.LocalXiangqiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Display names for the dataset's Chinese category identifiers, which must not be shown as UI text. */
object CCPDCategory {
    const val MATING_PRACTICE_ID = "殺局_殺法_練習題"

    fun titleKey(category: String): LocalizedKey? = when (category) {
        "中局" -> L10n.Learn.Category.middlegames
        "全盤戰術" -> L10n.Learn.Category.fullGameTactics
        "對局" -> L10n.Learn.Category.games
        "殘局" -> L10n.Learn.Category.endgames
        MATING_PRACTICE_ID -> L10n.Learn.Category.matingPractice
        "開局" -> L10n.Learn.Category.openings
        else -> null
    }

    /** The translated name, or the raw identifier when the corpus grows a category this build does not name. */
    fun title(category: String, l10n: Localizer): String = titleKey(category)?.let { l10n(it) } ?: category

    fun icon(category: String): ImageVector = when (category) {
        "中局" -> Icons.Outlined.GridView
        "全盤戰術" -> Icons.Outlined.TrackChanges
        "對局" -> Icons.Outlined.FormatListNumbered
        "殘局" -> Icons.Outlined.Flag
        MATING_PRACTICE_ID -> Icons.Filled.GpsFixed
        "開局" -> Icons.Outlined.CallSplit
        else -> Icons.Outlined.LibraryBooks
    }
}

private enum class MatchSubcategory(val prefix: String?, val titleKey: LocalizedKey) {
    ALL(null, L10n.Learn.Subcategory.allMatches),
    CCPD_MASTER("對局/大師對局/", L10n.Learn.Subcategory.ccpdMasterMatches),
    CCPD_COMPUTER("對局/電腦對局/", L10n.Learn.Subcategory.ccpdComputerMatches),
    WXF("ICCS/WXF/", L10n.Learn.Subcategory.wxfMatches),
    DONGPING("ICCS/Dongping/", L10n.Learn.Subcategory.dongpingMatches)
}

@Composable
fun LearningHomeScreen(app: AppModel) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    var categories by remember { mutableStateOf<List<CCPDCategorySummary>>(emptyList()) }
    var metadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<UserFacingError?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val library = app.container.learningLibrary.load()
                categories = library.categories()
                metadata = library.metadata()
            }
        } catch (e: Exception) {
            error = UserFacingError(e)
        }
        loading = false
    }

    ScreenScaffold(l10n(L10n.Learn.title), onBack = app::back) {
        when {
            loading -> LoadingBox(l10n(L10n.Learn.loadingLibrary))
            error != null -> UnavailableBox(Icons.Outlined.LibraryBooks, l10n(L10n.Learn.libraryUnavailable), error?.text(l10n))
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(l10n(L10n.Learn.Dataset.title), color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(l10n(L10n.Learn.Dataset.subtitle), color = colors.textSecondary)
                }
                SettingsSection(l10n(L10n.Learn.Section.browse)) {
                    for (category in categories) {
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { app.navigate(AppRoute.LearningCategory(category.id)) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(CCPDCategory.icon(category.id), null, tint = colors.accent, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(CCPDCategory.title(category.id, l10n), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                Text(
                                    l10n(L10n.Learn.recordCount, "%,d".format(l10n.locale, category.recordCount)),
                                    color = colors.textSecondary, fontSize = 14.sp
                                )
                            }
                            Text("›", color = colors.textSecondary, fontSize = 20.sp)
                        }
                    }
                }
                SettingsSection(l10n(L10n.Common.source)) {
                    LabeledRow(l10n(L10n.Learn.license), metadata["license"] ?: "CC BY 4.0")
                    LabeledRow(l10n(L10n.Learn.validatedRecords), metadata["imported_files"] ?: l10n(L10n.Common.emptyValue))
                    metadata["source_revision"]?.let { LabeledRow(l10n(L10n.Learn.datasetRevision), it.take(10)) }
                    Footnote(l10n(L10n.Learn.Dataset.note))
                }
            }
        }
    }
}

@Composable
fun LearningLibraryScreen(app: AppModel, category: String) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    var records by remember { mutableStateOf<List<CCPDRecordSummary>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var subcategory by remember { mutableStateOf(MatchSubcategory.ALL) }
    var error by remember { mutableStateOf<UserFacingError?>(null) }
    var loading by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(query, subcategory) {
        if (query.isNotEmpty()) delay(250)
        loading = true
        try {
            records = withContext(Dispatchers.IO) {
                app.container.learningLibrary.load().records(
                    category = category, query = query, sourcePrefix = subcategory.prefix, limit = 200
                )
            }
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = UserFacingError(e)
        }
        loading = false
    }

    ScreenScaffold(CCPDCategory.title(category, l10n), onBack = app::back) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(l10n(L10n.Learn.searchPrompt)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.line.copy(alpha = 0.3f),
                    focusedTextColor = colors.text, unfocusedTextColor = colors.text, cursorColor = colors.accent
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (category == "對局") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(onClick = { menuOpen = true }) {
                        Text(l10n(L10n.Learn.Subcategory.title) + ": " + l10n(subcategory.titleKey), color = colors.text)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        for (option in MatchSubcategory.entries) {
                            DropdownMenuItem(text = { Text(l10n(option.titleKey)) }, onClick = { subcategory = option; menuOpen = false })
                        }
                    }
                }
            }
            when {
                loading && records.isEmpty() -> LoadingBox(l10n(L10n.Learn.loadingRecords))
                error != null && records.isEmpty() ->
                    UnavailableBox(Icons.Filled.Warning, l10n(L10n.Learn.couldNotLoadRecords), error?.text(l10n))
                records.isEmpty() -> UnavailableBox(Icons.Filled.Search, query, null)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(records, key = { it.id }) { record ->
                        RecordRow(record, l10n) {
                            app.navigate(
                                if (category == CCPDCategory.MATING_PRACTICE_ID) AppRoute.PracticeRecord(record.id)
                                else AppRoute.StudyRecord(record.id)
                            )
                        }
                        com.frankzhu.xiangqi.ui.components.Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: CCPDRecordSummary, l10n: Localizer, onClick: () -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(record.event.nilIfEmpty() ?: record.sourcePath, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val red = record.red.nilIfEmpty()
        val black = record.black.nilIfEmpty()
        if (red != null || black != null) {
            Text(
                listOfNotNull(red, if (red != null || black != null) l10n(L10n.Common.nameSeparator) else null, black).joinToString(" "),
                color = colors.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            listOfNotNull(record.dateText.nilIfEmpty(), record.ecco.nilIfEmpty(), l10n(L10n.Learn.plyCount, record.moveCount)).joinToString("  "),
            color = colors.textSecondary.copy(alpha = 0.7f), fontSize = 12.sp
        )
    }
}

@Composable
fun StudyScreen(app: AppModel, recordId: String) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    val prefs = app.container.preferences
    val progress = app.container.learningProgress
    var record by remember { mutableStateOf<CCPDRecord?>(null) }
    var ply by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<UserFacingError?>(null) }
    var bookmarked by remember { mutableStateOf(false) }
    var completionRecorded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recordId) {
        try {
            withContext(Dispatchers.IO) {
                val loaded = app.container.learningLibrary.load().record(recordId)
                if (loaded == null) {
                    error = UserFacingError(L10n.Study.recordMissing)
                } else {
                    val saved = progress.progress(recordId)
                    ply = saved.lastPly.coerceAtMost(loaded.moves.size)
                    bookmarked = saved.isBookmarked
                    progress.recordOpened(recordId, ply)
                    record = loaded
                }
            }
        } catch (e: Exception) {
            error = UserFacingError(e)
        }
    }

    fun setPly(value: Int) {
        val r = record ?: return
        ply = value.coerceIn(0, r.moves.size)
        scope.launch(Dispatchers.IO) {
            runCatching {
                progress.updateLastPly(ply, recordId)
                if (ply == r.moves.size && !completionRecorded) {
                    completionRecorded = true
                    progress.recordCompletion(recordId, ply)
                }
            }
        }
    }

    ScreenScaffold(
        l10n(L10n.Study.title), onBack = app::back,
        actions = {
            TextButton(onClick = { app.navigate(AppRoute.PracticeRecord(recordId)) }) { Text(l10n(L10n.Study.practice), color = colors.accent) }
            IconButton(onClick = {
                scope.launch(Dispatchers.IO) { runCatching { bookmarked = progress.toggleBookmark(recordId).isBookmarked } }
            }) {
                Icon(
                    if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    l10n(if (bookmarked) L10n.Study.removeBookmark else L10n.Study.bookmark), tint = colors.accent
                )
            }
        }
    ) {
        val r = record
        when {
            r != null -> Column(Modifier.fillMaxSize()) {
                val position = remember(r, ply) { runCatching { r.positionAfterPly(ply) }.getOrDefault(Position.standard) }
                val last = if (ply > 0) Move.fromUci(r.moves[ply - 1].uci) else null
                BoardView(
                    position = position, orientation = Side.RED,
                    markers = last?.let { listOf(BoardMarker(it.from, colors.accent.copy(alpha = 0.28f), MarkerStyle.CORNERS), BoardMarker(it.to, colors.accent.copy(alpha = 0.5f), MarkerStyle.CORNERS)) }.orEmpty(),
                    glyphSet = prefs.pieceGlyphs, showsCoordinates = prefs.coordinates.isVisible(Side.RED),
                    aspectRatio = 8f / 9.25f, boardLabel = l10n(L10n.Board.study),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val listState = rememberLazyListState()
                LaunchedEffect(ply) { if (ply > 0) listState.animateScrollToItem((ply - 1 + 2).coerceAtMost(r.moves.size + 1)) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                    item {
                        SettingsSection(null) {
                            Text(r.summary.event.nilIfEmpty() ?: r.summary.sourcePath, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            r.summary.red.nilIfEmpty()?.let { LabeledRow(l10n(L10n.Side.red), it) }
                            r.summary.black.nilIfEmpty()?.let { LabeledRow(l10n(L10n.Side.black), it) }
                            r.summary.result.nilIfEmpty()?.let { LabeledRow(l10n(L10n.Common.result), it) }
                            r.summary.ecco.nilIfEmpty()?.let { LabeledRow(l10n(L10n.Learn.ecco), it) }
                        }
                    }
                    item {
                        Text(l10n(L10n.Common.moves).uppercase(), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                    items(r.moves, key = { it.ply }) { move ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (ply == move.ply) colors.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(role = Role.Button) { setPly(move.ply) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(l10n(L10n.Study.moveNumber, move.ply), color = colors.textSecondary, modifier = Modifier.width(42.dp))
                            Text(move.sourceNotation, color = colors.text, modifier = Modifier.weight(1f))
                            Text(move.uci, color = colors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                    item {
                        SettingsSection(l10n(L10n.Common.source)) {
                            Text(r.summary.sourcePath, color = colors.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            Footnote(l10n(L10n.Study.attribution))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(colors.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { setPly(ply - 1) }, enabled = ply > 0) { Text("‹ " + l10n(L10n.Common.previous), color = colors.accent.copy(alpha = if (ply > 0) 1f else 0.4f)) }
                    Text(l10n(L10n.Study.plyProgress, ply, r.moves.size), color = colors.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { setPly(ply + 1) }, enabled = ply < r.moves.size) { Text(l10n(L10n.Common.next) + " ›", color = colors.accent.copy(alpha = if (ply < r.moves.size) 1f else 0.4f)) }
                }
            }
            error != null -> UnavailableBox(Icons.Filled.Warning, l10n(L10n.Study.couldNotOpen), error?.text(l10n))
            else -> LoadingBox(l10n(L10n.Study.opening))
        }
    }
}

private sealed class PuzzleFeedback {
    object FindRecordedMove : PuzzleFeedback()
    object ChooseDestination : PuzzleFeedback()
    object Incorrect : PuzzleFeedback()
    object Correct : PuzzleFeedback()
    object Completed : PuzzleFeedback()
    data class Hint(val notation: String) : PuzzleFeedback()

    fun text(l10n: Localizer): String = when (this) {
        FindRecordedMove -> l10n(L10n.Practice.findRecordedMove)
        ChooseDestination -> l10n(L10n.Practice.chooseDestination)
        Incorrect -> l10n(L10n.Practice.incorrect)
        Correct -> l10n(L10n.Practice.correct)
        Completed -> l10n(L10n.Practice.completed)
        is Hint -> l10n(L10n.Practice.hintFormat, notation)
    }
}

@Composable
fun PracticeScreen(app: AppModel, recordId: String) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    val prefs = app.container.preferences
    val progress = app.container.learningProgress
    var record by remember { mutableStateOf<CCPDRecord?>(null) }
    var puzzle by remember { mutableStateOf<CCPDPuzzleSession?>(null) }
    // The session is mutable, so a counter forces recomposition after each attempt.
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Square?>(null) }
    var feedback by remember { mutableStateOf<PuzzleFeedback>(PuzzleFeedback.FindRecordedMove) }
    var error by remember { mutableStateOf<UserFacingError?>(null) }
    var completionRecorded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recordId) {
        app.container.feedback.prepare()
        try {
            withContext(Dispatchers.IO) {
                val loaded = app.container.learningLibrary.load().record(recordId)
                if (loaded == null) {
                    error = UserFacingError(L10n.Study.recordMissing)
                } else {
                    puzzle = CCPDPuzzleSession(loaded)
                    progress.recordOpened(recordId)
                    record = loaded
                }
            }
        } catch (e: Exception) {
            error = UserFacingError(e)
        }
    }

    fun tap(square: Square) {
        val current = puzzle ?: return
        if (current.isComplete) return
        val destinations = selected?.let { from -> current.position.legalMoves().filter { it.from == from }.map { it.to }.toSet() }.orEmpty()
        val from = selected
        if (from != null && square in destinations) {
            try {
                when (current.attempt(Move(from, square))) {
                    is CCPDPuzzleAttempt.Incorrect -> {
                        feedback = PuzzleFeedback.Incorrect
                        app.container.feedback.play(FeedbackEvent.INVALID_ATTEMPT)
                    }
                    is CCPDPuzzleAttempt.Correct -> {
                        feedback = PuzzleFeedback.Correct
                        app.container.feedback.play(FeedbackEvent.MOVE)
                    }
                    CCPDPuzzleAttempt.Completed -> {
                        feedback = PuzzleFeedback.Completed
                        app.container.feedback.play(FeedbackEvent.GAME_END)
                        if (!completionRecorded) {
                            completionRecorded = true
                            val ply = current.currentPly
                            scope.launch(Dispatchers.IO) { runCatching { progress.recordCompletion(recordId, ply) } }
                        }
                    }
                }
                selected = null
                revision++
                val ply = current.currentPly
                scope.launch(Dispatchers.IO) { runCatching { progress.updateLastPly(ply, recordId) } }
            } catch (e: Exception) {
                error = UserFacingError(e)
            }
            return
        }
        val piece = current.position.pieceAt(square)
        if (piece != null && piece.side == current.position.sideToMove) {
            selected = square
            feedback = PuzzleFeedback.ChooseDestination
            app.container.feedback.play(FeedbackEvent.PIECE_SELECTED)
        } else {
            selected = null
        }
    }

    ScreenScaffold(l10n(L10n.Practice.title), onBack = app::back) {
        val p = puzzle
        val r = record
        when {
            p != null && r != null -> {
                @Suppress("UNUSED_EXPRESSION") revision
                val destinations = selected?.let { from -> p.position.legalMoves().filter { it.from == from }.map { it.to } }.orEmpty()
                val markers = buildList {
                    p.lastMove?.let {
                        add(BoardMarker(it.from, colors.accent.copy(alpha = 0.28f), MarkerStyle.RING))
                        add(BoardMarker(it.to, colors.accent.copy(alpha = 0.5f), MarkerStyle.RING))
                    }
                    selected?.let { add(BoardMarker(it, colors.accent, MarkerStyle.RING)) }
                    for (square in destinations.sorted()) {
                        add(BoardMarker(square, colors.legal, if (p.position.pieceAt(square) != null) MarkerStyle.RING else MarkerStyle.DOT))
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BoardView(
                        position = p.position, orientation = p.practiceSide, markers = markers,
                        glyphSet = prefs.pieceGlyphs, showsCoordinates = prefs.coordinates.isVisible(p.practiceSide),
                        aspectRatio = 8f / 9.25f, boardLabel = l10n(L10n.Board.practice),
                        squareLabel = { it.uci }, onTap = ::tap,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Column(Modifier.fillMaxWidth().background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(r.summary.event.nilIfEmpty() ?: r.summary.sourcePath, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2)
                        Row {
                            Text(l10n(L10n.Practice.moveProgress, p.currentPly + 1, r.moves.size), color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(l10n(L10n.Practice.mistakes, p.mistakes), color = colors.textSecondary, fontSize = 12.sp)
                        }
                        Text(feedback.text(l10n), color = colors.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                val expected = p.expectedMove
                                if (expected != null) {
                                    selected = expected.from
                                    if (p.currentPly < r.moves.size) feedback = PuzzleFeedback.Hint(r.moves[p.currentPly].sourceNotation)
                                    revision++
                                }
                            }) { Text(l10n(L10n.Common.hint), color = colors.text) }
                            Box(Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                p.restart(); selected = null; completionRecorded = false
                                feedback = PuzzleFeedback.FindRecordedMove; revision++
                                scope.launch(Dispatchers.IO) { runCatching { progress.recordOpened(recordId) } }
                            }) { Text(l10n(L10n.Common.restart), color = colors.text) }
                            OutlinedButton(onClick = { app.navigate(AppRoute.StudyRecord(recordId)) }) { Text(l10n(L10n.Practice.studyLine), color = colors.text) }
                        }
                    }
                }
            }
            error != null -> UnavailableBox(Icons.Filled.Warning, l10n(L10n.Practice.couldNotOpen), error?.text(l10n))
            else -> LoadingBox(l10n(L10n.Practice.opening))
        }
    }
}
