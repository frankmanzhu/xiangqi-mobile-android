package com.frankzhu.xiangqi.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.frankzhu.xiangqi.core.ThemeID
import com.frankzhu.xiangqi.l10n.AppLanguage
import com.frankzhu.xiangqi.l10n.L10n
import com.frankzhu.xiangqi.l10n.LocalizedKey

/** Which character set the piece glyphs use. Stored as a stable token, never display text. */
enum class PieceGlyphSet(val storageValue: String) {
    TRADITIONAL("traditional"),
    SIMPLIFIED("simplified");

    val titleKey: LocalizedKey
        get() = if (this == TRADITIONAL) L10n.Settings.PieceLabelsOption.traditional else L10n.Settings.PieceLabelsOption.simplified

    fun glyph(piece: com.frankzhu.xiangqi.core.Piece): String {
        val red = piece.side == com.frankzhu.xiangqi.core.Side.RED
        val simplified = this == SIMPLIFIED
        return when (piece.kind) {
            com.frankzhu.xiangqi.core.PieceKind.GENERAL -> if (red) (if (simplified) "帅" else "帥") else (if (simplified) "将" else "將")
            com.frankzhu.xiangqi.core.PieceKind.ADVISOR -> if (red) "仕" else "士"
            com.frankzhu.xiangqi.core.PieceKind.ELEPHANT -> if (red) "相" else "象"
            com.frankzhu.xiangqi.core.PieceKind.HORSE -> if (simplified) "马" else "馬"
            com.frankzhu.xiangqi.core.PieceKind.CHARIOT -> if (simplified) "车" else "車"
            com.frankzhu.xiangqi.core.PieceKind.CANNON -> "炮"
            com.frankzhu.xiangqi.core.PieceKind.SOLDIER -> if (red) "兵" else "卒"
        }
    }

    companion object {
        const val STORAGE_KEY = "pieceLabels"

        /** Builds before the typed preferences stored the English label itself. */
        fun fromStored(value: String?): PieceGlyphSet =
            if (value?.lowercase() == "simplified") SIMPLIFIED else TRADITIONAL
    }
}

/** When file and rank labels are drawn around the board. */
enum class CoordinateDisplay(val storageValue: String) {
    OFF("off"),
    RED_PERSPECTIVE("redPerspective"),
    ALWAYS("always");

    val titleKey: LocalizedKey
        get() = when (this) {
            OFF -> L10n.Common.off
            RED_PERSPECTIVE -> L10n.Settings.CoordinatesOption.redPerspective
            ALWAYS -> L10n.Common.always
        }

    /** `RED_PERSPECTIVE` labels the board only while it is viewed from red's side. */
    fun isVisible(orientation: com.frankzhu.xiangqi.core.Side): Boolean = when (this) {
        OFF -> false
        RED_PERSPECTIVE -> orientation == com.frankzhu.xiangqi.core.Side.RED
        ALWAYS -> true
    }

    companion object {
        const val STORAGE_KEY = "coordinates"
        fun fromStored(value: String?): CoordinateDisplay = when (value?.lowercase()) {
            "off" -> OFF
            "always" -> ALWAYS
            else -> RED_PERSPECTIVE
        }
    }
}

/**
 * The app's settings, backed by SharedPreferences with the same keys as the iOS
 * app. Each setting is Compose state, so screens recompose when it changes.
 */
class AppPreferences(private val store: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("xiangqi_settings", Context.MODE_PRIVATE))

    var language by mutableStateOf(AppLanguage.fromStored(store.getString(AppLanguage.STORAGE_KEY, null)))
        private set
    var themeId by mutableStateOf(ThemeID(store.getString(ThemeID.STORAGE_KEY, ThemeID.CLASSIC.rawValue)!!))
        private set
    var pieceGlyphs by mutableStateOf(PieceGlyphSet.fromStored(store.getString(PieceGlyphSet.STORAGE_KEY, null)))
        private set
    var coordinates by mutableStateOf(CoordinateDisplay.fromStored(store.getString(CoordinateDisplay.STORAGE_KEY, null)))
        private set
    var confirmMoves by mutableStateOf(store.getBoolean(CONFIRM_MOVES, false))
        private set
    var sounds by mutableStateOf(store.getBoolean(SOUNDS, true))
        private set
    var haptics by mutableStateOf(store.getBoolean(HAPTICS, true))
        private set

    fun updateLanguage(value: AppLanguage) {
        language = value; store.edit().putString(AppLanguage.STORAGE_KEY, value.storageValue).apply()
    }

    fun updateTheme(value: ThemeID) {
        themeId = value; store.edit().putString(ThemeID.STORAGE_KEY, value.rawValue).apply()
    }

    fun updatePieceGlyphs(value: PieceGlyphSet) {
        pieceGlyphs = value; store.edit().putString(PieceGlyphSet.STORAGE_KEY, value.storageValue).apply()
    }

    fun updateCoordinates(value: CoordinateDisplay) {
        coordinates = value; store.edit().putString(CoordinateDisplay.STORAGE_KEY, value.storageValue).apply()
    }

    fun updateConfirmMoves(value: Boolean) {
        confirmMoves = value; store.edit().putBoolean(CONFIRM_MOVES, value).apply()
    }

    fun updateSounds(value: Boolean) {
        sounds = value; store.edit().putBoolean(SOUNDS, value).apply()
    }

    fun updateHaptics(value: Boolean) {
        haptics = value; store.edit().putBoolean(HAPTICS, value).apply()
    }

    private companion object {
        const val CONFIRM_MOVES = "confirmMoves"
        const val SOUNDS = "sounds"
        const val HAPTICS = "haptics"
    }
}
