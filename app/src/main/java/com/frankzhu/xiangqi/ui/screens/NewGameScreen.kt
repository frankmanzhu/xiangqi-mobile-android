package com.frankzhu.xiangqi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frankzhu.xiangqi.AppModel
import com.frankzhu.xiangqi.core.GameMode
import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.core.ThemeID
import com.frankzhu.xiangqi.core.TimeControl
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalLocalizer
import com.frankzhu.xiangqi.l10n.LocalizedKey
import com.frankzhu.xiangqi.l10n.titleKey
import com.frankzhu.xiangqi.ui.components.Footnote
import com.frankzhu.xiangqi.ui.components.LabeledRow
import com.frankzhu.xiangqi.ui.components.ScreenScaffold
import com.frankzhu.xiangqi.ui.components.SettingsSection
import com.frankzhu.xiangqi.ui.components.ThemeChoiceStrip
import com.frankzhu.xiangqi.ui.theme.LocalXiangqiTheme

private enum class SideChoice(val titleKey: LocalizedKey, val side: Side?) {
    RED(L10n.Side.red, Side.RED), BLACK(L10n.Side.black, Side.BLACK), RANDOM(L10n.Common.random, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(app: AppModel, mode: GameMode) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    val prefs = app.container.preferences
    var sideChoice by rememberSaveable { mutableStateOf(SideChoice.RED) }
    var level by rememberSaveable { mutableIntStateOf(2) }
    var timeControl by rememberSaveable { mutableStateOf(TimeControl.CASUAL) }
    var confirmReplacement by rememberSaveable { mutableStateOf(false) }

    fun start() {
        val humanSide = if (mode == GameMode.LOCAL_TWO_PLAYER) null else sideChoice.side ?: if (kotlin.random.Random.nextBoolean()) Side.RED else Side.BLACK
        val orientation = if (mode == GameMode.COMPUTER) humanSide ?: Side.RED else Side.RED
        app.start(
            GameRecord.create(
                mode = mode, humanSide = humanSide, computerLevel = level, timeControl = timeControl,
                orientation = orientation, theme = ThemeID(prefs.themeId.rawValue)
            )
        )
    }

    ScreenScaffold(l10n(L10n.NewGame.title), onBack = app::back) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection(null) { LabeledRow(l10n(L10n.NewGame.mode), l10n(mode.titleKey)) }
            if (mode == GameMode.COMPUTER) {
                SettingsSection(l10n(L10n.NewGame.Section.side)) {
                    Segmented(SideChoice.entries, sideChoice, { l10n(it.titleKey) }) { sideChoice = it }
                    Footnote(l10n(L10n.NewGame.redMovesFirst))
                }
                SettingsSection(l10n(L10n.NewGame.Section.strength)) {
                    Text(l10n(L10n.NewGame.level), color = colors.textSecondary, fontSize = 13.sp)
                    Segmented((1..5).toList(), level, { "$it" }) { level = it }
                    Text(l10n(levelName(level)), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Footnote(l10n(levelDetail(level)))
                }
            }
            SettingsSection(l10n(L10n.NewGame.Section.time)) {
                Segmented(TimeControl.entries, timeControl, { l10n(it.titleKey) }) { timeControl = it }
            }
            SettingsSection(l10n(L10n.NewGame.Section.theme)) {
                ThemeChoiceStrip(prefs.themeId.rawValue, { prefs.updateTheme(ThemeID(it)) }, Modifier.padding(vertical = 6.dp))
            }
            Button(
                onClick = { if (app.resumableRecord != null) confirmReplacement = true else start() },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) { Text(l10n(L10n.NewGame.start), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        }
    }

    if (confirmReplacement) {
        AlertDialog(
            onDismissRequest = { confirmReplacement = false },
            title = { Text(l10n(L10n.NewGame.Replace.title)) },
            text = { Text(l10n(L10n.NewGame.Replace.message)) },
            confirmButton = {
                TextButton(onClick = { confirmReplacement = false; start() }) { Text(l10n(L10n.NewGame.Replace.confirm), color = colors.accent) }
            },
            dismissButton = { TextButton(onClick = { confirmReplacement = false }) { Text(l10n(L10n.Common.cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.accent, activeContentColor = colors.onAccent,
                    inactiveContainerColor = colors.surface, inactiveContentColor = colors.text
                )
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

private fun levelName(level: Int) = when (level) {
    1 -> L10n.NewGame.LevelName._1
    2 -> L10n.NewGame.LevelName._2
    3 -> L10n.NewGame.LevelName._3
    4 -> L10n.NewGame.LevelName._4
    else -> L10n.NewGame.LevelName._5
}

private fun levelDetail(level: Int) = when (level) {
    1 -> L10n.NewGame.LevelDetail._1
    2 -> L10n.NewGame.LevelDetail._2
    3 -> L10n.NewGame.LevelDetail._3
    4 -> L10n.NewGame.LevelDetail._4
    else -> L10n.NewGame.LevelDetail._5
}
