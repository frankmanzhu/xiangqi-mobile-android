package com.frankzhu.xiangqi

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frankzhu.xiangqi.core.ComputerConfiguration
import com.frankzhu.xiangqi.core.GameRecord
import com.frankzhu.xiangqi.core.Position
import com.frankzhu.xiangqi.core.legalMoves
import com.frankzhu.xiangqi.engine.PikafishEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Long-session stability check, not part of the normal suite: Pikafish plays itself at the chosen
 * level for N minutes while we watch time per move, memory and device heat.
 *
 *   adb shell am instrument -w -e soakMinutes 10 -e soakLevel 5 \
 *     -e class com.frankzhu.xiangqi.EngineSoakTest com.frankzhu.xiangqimobile.test/androidx.test.runner.AndroidJUnitRunner
 *
 * The report is written to the app's external files dir (soak-report.txt) and logged under "XiangqiSoak".
 */
@RunWith(AndroidJUnit4::class)
class EngineSoakTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun temperatureCelsius(): Float {
        val battery: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    private fun thermalStatus(): Int {
        if (Build.VERSION.SDK_INT < 29) return 0
        return (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
    }

    private fun pssMb(): Long = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024L

    @Test
    fun engineSelfPlaySoak() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val minutes = args.getString("soakMinutes")?.toDoubleOrNull()
        assumeTrue("pass -e soakMinutes N to run the soak", minutes != null)
        val level = args.getString("soakLevel")?.toIntOrNull() ?: 5
        val budget = PikafishEngine.budget(level) // the full budget; the engine shortens it itself when the phone is hot
        val engine = PikafishEngine(context)
        engine.prepare()

        val report = StringBuilder()
        fun log(line: String) { Log.i("XiangqiSoak", line); report.appendLine(line) }

        val deadline = System.nanoTime() + (minutes!! * 60e9).toLong()
        val startPss = pssMb()
        val startTemp = temperatureCelsius()
        var position = Position.standard
        val moves = ArrayList<String>()
        var plies = 0; var games = 0
        var totalMs = 0L; var maxMs = 0L
        var maxPss = startPss; var maxTemp = startTemp; var maxThermal = thermalStatus()
        log("soak start: level $level (${budget} ms/move), $minutes min, PSS ${startPss} MB, battery ${startTemp} C, thermal $maxThermal")

        while (System.nanoTime() < deadline) {
            val began = System.nanoTime()
            val move = engine.chooseMove(GameRecord.STANDARD_FEN, moves, ComputerConfiguration(level, plies.toULong()))
            val ms = (System.nanoTime() - began) / 1_000_000
            assertTrue("engine move $move must be legal after $moves", move in position.legalMoves())
            moves += move.uci
            position = position.applying(move)
            plies++; totalMs += ms; if (ms > maxMs) maxMs = ms

            if (plies % 10 == 0) {
                val pss = pssMb(); val temp = temperatureCelsius(); val thermal = thermalStatus()
                if (pss > maxPss) maxPss = pss; if (temp > maxTemp) maxTemp = temp; if (thermal > maxThermal) maxThermal = thermal
                log("ply $plies game ${games + 1}: last ${ms} ms, PSS ${pss} MB, battery ${temp} C, thermal $thermal")
            }
            if (engine.result(GameRecord.STANDARD_FEN, moves) != null || moves.size >= 300) {
                games++; moves.clear(); position = Position.standard
            }
        }

        val avg = if (plies > 0) totalMs / plies else 0
        log("soak done: $plies plies in $games+ games, avg $avg ms/move, max $maxMs ms, " +
            "PSS ${startPss}->${maxPss} MB peak, battery ${startTemp}->${maxTemp} C peak, max thermal status $maxThermal")
        File(context.getExternalFilesDir(null), "soak-report.txt").writeText(report.toString())

        assertTrue("played too few plies: $plies", plies >= 5)
        // A search should take about its budget; allow generous slack for a loaded device, and for the
        // very first move which also opens the network.
        assertTrue("slowest move ${maxMs} ms is far over the ${budget} ms budget", maxMs <= budget * 2 + 6_000)
        assertTrue("average ${avg} ms is over the ${budget} ms budget", avg <= budget * 1.2 + 300)
        assertTrue("memory grew by ${maxPss - startPss} MB", maxPss - startPss <= 200)
        // SEVERE (3) after minutes of nonstop self-play is the OS asking apps to back off, and the engine does.
        // Fail only if the phone actually overheats.
        assertTrue("device reached critical thermal status ($maxThermal)", maxThermal < 4)
        assertTrue("battery reached ${maxTemp} C", maxTemp < 45f)
    }
}
