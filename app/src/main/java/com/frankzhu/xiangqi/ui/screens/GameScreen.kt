package com.frankzhu.xiangqi.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frankzhu.xiangqi.AppModel
import com.frankzhu.xiangqi.core.GameMessage
import com.frankzhu.xiangqi.core.GameMode
import com.frankzhu.xiangqi.core.GameSession
import com.frankzhu.xiangqi.core.GameState
import com.frankzhu.xiangqi.core.HintStage
import com.frankzhu.xiangqi.core.Move
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.core.Square
import com.frankzhu.xiangqi.core.isInCheck
import com.frankzhu.xiangqi.core.TimeControl
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalLocalizer
import com.frankzhu.xiangqi.l10n.LocalizedKey
import com.frankzhu.xiangqi.l10n.Localizer
import com.frankzhu.xiangqi.l10n.UserFacingError
import com.frankzhu.xiangqi.l10n.text
import com.frankzhu.xiangqi.l10n.titleKey
import com.frankzhu.xiangqi.ui.components.BoardMarker
import com.frankzhu.xiangqi.ui.components.BoardView
import com.frankzhu.xiangqi.ui.components.MarkerStyle
import com.frankzhu.xiangqi.ui.theme.ThemeRegistry
import com.frankzhu.xiangqi.ui.theme.XiangqiThemeProvider
import com.frankzhu.xiangqi.ui.theme.LocalXiangqiTheme
import kotlinx.coroutines.launch

@Composable
fun GameScreen(app: AppModel, session: GameSession) {
    // A game is played in the theme it was started with, so a reopened record looks as it did when saved.
    val state by session.state.collectAsStateWithLifecycle()
    val theme = ThemeRegistry.theme(state.record.theme)
    val scope = rememberCoroutineScope()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }

    BackHandler { app.leaveGame() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { scope.launch { session.pause() } }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { session.startIfNeeded() }
    LaunchedEffect(Unit) {
        app.container.feedback.prepare()
        runCatching { app.container.engine.prepare() }
        session.startIfNeeded()
    }

    XiangqiThemeProvider(theme) {
        val colors = theme.colors
        val l10n = LocalLocalizer.current
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
            val landscape = maxWidth > maxHeight
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.fillMaxSize()) {
                    GameTopBar(state, onLeave = { app.leaveGame() }, onMenu = { showMenu = true })
                    if (landscape) {
                        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FitBoard(Modifier.weight(1f).fillMaxHeight()) { BoardArea(app, session, state, it) }
                            Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PlayerRail(state, state.record.orientation.opponent, isOpponent = true)
                                StatusLine(state)
                                PlayerRail(state, state.record.orientation, isOpponent = false)
                                BottomControls(session, state) { showHistory = true }
                            }
                        }
                    } else {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().widthIn(max = 680.dp).padding(horizontal = 12.dp).padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PlayerRail(state, state.record.orientation.opponent, isOpponent = true)
                            StatusLine(state)
                            FitBoard(Modifier.weight(1f).padding(horizontal = 8.dp)) { BoardArea(app, session, state, it) }
                            PlayerRail(state, state.record.orientation, isOpponent = false)
                            BottomControls(session, state) { showHistory = true }
                        }
                    }
                }
                state.message?.let { MessageBanner(it, l10n) { session.dismissMessage() } }
            }
        }

        if (showHistory) MoveHistorySheet(session, state) { showHistory = false }
        if (showMenu) GameMenuSheet(app, session, state) { showMenu = false }
        if (state.showResult) ResultSheet(app, session, state, onReview = { session.dismissResult(); showHistory = true })
    }
}

/** Sizes the board to the largest 0.87-aspect rectangle that fits, so it never overflows in either orientation. */
@Composable
private fun FitBoard(modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * BOARD_ASPECT)
        content(Modifier.width(width))
    }
}

private const val BOARD_ASPECT = 0.87f

@Composable
private fun GameTopBar(state: GameState, onLeave: () -> Unit, onMenu: () -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    val l10n = LocalLocalizer.current
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onLeave) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, l10n(L10n.Game.leave), tint = colors.text)
        }
        Text(
            l10n(state.record.mode.titleKey), color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, l10n(L10n.Game.menu), tint = colors.text) }
    }
}

