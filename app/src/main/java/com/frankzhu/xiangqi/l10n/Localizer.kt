package com.frankzhu.xiangqi.l10n

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.frankzhu.xiangqi.core.CCPDCompressionException
import com.frankzhu.xiangqi.core.CCPDLibraryException
import com.frankzhu.xiangqi.core.GameMode
import com.frankzhu.xiangqi.core.GameResultReason
import com.frankzhu.xiangqi.core.GameStatus
import com.frankzhu.xiangqi.core.PieceKind
import com.frankzhu.xiangqi.core.PositionException
import com.frankzhu.xiangqi.core.Side
import com.frankzhu.xiangqi.core.TimeControl
import com.frankzhu.xiangqi.core.UnsupportedLearningSchemaException
import com.frankzhu.xiangqi.core.XiangqiPGNException
import com.frankzhu.xiangqi.engine.EngineException
import com.frankzhu.xiangqi.engine.NativeEngineException
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * A translatable string identified by a stable semantic key. [en] is the
 * compiled-in fallback used when a catalog lacks an entry, so the UI stays
 * readable rather than showing a raw key.
 */
class LocalizedKey(val key: String, val en: String, val arguments: Int = 0)

/** A language the user can pick in Settings, independently of the device language. */
enum class AppLanguage(val storageValue: String, val catalog: String?) {
    SYSTEM("system", null),
    ENGLISH("english", "en"),
    SIMPLIFIED_CHINESE("simplifiedChinese", "zh-Hans"),
    TRADITIONAL_CHINESE("traditionalChinese", "zh-Hant");

    val titleKey: LocalizedKey
        get() = when (this) {
            SYSTEM -> L10n.Settings.Language.system
            ENGLISH -> L10n.Settings.Language.english
            SIMPLIFIED_CHINESE -> L10n.Settings.Language.simplifiedChinese
            TRADITIONAL_CHINESE -> L10n.Settings.Language.traditionalChinese
        }

    /** The concrete catalog to use, resolving [SYSTEM] against the device locale. */
    fun resolved(device: Locale = Locale.getDefault()): AppLanguage = when (this) {
        SYSTEM -> when {
            device.language != "zh" -> ENGLISH
            device.script == "Hant" || device.country in setOf("TW", "HK", "MO") -> TRADITIONAL_CHINESE
            else -> SIMPLIFIED_CHINESE
        }
        else -> this
    }

    val locale: Locale
        get() = when (resolved()) {
            ENGLISH -> Locale.forLanguageTag("en")
            SIMPLIFIED_CHINESE -> Locale.forLanguageTag("zh-Hans")
            TRADITIONAL_CHINESE -> Locale.forLanguageTag("zh-Hant")
            SYSTEM -> Locale.getDefault()
        }

