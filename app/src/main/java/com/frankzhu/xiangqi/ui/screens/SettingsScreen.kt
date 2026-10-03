package com.frankzhu.xiangqi.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import com.frankzhu.xiangqi.AppModel
import com.frankzhu.xiangqi.core.FeedbackEvent
import com.frankzhu.xiangqi.core.ThemeID
import com.frankzhu.xiangqi.data.CoordinateDisplay
import com.frankzhu.xiangqi.data.PieceGlyphSet
import com.frankzhu.xiangqi.engine.PikafishEngine
import com.frankzhu.xiangqi.l10n.AppLanguage
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalLocalizer
import com.frankzhu.xiangqi.l10n.LocalizedKey
import com.frankzhu.xiangqi.ui.components.Divider
import com.frankzhu.xiangqi.ui.components.Footnote
import com.frankzhu.xiangqi.ui.components.LabeledRow
import com.frankzhu.xiangqi.ui.components.NavRow
import com.frankzhu.xiangqi.ui.components.ScreenScaffold
import com.frankzhu.xiangqi.ui.components.SettingsSection
import com.frankzhu.xiangqi.ui.theme.LocalXiangqiTheme
import com.frankzhu.xiangqi.ui.theme.ThemeRegistry

private const val SOURCE_URL = "https://github.com/frankmanzhu/xiangqi-mobile-android"
private const val ISSUES_URL = "https://github.com/frankmanzhu/xiangqi-mobile-android/issues"

private enum class SettingsPage { Main, HowToPlay, Licenses, Privacy }

@Composable
fun SettingsScreen(app: AppModel) {
    val l10n = LocalLocalizer.current
    var page by remember { mutableStateOf(SettingsPage.Main) }
    val back = { if (page == SettingsPage.Main) app.back() else page = SettingsPage.Main }
    androidx.activity.compose.BackHandler(enabled = page != SettingsPage.Main) { page = SettingsPage.Main }
    when (page) {
        SettingsPage.Main -> SettingsMain(app, back) { page = it }
        SettingsPage.HowToPlay -> RulesHelp(back)
        SettingsPage.Licenses -> Licenses(back)
        SettingsPage.Privacy -> PrivacyPolicy(back)
    }
}

@Composable
private fun SettingsMain(app: AppModel, onBack: () -> Unit, open: (SettingsPage) -> Unit) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    val prefs = app.container.preferences
    val context = LocalContext.current

    ScreenScaffold(l10n(L10n.Settings.title), onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection(l10n(L10n.Settings.Section.language)) {
                ChoiceGroup(
                    l10n(L10n.Settings.appLanguage), AppLanguage.entries, prefs.language,
                    { l10n(it.titleKey) }, prefs::updateLanguage
                )
            }
            SettingsSection(l10n(L10n.Settings.Section.appearance)) {
                ChoiceGroup(
                    l10n(L10n.Settings.theme), ThemeRegistry.themes.map { it.id }, prefs.themeId,
                    { l10n(ThemeRegistry.theme(it).nameKey) }, prefs::updateTheme
                )
                Divider()
                ChoiceGroup(
                    l10n(L10n.Settings.pieceLabels), PieceGlyphSet.entries, prefs.pieceGlyphs,
                    { l10n(it.titleKey) }, prefs::updatePieceGlyphs
                )
                Divider()
                ChoiceGroup(
                    l10n(L10n.Settings.coordinates), CoordinateDisplay.entries, prefs.coordinates,
                    { l10n(it.titleKey) }, prefs::updateCoordinates
                )
            }
            SettingsSection(l10n(L10n.Settings.Section.interaction)) {
                ToggleRow(l10n(L10n.Settings.confirmMoves), null, prefs.confirmMoves, prefs::updateConfirmMoves)
                ToggleRow(l10n(L10n.Settings.sounds), l10n(L10n.Settings.soundsPreviewHint), prefs.sounds) { on ->
                    prefs.updateSounds(on)
                    // Playing a cue on the way on lets the setting be judged here rather than in a game.
                    if (on) app.container.feedback.apply { prepare(); play(FeedbackEvent.MOVE) }
                }
                ToggleRow(l10n(L10n.Settings.haptics), null, prefs.haptics) { on ->
                    prefs.updateHaptics(on)
                    if (on) app.container.feedback.play(FeedbackEvent.CAPTURE)
                }
            }
            SettingsSection(l10n(L10n.Settings.Section.rules)) {
                NavRow(l10n(L10n.Settings.howToPlay)) { open(SettingsPage.HowToPlay) }
                Divider()
                LabeledRow(l10n(L10n.Settings.moveRecord), "UCI")
                LabeledRow(l10n(L10n.Settings.rulesPolicy), l10n(L10n.Settings.rulesPolicyName))
                Footnote(l10n(L10n.Settings.rulesPolicyExplanation))
            }
            SettingsSection(l10n(L10n.Settings.Section.about)) {
                LabeledRow(l10n(L10n.Settings.version), "1.0")
                LabeledRow(l10n(L10n.Settings.computer), "Pikafish")
                Divider()
                NavRow(l10n(L10n.Settings.licenses)) { open(SettingsPage.Licenses) }
                Divider()
                NavRow(l10n(L10n.Privacy.title)) { open(SettingsPage.Privacy) }
                Divider()
                NavRow(l10n(L10n.Settings.sourceCode)) { openUrl(context, SOURCE_URL) }
                Divider()
                NavRow(l10n(L10n.Settings.reportIssue)) { openUrl(context, ISSUES_URL) }
                Footnote(l10n(L10n.Settings.engineNote))
            }
        }
    }
}

