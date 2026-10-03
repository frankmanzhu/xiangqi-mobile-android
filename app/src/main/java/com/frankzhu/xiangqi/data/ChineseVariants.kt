package com.frankzhu.xiangqi.data

import android.content.Context
import android.icu.text.Transliterator
import android.os.Build

/**
 * Produces the Simplified and Traditional spellings of a search query, so a
 * search in either script finds records stored in the other.
 *
 * Android 10+ ships ICU's transliterator. Older releases fall back to a
 * per-character table (`assets/learning/chinese-variants.tsv`) generated from
 * that same ICU data by `GenerateChineseTableTest`.
 */
fun interface ChineseVariants {
    fun variants(value: String): List<String>

    companion object {
        fun forContext(context: Context): ChineseVariants =
            if (Build.VERSION.SDK_INT >= 29) IcuChineseVariants else TableChineseVariants.fromAssets(context.applicationContext)

        /** Identity conversion, for tests and tools that need no script folding. */
        val None = ChineseVariants { listOf(it) }
    }
}

@androidx.annotation.RequiresApi(29)
private object IcuChineseVariants : ChineseVariants {
    override fun variants(value: String): List<String> {
        val result = mutableListOf(value)
        for (id in listOf("Hans-Hant", "Hant-Hans")) {
            val converted = runCatching { Transliterator.getInstance(id).transliterate(value) }.getOrNull()
            if (converted != null && converted !in result) result += converted
        }
        return result
    }
}

internal class TableChineseVariants(private val load: () -> List<String>) : ChineseVariants {
    private val tables: Pair<Map<Char, Char>, Map<Char, Char>> by lazy {
        val toTraditional = HashMap<Char, Char>()
        val toSimplified = HashMap<Char, Char>()
        for (line in load()) {
            val parts = line.split('\t')
            if (parts.size != 3 || parts[1].length != 1 || parts[2].length != 1) continue
            if (parts[0] == "S") toTraditional[parts[1][0]] = parts[2][0] else toSimplified[parts[1][0]] = parts[2][0]
        }
        toTraditional to toSimplified
    }

    override fun variants(value: String): List<String> {
        val (toTraditional, toSimplified) = tables
        val result = mutableListOf(value)
        for (table in listOf(toTraditional, toSimplified)) {
            val converted = value.map { table[it] ?: it }.joinToString("")
            if (converted !in result) result += converted
        }
        return result
    }

    companion object {
        fun fromAssets(context: Context) = TableChineseVariants {
            runCatching {
                context.assets.open("learning/chinese-variants.tsv").bufferedReader().use { it.readLines() }
            }.getOrDefault(emptyList())
        }
    }
}
