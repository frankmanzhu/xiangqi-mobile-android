package com.frankzhu.xiangqi

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frankzhu.xiangqi.core.GameMode
import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.core.GameSession
import kotlinx.coroutines.launch

sealed class AppRoute {
    data class Setup(val mode: GameMode) : AppRoute()
    object Game : AppRoute()
    object Settings : AppRoute()
    object Learning : AppRoute()
    data class LearningCategory(val category: String) : AppRoute()
    data class StudyRecord(val id: String) : AppRoute()
    data class PracticeRecord(val id: String) : AppRoute()
}

/** Navigation and the one active game, shared by every screen (the Android twin of iOS `AppModel`). */
class AppModel(application: Application) : AndroidViewModel(application) {
    val container = (application as XiangqiApp).container

    /** The navigation stack above Home; empty means Home is showing. */
    val path = mutableStateListOf<AppRoute>()
    var resumableRecord by mutableStateOf<GameRecord?>(null)
        private set
    var session by mutableStateOf<GameSession?>(null)
        private set

    /** Set when a saved game failed validation on launch. */
    var showRecoveryAlert by mutableStateOf(false)

    init {
        viewModelScope.launch { loadSavedGame() }
    }

    private suspend fun loadSavedGame() {
        try {
            val record = container.repository.load()
            resumableRecord = record?.takeIf { it.isActive }
        } catch (e: Exception) {
            showRecoveryAlert = true
        }
    }

    fun navigate(route: AppRoute) { path.add(route) }

    fun back() { if (path.isNotEmpty()) path.removeAt(path.lastIndex) }

    private fun newSession(record: GameRecord) = GameSession(
        record = record,
        repository = container.repository,
        computer = container.engine,
        rules = container.engine,
        scope = viewModelScope,
        settings = { container.preferences.confirmMoves },
        feedback = container.feedback
    )

    fun start(record: GameRecord) {
        viewModelScope.launch {
            runCatching { container.repository.save(record) }
            session?.close()
            val game = newSession(record)
            session = game
            resumableRecord = record
            path.add(AppRoute.Game)
            game.startIfNeeded()
        }
    }

    fun continueGame() {
        val record = resumableRecord ?: return
        session?.close()
        val game = newSession(record)
        session = game
        path.add(AppRoute.Game)
        game.startIfNeeded()
    }

    fun leaveGame() {
        val leaving = session
        if (leaving != null) resumableRecord = leaving.record.takeIf { it.isActive }
        viewModelScope.launch {
            leaving?.pause()
            leaving?.close()
        }
        session = null
        path.clear()
    }

    fun newGameFromGame(mode: GameMode) {
        session?.cancelSearch()
        session?.close()
        session = null
        path.clear()
        path.add(AppRoute.Setup(mode))
    }
}
