package com.frankzhu.xiangqi

import android.app.Application
import com.frankzhu.xiangqi.core.LearningProgressStore
import com.frankzhu.xiangqi.data.AppPreferences
import com.frankzhu.xiangqi.data.FileGameRepository
import com.frankzhu.xiangqi.data.LearningLibraryProvider
import com.frankzhu.xiangqi.engine.PikafishEngine
import com.frankzhu.xiangqi.feedback.FeedbackPlayer
import java.io.File

/** Process-wide singletons, created once and shared by every screen. */
class XiangqiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    private val dataDir = File(app.filesDir, "XiangqiMobile")

    val preferences = AppPreferences(app)
    val repository = FileGameRepository(File(dataDir, "active-game.json"))
    val learningProgress = LearningProgressStore(File(dataDir, "learning-progress.json"))
    val learningLibrary = LearningLibraryProvider(app)
    val engine = PikafishEngine(app)
    val feedback = FeedbackPlayer(app, preferences)
}
