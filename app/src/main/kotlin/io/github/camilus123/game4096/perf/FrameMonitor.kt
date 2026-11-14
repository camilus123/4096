package io.github.camilus123.game4096.perf

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

/** Frame timing over a stretch of play. Times are in milliseconds. */
class FrameStats(
    val frames: Int,
    /** Frames that were not ready by their deadline, so the previous frame stayed on screen. */
    val slowFrames: Int,
    val medianMs: Float,
    val p90Ms: Float,
    val p99Ms: Float,
    val worstMs: Float,
) {
    val slowPercent: Float get() = if (frames == 0) 0f else slowFrames * 100f / frames
}

/**
 * Measures how long each frame of the window really took, using the platform's own
 * per-frame report (the same source as `dumpsys gfxinfo` and JankStats). The time covers
 * the whole pipeline, input to buffer handed to the compositor, including the GPU side that
 * the app's own code cannot see.
 *
 * A frame counts as slow when it took longer than its deadline: one refresh period, as
 * reported by the system on Android 12 and later, and worked out from the refresh rate
 * before that.
 *
 * The reports arrive on a private thread; nothing here runs on the main thread except the
 * [onSustainedSlowFrames] call.
 */
class FrameMonitor(private val window: Window) : Window.OnFrameMetricsAvailableListener {

    /** One refresh period in nanoseconds. Set from the display's refresh rate. */
    @Volatile var periodNanos: Long = 16_666_667L

    /**
     * While true, [onSustainedSlowFrames] is called (on the main thread) whenever more than
     * [SLOW_PERCENT_LIMIT] percent of the last [WINDOW] frames were slow.
     */
    @Volatile var watching: Boolean = false

    @Volatile var onSustainedSlowFrames: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private val lock = Any()

    // Guarded by lock.
    private val durations = LongArray(CAPACITY)
    private var stored = 0
    private var next = 0
    private var frames = 0
    private var slow = 0
    private var windowFrames = 0
    private var windowSlow = 0
    private var lastFrameId = 0L
    private var duplicates = 0

    fun start() {
        if (thread != null) return
        try {
            val t = HandlerThread("frame-metrics").apply { start() }
            window.addOnFrameMetricsAvailableListener(this, Handler(t.looper))
            thread = t
        } catch (e: RuntimeException) {
            Log.w("FrameMonitor", "Frame metrics unavailable", e)
        }
    }

    fun stop() {
        val t = thread ?: return
        thread = null
        try {
            window.removeOnFrameMetricsAvailableListener(this)
        } catch (e: RuntimeException) {
            // Was never registered.
        }
        t.quitSafely()
    }

    /** Forgets everything measured so far. */
    fun reset() {
        synchronized(lock) {
            stored = 0
            next = 0
            frames = 0
            slow = 0
            windowFrames = 0
            windowSlow = 0
            duplicates = 0
        }
    }

    /** Reports ignored since the last [reset] because they repeated a frame already counted. */
    fun duplicateCount(): Int = synchronized(lock) { duplicates }

    /** Total frames seen since the last [reset]. Cheap; for working out frames per second. */
    fun frameCount(): Int = synchronized(lock) { frames }

    override fun onFrameMetricsAvailable(window: Window, metrics: FrameMetrics, dropped: Int) {
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        record(
            metrics.getMetric(FrameMetrics.TOTAL_DURATION),
            deadlineOf(metrics),
            metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
        )
    }

    private fun deadlineOf(metrics: FrameMetrics): Long {
        if (Build.VERSION.SDK_INT >= 31) {
            val d = metrics.getMetric(FrameMetrics.DEADLINE)
            if (d > 0L) return d
        }
        return periodNanos
    }

    /**
     * Adds one frame. Visible for tests, which have no real window.
     *
     * [frameId] is the refresh the frame was meant for. Some phones report every frame twice
     * (seen on a Xiaomi with Android 16, where a 120 Hz screen appeared to draw 240 frames a
     * second); a second report for the same refresh is counted as a duplicate and ignored.
     * Pass 0 if unknown.
     */
    internal fun record(totalNanos: Long, deadlineNanos: Long, frameId: Long = 0L) {
        var notify = false
        synchronized(lock) {
            if (frameId != 0L) {
                if (frameId == lastFrameId) {
                    duplicates++
                    return
                }
                lastFrameId = frameId
            }
            durations[next] = totalNanos
            next = (next + 1) % CAPACITY
            if (stored < CAPACITY) stored++
            frames++
            windowFrames++
            if (totalNanos > deadlineNanos) {
                slow++
                windowSlow++
            }
            if (windowFrames >= WINDOW) {
                notify = watching && windowSlow * 100 > windowFrames * SLOW_PERCENT_LIMIT
                windowFrames = 0
                windowSlow = 0
            }
        }
        if (notify) {
            val callback = onSustainedSlowFrames
            if (callback != null) main.post(callback)
        }
    }

    /** Statistics since the last [reset]. Percentiles cover the most recent [CAPACITY] frames. */
    fun snapshot(): FrameStats {
        val copy: LongArray
        val total: Int
        val slowCount: Int
        synchronized(lock) {
            copy = durations.copyOf(stored)
            total = frames
            slowCount = slow
        }
        if (copy.isEmpty()) return FrameStats(0, 0, 0f, 0f, 0f, 0f)
        copy.sort()
        fun at(fraction: Double): Float = copy[((copy.size - 1) * fraction).toInt()] / 1_000_000f
        return FrameStats(total, slowCount, at(0.5), at(0.9), at(0.99), copy[copy.size - 1] / 1_000_000f)
    }

    companion object {
        const val CAPACITY = 8192
        const val WINDOW = 240
        const val SLOW_PERCENT_LIMIT = 12
    }
}
