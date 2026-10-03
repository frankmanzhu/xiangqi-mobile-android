package com.frankzhu.xiangqi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frankzhu.xiangqi.AppModel
import com.frankzhu.xiangqi.AppRoute
import com.frankzhu.xiangqi.core.GameMode
import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalLocalizer
import com.frankzhu.xiangqi.l10n.LocalizedKey
import com.frankzhu.xiangqi.l10n.titleKey
import com.frankzhu.xiangqi.ui.components.ThemeChoiceStrip
import com.frankzhu.xiangqi.ui.components.readableWidth
import com.frankzhu.xiangqi.ui.theme.LocalXiangqiTheme

@Composable
fun HomeScreen(app: AppModel) {
    val theme = LocalXiangqiTheme.current
    val colors = theme.colors
    val l10n = LocalLocalizer.current
    val prefs = app.container.preferences

    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).readableWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(52.dp).background(colors.accent, theme.cardShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("帥", color = colors.onAccent, fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(l10n(L10n.Home.title), color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Text(l10n(L10n.Home.wordmark), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
                }
                val settingsLabel = l10n(L10n.Common.settings)
                Box(
                    Modifier.size(44.dp).background(colors.surface, CircleShape)
                        .clickable(role = Role.Button) { app.navigate(AppRoute.Settings) }
                        .semantics { contentDescription = settingsLabel },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Settings, null, tint = colors.accent) }
            }

            // Hero
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(l10n(L10n.Home.Hero.title), color = colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
                Text(l10n(L10n.Home.Hero.subtitle), color = colors.textSecondary, fontSize = 16.sp)
            }

            app.resumableRecord?.let { ContinueCard(it) { app.continueGame() } }

            // Modes
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(l10n(L10n.Home.Section.mode))
                ModeButton(L10n.Mode.computer, L10n.Home.Mode.Computer.subtitle, Icons.Filled.Memory, prominent = true) {
                    app.navigate(AppRoute.Setup(GameMode.COMPUTER))
                }
                ModeButton(L10n.Mode.localTwoPlayer, L10n.Home.Mode.TwoPlayer.subtitle, Icons.Filled.People) {
                    app.navigate(AppRoute.Setup(GameMode.LOCAL_TWO_PLAYER))
                }
                ModeButton(L10n.Home.Mode.Learn.title, L10n.Home.Mode.Learn.subtitle, Icons.Filled.School) {
                    app.navigate(AppRoute.Learning)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(l10n(L10n.Home.Section.theme))
                ThemeChoiceStrip(prefs.themeId.rawValue, { prefs.updateTheme(com.frankzhu.xiangqi.core.ThemeID(it)) })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = LocalXiangqiTheme.current.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@Composable
private fun ContinueCard(record: GameRecord, onClick: () -> Unit) {
    val theme = LocalXiangqiTheme.current
    val colors = theme.colors
    val l10n = LocalLocalizer.current
    val hint = l10n(L10n.Home.Continue.hint)
    Row(
        Modifier.fillMaxWidth()
            .background(colors.surface, theme.cardShape(22.dp))
            .border(1.dp, theme.border, theme.cardShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = hint }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(52.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = colors.onAccent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(l10n(L10n.Home.Continue.title), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(
                l10n(L10n.Home.Continue.subtitle, l10n(record.mode.titleKey), record.moves.size),
                color = colors.textSecondary, fontSize = 14.sp
            )
        }
        Icon(Icons.Filled.ChevronRight, null, tint = colors.textSecondary)
    }
}

@Composable
private fun ModeButton(
    title: LocalizedKey, subtitle: LocalizedKey, icon: ImageVector, prominent: Boolean = false, onClick: () -> Unit
) {
    val theme = LocalXiangqiTheme.current
    val colors = theme.colors
    val l10n = LocalLocalizer.current
    val foreground = if (prominent) colors.onAccent else colors.text
    val label = l10n(title)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .background(if (prominent) colors.accent else colors.surface, theme.cardShape())
            .then(if (prominent) Modifier else Modifier.border(1.dp, theme.border, theme.cardShape()))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = foreground, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(l10n(title), color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(l10n(subtitle), color = foreground.copy(alpha = 0.78f), fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = foreground.copy(alpha = 0.7f))
    }
}
