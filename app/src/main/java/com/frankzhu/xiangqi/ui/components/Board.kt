package com.frankzhu.xiangqi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frankzhu.xiangqi.core.Move
import com.frankzhu.xiangqi.core.Piece
import com.frankzhu.xiangqi.core.Position
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.core.Square
import com.frankzhu.xiangqi.data.PieceGlyphSet
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalLocalizer
import com.frankzhu.xiangqi.ui.theme.LocalXiangqiTheme
import com.frankzhu.xiangqi.ui.theme.XiangqiTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pixel geometry for a 9x10 xiangqi grid inside a given size. When coordinates
 * are shown the left margin widens to hold the rank digits, so labels sit clear
 * of the edge pieces instead of underneath them.
 */
class BoardGeometry(
    val width: Float,
    val height: Float,
    pieceScale: Float,
    val showsCoordinates: Boolean,
    private val density: Float
) {
    val inset: Float
    val leadingInset: Float
    val step: Float
    val pieceSize: Float

    init {
        val safeWidth = if (width.isFinite()) max(width, 80f * density) else 80f * density
        val base = min(22f * density, max(8f * density, safeWidth * 0.055f))
        inset = base
        leadingInset = base + if (showsCoordinates) max(12f * density, safeWidth * 0.038f) else 0f
        step = max(1f, (safeWidth - leadingInset - base) / 8f)
        pieceSize = max(1f, min(44f * density, step * pieceScale))
    }

    fun point(column: Int, row: Int) = Offset(leadingInset + column * step, inset + row * step)

    fun point(square: Square, orientation: Side): Offset {
        val column = if (orientation == Side.RED) square.file else 8 - square.file
        val row = if (orientation == Side.RED) 9 - square.rank else square.rank
        return point(column, row)
    }

    fun square(point: Offset, orientation: Side): Square? {
        val column = ((point.x - leadingInset) / step).roundToInt()
        val row = ((point.y - inset) / step).roundToInt()
        if (column !in 0..8 || row !in 0..9) return null
        return if (orientation == Side.RED) Square(column, 9 - row) else Square(8 - column, row)
    }

    val coordinateFontSize: Float get() = max(9f * density, step * 0.26f)

    /** Baseline for the file letters: below the last rank's pieces, never past the bottom edge. */
    val fileLabelY: Float
        get() {
            val lastRow = point(0, 9).y
            val clear = lastRow + pieceSize / 2 + coordinateFontSize * 0.85f
            val limit = max(height, lastRow) - coordinateFontSize * 0.6f
            return min(clear, limit)
        }

    val rankLabelX: Float get() = max(coordinateFontSize * 0.62f, (leadingInset - pieceSize / 2) / 2)
}

enum class MarkerStyle { DOT, RING, CORNERS, HINT }

/** A marker layered over a board point. */
class BoardMarker(val square: Square, val color: Color, val style: MarkerStyle)

/**
 * The shared board renderer: grid, river text, coordinates, markers and pieces.
 * Every board in the app draws through here so a theme's line weight and river
 * colour apply everywhere. If [onTap] is set the board is interactive, with one
 * labelled touch target per point (as on iOS) so it is usable with TalkBack.
 */
