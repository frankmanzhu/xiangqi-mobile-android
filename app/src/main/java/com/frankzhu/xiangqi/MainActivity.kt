package com.frankzhu.xiangqi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalLocalizer
import com.frankzhu.xiangqi.l10n.Localizer
import com.frankzhu.xiangqi.ui.screens.GameScreen
import com.frankzhu.xiangqi.ui.screens.HomeScreen
import com.frankzhu.xiangqi.ui.screens.LearningHomeScreen
import com.frankzhu.xiangqi.ui.screens.LearningLibraryScreen
import com.frankzhu.xiangqi.ui.screens.NewGameScreen
import com.frankzhu.xiangqi.ui.screens.PracticeScreen
import com.frankzhu.xiangqi.ui.screens.SettingsScreen
import com.frankzhu.xiangqi.ui.screens.StudyScreen
import com.frankzhu.xiangqi.ui.theme.ThemeRegistry
import com.frankzhu.xiangqi.ui.theme.XiangqiThemeProvider

class MainActivity : ComponentActivity() {
    private val app: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { XiangqiRoot(app) }
    }
}

@Composable
fun XiangqiRoot(app: AppModel) {
    val prefs = app.container.preferences
    val context = LocalContext.current
    val theme = ThemeRegistry.theme(prefs.themeId)
    val localizer = remember(prefs.language, context) { Localizer.load(context, prefs.language) }

    // Match system bar icons to the theme's light or dark chrome.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !theme.isDark
            isAppearanceLightNavigationBars = !theme.isDark
        }
    }

    CompositionLocalProvider(LocalLocalizer provides localizer) {
        XiangqiThemeProvider(theme) {
            val route = app.path.lastOrNull()
            BackHandler(enabled = route != null && route != AppRoute.Game && route != AppRoute.Settings) { app.back() }
            when (route) {
                null -> HomeScreen(app)
                is AppRoute.Setup -> NewGameScreen(app, route.mode)
                AppRoute.Game -> {
                    val session = app.session
                    if (session != null) GameScreen(app, session) else app.back()
                }
                AppRoute.Settings -> SettingsScreen(app)
                AppRoute.Learning -> LearningHomeScreen(app)
                is AppRoute.LearningCategory -> LearningLibraryScreen(app, route.category)
                is AppRoute.StudyRecord -> StudyScreen(app, route.id)
                is AppRoute.PracticeRecord -> PracticeScreen(app, route.id)
            }
            if (app.showRecoveryAlert) {
                AlertDialog(
                    onDismissRequest = { app.showRecoveryAlert = false },
                    title = { Text(localizer(L10n.App.Recovery.title)) },
                    text = { Text(localizer(L10n.App.Recovery.message)) },
                    confirmButton = { TextButton(onClick = { app.showRecoveryAlert = false }) { Text(localizer(L10n.Common.ok)) } }
                )
            }
        }
    }
}
