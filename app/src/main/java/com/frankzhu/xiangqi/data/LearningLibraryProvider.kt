package com.frankzhu.xiangqi.data

import android.content.Context
import com.frankzhu.xiangqi.core.CCPDLibraryException
import com.frankzhu.xiangqi.core.LearningLibraryStore
import java.io.File

/**
 * Opens the shipped CCPD corpus, copying it out of the APK on first use (SQLite
 * needs a real file), plus the user's own writable corpus, which an app update
 * never overwrites.
 */
class LearningLibraryProvider(private val context: Context) {
    @Volatile private var cached: LearningLibraryStore? = null

    fun load(): LearningLibraryStore {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val bundled = bundledDatabase()
            val userFile = File(context.filesDir, "XiangqiMobile/user-games.sqlite3")
            SqliteCCPDLibrary.createEmptyUserDatabase(userFile)
            return LearningLibraryStore(SqliteCCPDLibrary(bundled), SqliteCCPDLibrary(userFile)).also { cached = it }
        }
    }

    private fun bundledDatabase(): File {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).let {
            @Suppress("DEPRECATION") it.versionCode
        }
        val target = File(context.filesDir, "learning/ccpd-$version.sqlite3")
        if (target.isFile && target.length() > 0) return target
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".partial")
        try {
            context.assets.open("learning/ccpd.sqlite3").use { input ->
                partial.outputStream().use { input.copyTo(it, 1 shl 20) }
            }
        } catch (e: java.io.IOException) {
            partial.delete()
            throw CCPDLibraryException.DatabaseUnavailable("The bundled learning library is missing.")
        }
        check(partial.renameTo(target)) { "Could not install the learning library" }
        target.parentFile?.listFiles()?.filter { it != target }?.forEach { it.delete() }
        return target
    }
}