@Composable
fun BoardView(
    position: Position,
    orientation: Side,
    markers: List<BoardMarker>,
    glyphSet: PieceGlyphSet,
    showsCoordinates: Boolean,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.87f,
    elevated: Boolean = false,
    boardLabel: String,
    squareLabel: ((Square) -> String)? = null,
    squareState: ((Square) -> String?)? = null,
    onTap: ((Square) -> Unit)? = null,
    onDrag: ((Square, Square) -> Unit)? = null
) {
    val theme = LocalXiangqiTheme.current
    val l10n = LocalLocalizer.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val riverLeft = l10n(L10n.Board.River.left)
    val riverRight = l10n(L10n.Board.River.right)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .semantics { contentDescription = boardLabel }
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val geometry = remember(widthPx, heightPx, theme.metrics.pieceScale, showsCoordinates, density) {
            BoardGeometry(widthPx, heightPx, theme.metrics.pieceScale, showsCoordinates, density)
        }
        val currentOrientation by rememberUpdatedState(orientation)
        val currentDrag by rememberUpdatedState(onDrag)

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .clip(theme.boardShape)
                .background(theme.colors.board)
                .border(1.dp, theme.colors.line.copy(alpha = 0.2f), theme.boardShape)
        ) {
            drawGrid(geometry, theme)
            drawRiver(geometry, theme, measurer, riverLeft, riverRight)
            if (showsCoordinates) drawCoordinates(geometry, theme, measurer, orientation)
            for (marker in markers) drawMarker(marker, geometry, orientation, measurer)
            for ((square, piece) in position.pieces.entries.sortedBy { it.key }) {
                drawPiece(piece, geometry.point(square, orientation), geometry.pieceSize, theme, glyphSet, measurer)
            }
        }

        if (onTap != null) {
            val stepDp = with(LocalDensity.current) { geometry.step.toDp() }
            // The wrapper is the parent of every touch target, so it still sees drags
            // that start on a point while the points handle plain taps.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(geometry) {
                        var start: Square? = null
                        var last = Offset.Zero
                        detectDragGestures(
                            onDragStart = { start = geometry.square(it, currentOrientation); last = it },
                            onDrag = { change, _ -> last = change.position },
                            onDragEnd = {
                                val from = start
                                start = null
                                val to = geometry.square(last, currentOrientation)
                                if (from != null && to != null) currentDrag?.invoke(from, to)
                            },
                            onDragCancel = { start = null }
                        )
                    }
            ) {
                for (index in 0 until 90) {
                    val square = Square(index % 9, index / 9)
                    val center = geometry.point(square, orientation)
                    val label = squareLabel?.invoke(square).orEmpty()
                    val state = squareState?.invoke(square)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((center.x - geometry.step / 2).roundToInt(), (center.y - geometry.step / 2).roundToInt()) }
                            .size(stepDp)
                            .semantics {
                                contentDescription = label
                                if (state != null) stateDescription = state
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTap(square) }
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGrid(g: BoardGeometry, theme: XiangqiTheme) {
    val stroke = theme.metrics.gridLineWidth.toPx()
    val color = theme.colors.line.copy(alpha = 0.84f)
    fun line(a: Offset, b: Offset) = drawLine(color, a, b, strokeWidth = stroke)
    for (row in 0..9) line(g.point(0, row), g.point(8, row))
    for (column in 0..8) {
        if (column == 0 || column == 8) {
            line(g.point(column, 0), g.point(column, 9))
        } else {
            line(g.point(column, 0), g.point(column, 4))
            line(g.point(column, 5), g.point(column, 9))
        }
    }
    for (top in listOf(0, 7)) {
        line(g.point(3, top), g.point(5, top + 2))
        line(g.point(5, top), g.point(3, top + 2))
    }
}

private fun DrawScope.drawRiver(g: BoardGeometry, theme: XiangqiTheme, m: TextMeasurer, left: String, right: String) {
    val style = TextStyle(
        color = theme.colors.river,
        fontSize = max(13f * density, g.step * 0.28f).toSp(),
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Serif
    )
    val a = m.measure(left, style)
    val b = m.measure(right, style)
    val centerY = (g.point(0, 4).y + g.point(0, 5).y) / 2
    drawText(a, topLeft = Offset(g.leadingInset + g.step * 0.8f, centerY - a.size.height / 2f))
    drawText(b, topLeft = Offset(g.width - g.inset - g.step * 0.8f - b.size.width, centerY - b.size.height / 2f))
}

private fun DrawScope.drawCentered(m: TextMeasurer, text: String, style: TextStyle, center: Offset) {
    val layout = m.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

/** File letters below the board and rank digits down its leading margin. */
private fun DrawScope.drawCoordinates(g: BoardGeometry, theme: XiangqiTheme, m: TextMeasurer, orientation: Side) {
    val style = TextStyle(
        color = theme.colors.line.copy(alpha = 0.55f),
        fontSize = g.coordinateFontSize.toSp(),
        fontWeight = FontWeight.SemiBold
    )
    for (column in 0..8) {
        val file = if (orientation == Side.RED) column else 8 - column
        drawCentered(m, Square(file, 0).uci.take(1).uppercase(), style, Offset(g.point(column, 0).x, g.fileLabelY - g.coordinateFontSize * 0.35f))
    }
    for (row in 0..9) {
        val rank = if (orientation == Side.RED) 9 - row else row
        drawCentered(m, "$rank", style, Offset(g.rankLabelX, g.point(0, row).y))
    }
}

private fun DrawScope.drawMarker(marker: BoardMarker, g: BoardGeometry, orientation: Side, m: TextMeasurer) {
    val center = g.point(marker.square, orientation)
    val color = marker.color
    when (marker.style) {
        MarkerStyle.DOT -> drawCircle(color, g.step * 0.11f, center)
        MarkerStyle.RING -> drawCircle(color, (g.pieceSize + 6f * density) / 2, center, style = Stroke(3f * density))
        MarkerStyle.CORNERS -> {
            val side = g.pieceSize + 5f * density
            drawRoundRect(
                color, Offset(center.x - side / 2, center.y - side / 2), Size(side, side),
                androidx.compose.ui.geometry.CornerRadius(8f * density), style = Stroke(3f * density)
            )
        }
        MarkerStyle.HINT -> {
            val radius = (g.pieceSize + 9f * density) / 2
            drawCircle(
                color, radius, center,
                style = Stroke(3f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * density, 3f * density)))
            )
            drawCentered(
                m, "💡", TextStyle(fontSize = (11f * density).toSp()),
                Offset(center.x + g.pieceSize / 2, center.y - g.pieceSize / 2)
            )
        }
    }
}

