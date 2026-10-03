package com.frankzhu.xiangqi

import android.icu.text.Transliterator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Developer tool, not a check: dumps ICU's per-character Simplified<->Traditional
 * mapping so devices older than Android 10 (which lack the transliterator API)
 * can still match either script. Run with
 * `-Pandroid.testInstrumentationRunnerArguments.generateChineseTable=true`
 * and pull `cache/chinese-variants.tsv` into `app/src/main/assets/`.
 */
@RunWith(AndroidJUnit4::class)
class GenerateChineseTableTest {
    @Test
    fun dumpTable() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("generateChineseTable") == "true")
        val toHant = Transliterator.getInstance("Hans-Hant")
        val toHans = Transliterator.getInstance("Hant-Hans")
        val lines = sortedSetOf<String>()
        for (cp in 0x3400..0x9FFF) {
            val c = String(Character.toChars(cp))
            val hant = toHant.transliterate(c)
            val hans = toHans.transliterate(c)
            if (hant != c && hant.length == 1) lines += "S\t$c\t$hant"
            if (hans != c && hans.length == 1) lines += "T\t$c\t$hans"
        }
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "chinese-variants.tsv")
            .writeText(lines.joinToString("\n") + "\n")
    }
}