    companion object {
        const val STORAGE_KEY = "appLanguage"
        fun fromStored(value: String?): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/**
 * Resolves [LocalizedKey]s against one explicitly chosen language. Android's
 * resource system follows the device language, so an in-app picker needs this
 * explicit catalog to take effect without restarting the app.
 */
class Localizer(val language: AppLanguage, private val catalog: Map<String, String>) {
    val locale: Locale = language.locale

    operator fun invoke(key: LocalizedKey): String = catalog[key.key] ?: key.en

    operator fun invoke(key: LocalizedKey, vararg arguments: Any?): String {
        val template = invoke(key)
        return if (arguments.isEmpty()) template else String.format(locale, template, *arguments)
    }

    companion object {
        private val json = Json
        private val cache = HashMap<String, Map<String, String>>()

        /** Loads (and caches) the catalog for [language] from the app's assets. */
        fun load(context: Context, language: AppLanguage): Localizer {
            val resolved = language.resolved()
            val code = resolved.catalog ?: "en"
            val catalog = synchronized(cache) {
                cache.getOrPut(code) {
                    runCatching {
                        context.assets.open("l10n/$code.json").bufferedReader().use {
                            json.decodeFromString<Map<String, String>>(it.readText())
                        }
                    }.getOrDefault(emptyMap())
                }
            }
            return Localizer(language, catalog)
        }

        /** An English-only localizer used before assets are available (previews, tests). */
        val English = Localizer(AppLanguage.ENGLISH, emptyMap())
    }
}

val LocalLocalizer = compositionLocalOf { Localizer.English }

// region Display names for domain values — the domain stays free of presentation.

val Side.titleKey: LocalizedKey
    get() = if (this == Side.RED) L10n.Side.red else L10n.Side.black

val PieceKind.titleKey: LocalizedKey
    get() = when (this) {
        PieceKind.GENERAL -> L10n.Piece.general
        PieceKind.ADVISOR -> L10n.Piece.advisor
        PieceKind.ELEPHANT -> L10n.Piece.elephant
        PieceKind.HORSE -> L10n.Piece.horse
        PieceKind.CHARIOT -> L10n.Piece.chariot
        PieceKind.CANNON -> L10n.Piece.cannon
        PieceKind.SOLDIER -> L10n.Piece.soldier
    }

val GameMode.titleKey: LocalizedKey
    get() = if (this == GameMode.COMPUTER) L10n.Mode.computer else L10n.Mode.localTwoPlayer

val TimeControl.titleKey: LocalizedKey
    get() = when (this) {
        TimeControl.CASUAL -> L10n.TimeControl.casual
        TimeControl.TEN_MINUTES -> L10n.TimeControl.tenMinutes
        TimeControl.FIFTEEN_MINUTES -> L10n.TimeControl.fifteenMinutes
    }

val GameResultReason.titleKey: LocalizedKey
    get() = when (this) {
        GameResultReason.CHECKMATE -> L10n.Reason.checkmate
        GameResultReason.STALEMATE -> L10n.Reason.stalemate
        GameResultReason.RESIGNATION -> L10n.Reason.resignation
        GameResultReason.TIME_LOSS -> L10n.Reason.timeLoss
        GameResultReason.REPETITION -> L10n.Reason.repetition
        GameResultReason.RULES_ADJUDICATION -> L10n.Reason.rulesAdjudication
    }

fun GameStatus.text(l10n: Localizer): String = when (this) {
    is GameStatus.Win -> l10n(L10n.Game.Status.wins, l10n(winner.titleKey), l10n(reason.titleKey))
    is GameStatus.Draw -> l10n(L10n.Game.Status.draw, l10n(reason.titleKey))
    is GameStatus.Reviewing -> l10n(L10n.Game.Status.reviewing, ply, total)
    is GameStatus.Check -> l10n(L10n.Game.Status.check, l10n(side.titleKey))
    GameStatus.Thinking -> l10n(L10n.Game.Status.thinking)
    is GameStatus.SideToMove -> l10n(L10n.Game.Status.sideToMove, l10n(side.titleKey))
    GameStatus.YourMove -> l10n(L10n.Game.Status.yourMove)
    GameStatus.ComputerToMove -> l10n(L10n.Game.Status.computerToMove)
}

// endregion

/**
 * A failure translated into something a player can read. Thrown errors carry
 * developer detail (FEN fragments, SQLite messages) that is meaningless on
 * screen; this maps each failure to a catalog key and keeps the detail for the log.
 */
class UserFacingError private constructor(val key: LocalizedKey, val diagnostic: String) {
    constructor(key: LocalizedKey) : this(key, key.key)
    constructor(error: Throwable) : this(keyFor(error), error.toString()) {
        android.util.Log.e("XiangqiErrors", error.toString(), error)
    }

    fun text(l10n: Localizer): String = l10n(key)

    override fun equals(other: Any?) = other is UserFacingError && other.key.key == key.key
    override fun hashCode() = key.key.hashCode()

    companion object {
        private fun keyFor(error: Throwable): LocalizedKey = when (error) {
            is CCPDLibraryException.DatabaseUnavailable -> L10n.Error.Library.unavailable
            is CCPDLibraryException.UnsupportedSchema -> L10n.Error.Library.unsupportedSchema
            is CCPDLibraryException.CorruptRecord, is CCPDCompressionException -> L10n.Error.Library.corruptRecord
            is UnsupportedLearningSchemaException -> L10n.Error.Progress.unsupportedSchema
            is PositionException, is XiangqiPGNException -> L10n.Error.Position.invalid
            is EngineException.NetworkMissing -> L10n.Error.Engine.networkMissing
            is EngineException.NetworkInvalid -> L10n.Error.Engine.networkInvalid
            is EngineException.InvalidMove -> L10n.Error.Engine.invalidMove
            is EngineException, is NativeEngineException, is kotlinx.coroutines.CancellationException ->
                L10n.Error.Engine.unavailable
            else -> L10n.Error.generic
        }
    }
}
