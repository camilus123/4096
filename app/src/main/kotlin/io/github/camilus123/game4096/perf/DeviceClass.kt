package io.github.camilus123.game4096.perf

import android.app.ActivityManager
import android.content.Context
import io.github.camilus123.game4096.game.Quality

/**
 * The quality level Auto starts from, judged from the phone's memory and processor count.
 * It is only a starting point: Auto steps down by itself if frames turn out to be slow.
 *
 *  - Low: phones the system itself marks as low-memory, under about 3 GB of RAM, or with
 *    four or fewer cores.
 *  - Medium: up to about 6 GB of RAM (the mid-range phones the game is tuned for).
 *  - High: everything above.
 */
object DeviceClass {

    fun detect(context: Context): Quality {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            classify(manager.isLowRamDevice, info.totalMem, Runtime.getRuntime().availableProcessors())
        } catch (e: RuntimeException) {
            Quality.MEDIUM
        }
    }

    /** Phones sold as "3 GB" report a little less, hence limits just under the round numbers. */
    fun classify(lowRamDevice: Boolean, totalMemoryBytes: Long, cores: Int): Quality {
        val gigabytes = totalMemoryBytes / 1_000_000_000.0
        return when {
            lowRamDevice || gigabytes < 2.6 || cores <= 4 -> Quality.LOW
            gigabytes < 5.6 -> Quality.MEDIUM
            else -> Quality.HIGH
        }
    }
}