private fun DrawScope.drawPiece(
    piece: Piece, center: Offset, size: Float, theme: XiangqiTheme, glyphSet: PieceGlyphSet, m: TextMeasurer
) {
    val color = if (piece.side == Side.RED) theme.colors.red else theme.colors.black
    val radius = size / 2
    drawCircle(Color.Black.copy(alpha = 0.2f), radius, center + Offset(0f, 1.5f * density))
    drawCircle(theme.colors.surface, radius, center)
    drawCircle(color, radius - theme.metrics.pieceStrokeWidth.toPx() / 2, center, style = Stroke(theme.metrics.pieceStrokeWidth.toPx()))
    drawCentered(
        m, glyphSet.glyph(piece),
        TextStyle(color = color, fontSize = (size * 0.57f).toSp(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
        center
    )
}

/** A small decorative preview of a theme, used by the theme picker. */
@Composable
fun MiniBoardPreview(theme: XiangqiTheme, selected: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(theme.cardShape(12.dp))
            .background(theme.colors.board)
            .border(
                if (selected) 2.5.dp else 1.dp,
                if (selected) theme.colors.accent else theme.border,
                theme.cardShape(12.dp)
            )
    ) {
        val lineColor = theme.colors.line.copy(alpha = 0.45f)
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(lineColor, Offset(size.width * fraction, 8f * density), Offset(size.width * fraction, size.height - 8f * density), 0.75f * density)
        }
        for (fraction in listOf(0.34f, 0.66f)) {
            drawLine(lineColor, Offset(8f * density, size.height * fraction), Offset(size.width - 8f * density, size.height * fraction), 0.75f * density)
        }
        val glyphs = listOf("車" to theme.colors.black, "帥" to theme.colors.red, "炮" to theme.colors.red)
        val diameter = min(size.height * 0.46f, size.width / 4.4f)
        val gap = max(5f * density, size.width * 0.05f)
        val total = glyphs.size * diameter + (glyphs.size - 1) * gap
        var x = (size.width - total) / 2 + diameter / 2
        for ((glyph, color) in glyphs) {
            val center = Offset(x, size.height / 2)
            drawCircle(theme.colors.surface, diameter / 2, center)
            drawCircle(color, diameter / 2 - density, center, style = Stroke(theme.metrics.pieceStrokeWidth.toPx() * 0.75f))
            drawCentered(measurer, glyph, TextStyle(color = color, fontSize = (diameter * 0.55f).toSp(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif), center)
            x += diameter + gap
        }
        if (selected) {
            drawCircle(theme.colors.accent, 8f * density, Offset(size.width - 12f * density, 12f * density))
            drawCentered(measurer, "✓", TextStyle(color = theme.colors.onAccent, fontSize = 10.sp), Offset(size.width - 12f * density, 12f * density))
        }
    }
}

/** Move helper shared by the boards. */
fun lastMoveMarkers(move: Move?, color: Color, style: MarkerStyle = MarkerStyle.CORNERS): List<BoardMarker> =
    if (move == null) emptyList() else listOf(
        BoardMarker(move.from, color.copy(alpha = 0.3f), style),
        BoardMarker(move.to, color.copy(alpha = 0.45f), style)
    )
