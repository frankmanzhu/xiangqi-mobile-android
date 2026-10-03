package com.frankzhu.xiangqi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalLocalizer
import com.frankzhu.xiangqi.ui.theme.LocalXiangqiTheme

/** Caps content at a comfortable width and centres it, so tablets do not stretch it edge to edge. */
fun Modifier.readableWidth(max: Int = 680): Modifier = this.widthIn(max = max.dp)

/** A themed full-screen scaffold with a back-aware title bar. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val colors = LocalXiangqiTheme.current.colors
    val l10n = LocalLocalizer.current
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().readableWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = l10n(L10n.Game.leave), tint = colors.text)
                    }
                } else {
                    Box(Modifier.size(12.dp))
                }
                Text(
                    title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                actions()
            }
            Box(Modifier.weight(1f).fillMaxWidth().readableWidth()) { content() }
        }
    }
}

/** An inset list section: an overline label above a rounded card holding [content]. */
@Composable
fun SettingsSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalXiangqiTheme.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), color = theme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Column(
            Modifier.fillMaxWidth()
                .background(theme.colors.surface, theme.cardShape())
                .border(1.dp, theme.border, theme.cardShape())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalXiangqiTheme.current.colors
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.text, modifier = Modifier.weight(1f))
        Text(value, color = colors.textSecondary)
    }
}

@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalXiangqiTheme.current.colors.textSecondary, fontSize = 13.sp, modifier = modifier)
}

@Composable
fun Divider() = HorizontalDivider(color = LocalXiangqiTheme.current.border)

/** A tappable list row with an optional icon and trailing chevron. */
@Composable
fun NavRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: () -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text)
            if (subtitle != null) Text(subtitle, color = colors.textSecondary, fontSize = 13.sp)
        }
        Text("›", color = colors.textSecondary, fontSize = 20.sp)
    }
}

@Composable
fun LoadingBox(label: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = LocalXiangqiTheme.current.colors.accent)
        Text(label, color = LocalXiangqiTheme.current.colors.textSecondary, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun UnavailableBox(icon: ImageVector, title: String, description: String?, modifier: Modifier = Modifier) {
    val colors = LocalXiangqiTheme.current.colors
    Column(
        modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(44.dp))
        Text(title, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        if (description != null) Text(description, color = colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A horizontal row of selectable theme swatches, driven by the registry. */
@Composable
fun ThemeChoiceStrip(selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val l10n = LocalLocalizer.current
    val current = LocalXiangqiTheme.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (theme in com.frankzhu.xiangqi.ui.theme.ThemeRegistry.themes) {
            val selected = selectedId == theme.id.rawValue
            val label = l10n(L10n.Theme.accessibilityLabel, l10n(theme.nameKey))
            Column(
                Modifier.weight(1f)
                    .clickable(role = Role.RadioButton) { onSelect(theme.id.rawValue) }
                    .semantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniBoardPreview(theme, selected)
                Text(
                    l10n(theme.nameKey), color = current.colors.text, fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

fun String?.nilIfEmpty(): String? = this?.trim()?.takeIf { it.isNotEmpty() }



/** Sizes [content] to the largest rectangle of the given width/height [aspect] that fits, so a board never overflows. */
@Composable
fun FitBoard(modifier: Modifier, aspect: Float = 0.87f, content: @Composable (Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * aspect)
        content(Modifier.width(width))
    }
}

/**
 * A board with a panel of controls: side by side in landscape (and on tablets), stacked in
 * portrait with the board capped to just over half the height so the panel stays reachable.
 */
@Composable
fun BoardWithPanel(
    aspect: Float,
    board: @Composable (Modifier) -> Unit,
    panel: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FitBoard(Modifier.weight(1f).fillMaxHeight(), aspect) { board(it) }
                Column(Modifier.weight(1f).fillMaxHeight(), content = panel)
            }
        } else {
            val boardCap = maxHeight * 0.55f
            Column(Modifier.fillMaxSize()) {
                FitBoard(
                    Modifier.fillMaxWidth().heightIn(max = boardCap).padding(horizontal = 16.dp, vertical = 8.dp),
                    aspect
                ) { board(it) }
                Column(Modifier.weight(1f).fillMaxWidth(), content = panel)
            }
        }
    }
}