@Composable
private fun StatusLine(state: GameState) {
    val colors = LocalXiangqiTheme.current.colors
    val l10n = LocalLocalizer.current
    Row(
        Modifier.height(28.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.isThinking) CircularProgressIndicator(Modifier.size(16.dp), color = colors.accent, strokeWidth = 2.dp)
        if (state.record.result == null && state.position.isInCheck(state.position.sideToMove)) {
            Icon(Icons.Filled.Warning, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
        Text(state.status.text(l10n), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlayerRail(state: GameState, side: Side, isOpponent: Boolean) {
    val theme = LocalXiangqiTheme.current
    val colors = theme.colors
    val l10n = LocalLocalizer.current
    val record = state.record
    val isActive = record.result == null && state.position.sideToMove == side
    val sideName = l10n(side.titleKey)
    val label = when {
        record.mode == GameMode.LOCAL_TWO_PLAYER -> sideName
        side == record.humanSide -> l10n(L10n.Game.Rail.you, sideName)
        else -> l10n(L10n.Game.Rail.engine, record.computerLevel)
    }
    val clock = if (side == Side.RED) record.redSecondsRemaining else record.blackSecondsRemaining
    Row(
        Modifier.fillMaxWidth().height(42.dp)
            .background(colors.surface.copy(alpha = 0.82f), theme.cardShape(14.dp))
            .border(if (isActive) 1.5.dp else 1.dp, if (isActive) colors.accent.copy(alpha = 0.55f) else theme.border, theme.cardShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(if (side == Side.RED) colors.red else colors.black, CircleShape))
        Text(label, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (clock != null) {
            Text("%02d:%02d".format(clock / 60, clock % 60), color = colors.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        } else {
            val key = when {
                isActive -> L10n.Game.Rail.toMove
                isOpponent -> L10n.Game.Rail.opponent
                else -> L10n.Game.Rail.player
            }
            Text(l10n(key), color = if (isActive) colors.accent else colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoardArea(app: AppModel, session: GameSession, state: GameState, modifier: Modifier) {
    val colors = LocalXiangqiTheme.current.colors
    val l10n = LocalLocalizer.current
    val prefs = app.container.preferences
    val orientation = state.record.orientation
    val shown = state.displayedPosition

    val markers = buildList {
        state.lastMove?.let {
            add(BoardMarker(it.from, colors.accent.copy(alpha = 0.3f), MarkerStyle.CORNERS))
            add(BoardMarker(it.to, colors.accent.copy(alpha = 0.45f), MarkerStyle.CORNERS))
        }
        state.selectedSquare?.let { add(BoardMarker(it, colors.accent, MarkerStyle.RING)) }
        for (square in state.legalDestinations.sorted()) {
            val occupied = state.position.pieceAt(square) != null
            add(BoardMarker(square, colors.legal, if (occupied) MarkerStyle.RING else MarkerStyle.DOT))
        }
        when (val hint = state.hintStage) {
            is HintStage.Source -> add(BoardMarker(hint.move.from, colors.legal, MarkerStyle.HINT))
            is HintStage.Destination -> {
                add(BoardMarker(hint.move.from, colors.legal, MarkerStyle.HINT))
                add(BoardMarker(hint.move.to, colors.legal, MarkerStyle.RING))
            }
            else -> Unit
        }
    }

    BoardView(
        position = shown,
        orientation = orientation,
        markers = markers,
        glyphSet = prefs.pieceGlyphs,
        showsCoordinates = prefs.coordinates.isVisible(orientation),
        modifier = modifier,
        boardLabel = l10n(L10n.Board.label),
        squareLabel = { square ->
            val piece = shown.pieceAt(square)
            if (piece == null) {
                l10n(L10n.Board.Square.empty, square.uci)
            } else {
                l10n(L10n.Board.Square.occupied, l10n(piece.side.titleKey), l10n(piece.kind.titleKey), square.uci,
                    l10n(if (piece.side == shown.sideToMove) L10n.Board.State.selectable else L10n.Board.State.occupied))
            }
        },
        squareState = { square -> if (square in state.legalDestinations) l10n(L10n.Board.legalDestination) else null },
        onTap = session::tap,
        onDrag = session::drag
    )
}

@Composable
private fun BottomControls(session: GameSession, state: GameState, onShowHistory: () -> Unit) {
    val pending = state.pendingMove
    if (pending != null) ConfirmationBar(session, pending) else ActionDock(session, state, onShowHistory)
}

@Composable
private fun ConfirmationBar(session: GameSession, move: Move) {
    val theme = LocalXiangqiTheme.current
    val colors = theme.colors
    val l10n = LocalLocalizer.current
    Row(
        Modifier.fillMaxWidth().height(58.dp).background(colors.surface, theme.cardShape(14.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(l10n(L10n.Game.confirmMove), color = colors.textSecondary, fontSize = 12.sp)
            Text(move.uci, color = colors.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        OutlinedButton(onClick = session::cancelPendingMove) { Text(l10n(L10n.Common.cancel), color = colors.text) }
        Button(
            onClick = session::confirmPendingMove,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
        ) { Text(l10n(L10n.Common.confirm)) }
    }
}

@Composable
private fun ActionDock(session: GameSession, state: GameState, onShowHistory: () -> Unit) {
    val scope = rememberCoroutineScope()
    val record = state.record
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DockAction(L10n.Game.undo, Icons.AutoMirrored.Filled.Undo, record.timeControl == TimeControl.CASUAL && record.moves.isNotEmpty()) {
            scope.launch { session.undo() }
        }
        if (record.mode == GameMode.COMPUTER) {
            val title = when (state.hintStage) {
                HintStage.Available -> L10n.Game.Hint.available
                HintStage.Searching -> L10n.Game.Hint.searching
                is HintStage.Source -> L10n.Game.Hint.source
                is HintStage.Destination -> L10n.Game.Hint.destination
            }
            DockAction(title, Icons.Outlined.Lightbulb, state.canInteract) { session.hint() }
        }
        DockAction(L10n.Game.flip, Icons.Outlined.Sync, true) { session.flip() }
        DockAction(L10n.Game.movesAction, Icons.Outlined.FormatListNumbered, record.moves.isNotEmpty(), onShowHistory)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockAction(
    title: LocalizedKey, icon: ImageVector, enabled: Boolean, onClick: () -> Unit
) {
    val theme = LocalXiangqiTheme.current
    val colors = theme.colors
    val l10n = LocalLocalizer.current
    Column(
        Modifier.weight(1f).height(52.dp)
            .background(colors.surface.copy(alpha = if (enabled) 1f else 0.42f), theme.controlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = colors.text.copy(alpha = if (enabled) 1f else 0.42f), modifier = Modifier.size(22.dp))
        Text(l10n(title), color = colors.text.copy(alpha = if (enabled) 1f else 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun MessageBanner(message: GameMessage, l10n: Localizer, onDismiss: () -> Unit) {
    val text = when (message) {
        GameMessage.EngineMismatch -> l10n(L10n.Game.Message.engineMismatch)
        GameMessage.NotSaved -> l10n(L10n.Game.Message.notSaved)
        is GameMessage.Failure -> UserFacingError(message.error).text(l10n)
    }
    Text(
        text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 60.dp).background(Color(0xE6D32F2F), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

// region Sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveHistorySheet(session: GameSession, state: GameState, onDismiss: () -> Unit) {
    val theme = LocalXiangqiTheme.current
    val colors = theme.colors
    val l10n = LocalLocalizer.current
    val context = LocalContext.current
    val moves = state.record.moves
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l10n(L10n.History.title, moves.size), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { shareText(context, session.shareText()) }) { Text(l10n(L10n.GameMenu.share), color = colors.accent) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(l10n(L10n.Common.done), color = colors.accent) }
            }
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                for (index in moves.indices step 2) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(l10n(L10n.History.moveNumber, index / 2 + 1), color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(32.dp).padding(top = 8.dp), textAlign = TextAlign.End)
                        MoveCell(state, index, Modifier.weight(1f)) { session.showReplay(index + 1) }
                        if (index + 1 < moves.size) MoveCell(state, index + 1, Modifier.weight(1f)) { session.showReplay(index + 2) } else Box(Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(colors.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val ply = state.replayPly ?: moves.size
                androidx.compose.material3.TextButton(onClick = { session.stepReplay(-1) }, enabled = ply > 0) { Text("‹ " + l10n(L10n.Common.previous), color = colors.accent.copy(alpha = if (ply > 0) 1f else 0.4f)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (state.isReplaying) {
                        androidx.compose.material3.TextButton(onClick = session::returnToLive) { Text(l10n(L10n.History.returnToLive), color = colors.accent, fontWeight = FontWeight.SemiBold) }
                    } else {
                        Text(l10n(L10n.History.livePosition), color = colors.textSecondary)
                    }
                }
                androidx.compose.material3.TextButton(onClick = { session.stepReplay(1) }, enabled = ply < moves.size) { Text(l10n(L10n.Common.next) + " ›", color = colors.accent.copy(alpha = if (ply < moves.size) 1f else 0.4f)) }
            }
        }
    }
}

@Composable
private fun MoveCell(state: GameState, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val theme = LocalXiangqiTheme.current
    val colors = theme.colors
    val move = state.record.moves[index]
    val selected = state.replayPly == index + 1
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    Column(
        modifier.background(if (selected) colors.accent.copy(alpha = 0.14f) else Color.Transparent, shape)
            .then(if (selected) Modifier.border(1.dp, colors.accent, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(move.notation, color = colors.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        Text(move.uci, color = colors.textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameMenuSheet(app: AppModel, session: GameSession, state: GameState, onDismiss: () -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    val l10n = LocalLocalizer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmResign by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(l10n(L10n.Game.menu), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            MenuRow(l10n(L10n.Common.resume)) { onDismiss() }
            MenuRow(l10n(L10n.GameMenu.share)) { shareText(context, session.shareText()); onDismiss() }
            Text(l10n(L10n.GameMenu.Section.game).uppercase(), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            if (state.record.result == null) MenuRow(l10n(L10n.GameMenu.resign), destructive = true) { confirmResign = true }
            MenuRow(l10n(L10n.GameMenu.saveAndLeave)) { onDismiss(); app.leaveGame() }
            MenuRow(l10n(L10n.GameMenu.newGame)) { onDismiss(); app.newGameFromGame(state.record.mode) }
            Text(l10n(L10n.GameMenu.Section.format).uppercase(), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            com.frankzhu.xiangqi.ui.components.LabeledRow(l10n(L10n.Common.moves), l10n(L10n.GameMenu.movesValue))
            com.frankzhu.xiangqi.ui.components.LabeledRow(l10n(L10n.GameMenu.rules), state.record.rulesPolicyID.let { if (it.length > 28) it.take(28) + "…" else it })
            com.frankzhu.xiangqi.ui.components.Footnote(l10n(L10n.GameMenu.formatNote))
        }
    }
    if (confirmResign) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(l10n(L10n.GameMenu.resignConfirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmResign = false; scope.launch { session.resign() }; onDismiss() }) {
                    Text(l10n(L10n.GameMenu.resign), color = Color(0xFFD32F2F))
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmResign = false }) { Text(l10n(L10n.Common.cancel)) } }
        )
    }
}

@Composable
private fun MenuRow(title: String, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    Text(
        title, color = if (destructive) Color(0xFFD32F2F) else colors.text, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultSheet(app: AppModel, session: GameSession, state: GameState, onReview: () -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    val l10n = LocalLocalizer.current
    val record = state.record
    val result = record.result
    val title = when {
        result == null -> l10n(L10n.Result.complete)
        result.winner == null -> l10n(L10n.Result.draw)
        else -> l10n(L10n.Result.wins, l10n(result.winner!!.titleKey))
    }
    ModalBottomSheet(onDismissRequest = session::dismissResult, containerColor = colors.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (result?.winner == null) "＝" else "🏁", fontSize = 56.sp)
            Text(title, color = colors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            result?.let { Text(l10n(it.reason.titleKey), color = colors.textSecondary, fontSize = 18.sp) }
            Row(
                Modifier.background(colors.surface, LocalXiangqiTheme.current.cardShape(18.dp)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Summary(l10n(L10n.Result.Summary.moves), "${record.moves.size}")
                Summary(l10n(L10n.Result.Summary.time), "%d:%02d".format(record.elapsedSeconds / 60, record.elapsedSeconds % 60))
                Summary(l10n(L10n.Result.Summary.hints), "${record.moves.count { it.hintUsed }}")
            }
            Button(
                onClick = onReview, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) { Text(l10n(L10n.Result.review), modifier = Modifier.padding(vertical = 4.dp)) }
            OutlinedButton(onClick = { session.dismissResult(); app.leaveGame() }, modifier = Modifier.fillMaxWidth()) {
                Text(l10n(L10n.Common.home), color = colors.text, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun Summary(title: String, value: String) {
    val colors = LocalXiangqiTheme.current.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(title, color = colors.textSecondary, fontSize = 12.sp)
    }
}

internal fun shareText(context: android.content.Context, text: String) {
    if (text.isEmpty()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// endregion