@Composable
private fun <T> ChoiceGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    Column {
        Text(title, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        for (option in options) {
            Row(
                Modifier.fillMaxWidth().selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option == selected, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary)
                )
                Text(label(option), color = colors.text, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, caption: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalXiangqiTheme.current.colors
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text)
            if (caption != null) Text(caption, color = colors.textSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent)
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun RulesHelp(onBack: () -> Unit) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    ScreenScaffold(l10n(L10n.Rules.title), onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection(l10n(L10n.Rules.Section.goal)) { Text(l10n(L10n.Rules.goal), color = colors.text) }
            SettingsSection(l10n(L10n.Rules.Section.pieces)) {
                val pieces = listOf(
                    L10n.Piece.chariot to L10n.Rules.chariot, L10n.Piece.horse to L10n.Rules.horse,
                    L10n.Piece.cannon to L10n.Rules.cannon, L10n.Piece.elephant to L10n.Rules.elephant,
                    L10n.Piece.advisor to L10n.Rules.advisor, L10n.Piece.general to L10n.Rules.general,
                    L10n.Piece.soldier to L10n.Rules.soldier
                )
                pieces.forEachIndexed { index, (name, text) ->
                    if (index > 0) Divider()
                    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(l10n(name), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(l10n(text), color = colors.textSecondary)
                    }
                }
            }
            SettingsSection(l10n(L10n.Rules.Section.notation)) { Text(l10n(L10n.Rules.notation), color = colors.text) }
        }
    }
}

@Composable
private fun PrivacyPolicy(onBack: () -> Unit) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    val context = LocalContext.current
    ScreenScaffold(l10n(L10n.Privacy.title), onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection(null) {
                Text(l10n(L10n.Privacy.summary), color = colors.text)
                Footnote(l10n(L10n.Privacy.updated))
            }
            SettingsSection(l10n(L10n.Privacy.Section.storage)) { Text(l10n(L10n.Privacy.storage), color = colors.text) }
            SettingsSection(l10n(L10n.Privacy.Section.sharing)) { Text(l10n(L10n.Privacy.sharing), color = colors.text) }
            SettingsSection(l10n(L10n.Privacy.Section.retention)) { Text(l10n(L10n.Privacy.retention), color = colors.text) }
            SettingsSection(l10n(L10n.Privacy.Section.contact)) {
                Text(l10n(L10n.Privacy.contact), color = colors.text)
                NavRow(l10n(L10n.Settings.reportIssue)) { openUrl(context, ISSUES_URL) }
            }
        }
    }
}

@Composable
private fun Licenses(onBack: () -> Unit) {
    val l10n = LocalLocalizer.current
    val colors = LocalXiangqiTheme.current.colors
    val context = LocalContext.current
    fun asset(name: String): String? =
        runCatching { context.assets.open("licenses/$name.txt").bufferedReader().use { it.readText() } }.getOrNull()

    ScreenScaffold(l10n(L10n.Licenses.title), onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection(l10n(L10n.Licenses.Section.learning)) {
                Text(l10n(L10n.Licenses.Ccpd.name), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(l10n(L10n.Licenses.Ccpd.authors), color = colors.text)
                Text(l10n(L10n.Licenses.Ccpd.license), color = colors.textSecondary)
                Footnote(l10n(L10n.Licenses.Ccpd.note))
                NavRow(l10n(L10n.Licenses.Ccpd.sourceLink)) { openUrl(context, "https://github.com/Yvonne761/Chinese-Chess-Practical-Dataset") }
                NavRow(l10n(L10n.Licenses.Ccpd.licenseLink)) { openUrl(context, "https://creativecommons.org/licenses/by/4.0/legalcode") }
                asset("CCPD-CC-BY-4.0")?.let { Disclosure(l10n(L10n.Licenses.bundledNotice), it, mono = true) }
            }
            SettingsSection(l10n(L10n.Licenses.Section.engine)) {
                Footnote(l10n(L10n.Licenses.Engine.note))
                NavRow(l10n(L10n.Licenses.Engine.source)) { openUrl(context, "https://github.com/official-pikafish/Pikafish/tree/${PikafishEngine.REVISION}") }
                Footnote(l10n(L10n.Licenses.Engine.nnue))
                NavRow(l10n(L10n.Licenses.Engine.nnueTerms)) { openUrl(context, "https://www.pikafish.com/list.html?lang=zh-CN") }
                asset("Pikafish-AUTHORS")?.let { Disclosure(l10n(L10n.Licenses.Engine.authors), it) }
                asset("Pikafish-NNUE-NOTICE")?.let { Disclosure(l10n(L10n.Licenses.bundledNotice), it) }
                asset("Pikafish-GPL-3.0")?.let { Disclosure(l10n(L10n.Licenses.gpl), it, mono = true) }
            }
        }
    }
}

@Composable
private fun Disclosure(title: String, body: String, mono: Boolean = false) {
    val colors = LocalXiangqiTheme.current.colors
    var open by remember { mutableStateOf(false) }
    Column {
        Text(
            (if (open) "▾ " else "▸ ") + title, color = colors.accent, fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(vertical = 10.dp)
        )
        if (open) {
            SelectionContainer {
                Text(body, color = colors.textSecondary, fontSize = 12.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
            }
        }
    }
}
