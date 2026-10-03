package com.frankzhu.xiangqi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frankzhu.xiangqi.core.ThemeID
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalizedKey

/** The semantic colour roles a theme must supply; screens name a role, never a literal colour. */
class ThemeColors(
    val background: Color,
    val surface: Color,
    val board: Color,
    val line: Color,
    val river: Color,
    val red: Color,
    val black: Color,
    val accent: Color,
    val onAccent: Color = Color.White,
    val legal: Color,
    val text: Color,
    val textSecondary: Color
)

/** Shape and line weights, so a theme can change its feel and not only its hues. */
class ThemeMetrics(
    val boardCornerRadius: Dp = 16.dp,
    val cardCornerRadius: Dp = 18.dp,
    val controlCornerRadius: Dp = 12.dp,
    val gridLineWidth: Dp = 1.15.dp,
    val pieceStrokeWidth: Dp = 2.dp,
    /** Piece diameter as a fraction of one grid step. */
    val pieceScale: Float = 0.82f,
    /** Opacity of the hairline that outlines cards and the board. */
    val borderOpacity: Float = 0.1f
)

/** A complete visual identity, addressed by a stable [ThemeID]. */
class XiangqiTheme(
    val id: ThemeID,
    val nameKey: LocalizedKey,
    val colors: ThemeColors,
    val metrics: ThemeMetrics = ThemeMetrics(),
    /** Forces light or dark chrome. */
    val isDark: Boolean
) {
    fun cardShape(radius: Dp? = null) = RoundedCornerShape(radius ?: metrics.cardCornerRadius)
    val boardShape get() = RoundedCornerShape(metrics.boardCornerRadius)
    val controlShape get() = RoundedCornerShape(metrics.controlCornerRadius)
    val border: Color get() = colors.line.copy(alpha = metrics.borderOpacity)
}

private fun hex(value: Long, alpha: Float = 1f) = Color(value.toInt() or 0xFF000000.toInt()).copy(alpha = alpha)

val ClassicTheme = XiangqiTheme(
    id = ThemeID.CLASSIC,
    nameKey = L10n.Theme.classic,
    colors = ThemeColors(
        background = hex(0xF6F0E4), surface = hex(0xFFF9EE), board = hex(0xE9CFA2),
        line = hex(0x604A35), river = hex(0x604A35, 0.72f), red = hex(0xA8342C), black = hex(0x24221F),
        accent = hex(0xB23A2F), legal = hex(0x166B5C), text = hex(0x2B2620), textSecondary = hex(0x2B2620, 0.62f)
    ),
    isDark = false
)

val TournamentTheme = XiangqiTheme(
    id = ThemeID.TOURNAMENT,
    nameKey = L10n.Theme.tournament,
    colors = ThemeColors(
        background = hex(0x15181C), surface = hex(0x24282E), board = hex(0x30353A),
        line = hex(0xB8BDC3), river = hex(0xB8BDC3, 0.72f), red = hex(0xEE5B62), black = hex(0xE7EAEE),
        accent = hex(0xEE5B62), legal = hex(0x6DD5B2), text = Color.White, textSecondary = hex(0xB8BDC3)
    ),
    metrics = ThemeMetrics(
        boardCornerRadius = 12.dp, cardCornerRadius = 14.dp, controlCornerRadius = 10.dp,
        gridLineWidth = 1.dp, borderOpacity = 0.18f
    ),
    isDark = true
)

val CalmTheme = XiangqiTheme(
    id = ThemeID.CALM,
    nameKey = L10n.Theme.calm,
    colors = ThemeColors(
        background = hex(0xF4F2EA), surface = hex(0xFFFEFA), board = hex(0xE5E0D2),
        line = hex(0x777064), river = hex(0x777064, 0.72f), red = hex(0xC54742), black = hex(0x263D38),
        accent = hex(0x236D60), legal = hex(0x247767), text = hex(0x193D35), textSecondary = hex(0x193D35, 0.6f)
    ),
    metrics = ThemeMetrics(
        boardCornerRadius = 22.dp, cardCornerRadius = 22.dp, controlCornerRadius = 16.dp,
        gridLineWidth = 1.dp, pieceStrokeWidth = 1.5.dp, borderOpacity = 0.08f
    ),
    isDark = false
)

/** The set of themes the app offers. A saved game naming an unknown theme still opens. */
object ThemeRegistry {
    private val storage = mutableListOf(ClassicTheme, TournamentTheme, CalmTheme)
    val themes: List<XiangqiTheme> get() = storage
    val fallback: XiangqiTheme get() = storage.first()

    fun register(theme: XiangqiTheme) {
        val index = storage.indexOfFirst { it.id == theme.id }
        if (index >= 0) storage[index] = theme else storage.add(theme)
    }

    fun theme(id: ThemeID): XiangqiTheme = storage.firstOrNull { it.id == id } ?: fallback
}

val LocalXiangqiTheme = staticCompositionLocalOf { ClassicTheme }

/** Installs [theme] and a matching Material colour scheme so stock components agree with it. */
@Composable
fun XiangqiThemeProvider(theme: XiangqiTheme, content: @Composable () -> Unit) {
    val c = theme.colors
    val scheme = if (theme.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary,
            secondaryContainer = c.surface, onSecondaryContainer = c.text, outline = c.line,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
            surfaceContainerLow = c.background, surfaceContainerLowest = c.background
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary,
            secondaryContainer = c.board.copy(alpha = 0.5f), onSecondaryContainer = c.text, outline = c.line,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
            surfaceContainerLow = c.background, surfaceContainerLowest = c.background
        )
    }
    CompositionLocalProvider(LocalXiangqiTheme provides theme) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
