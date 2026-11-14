package io.github.camilus123.game4096

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.camilus123.game4096.data.Languages
import io.github.camilus123.game4096.perf.FrameMonitor
import kotlin.math.abs
import io.github.camilus123.game4096.ui.App

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Before Android 13 the in-app language choice is applied here.
        super.attachBaseContext(Languages.wrap(newBase))
    }

    /** Real per-frame timing for this window. Read by the frame readout and the performance test. */
    lateinit var frameMonitor: FrameMonitor
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        frameMonitor = FrameMonitor(window)
        applyRefreshRate(limitTo60 = false)
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        frameMonitor.start()
    }

    override fun onStop() {
        frameMonitor.stop()
        super.onStop()
    }

    /**
     * Many phones run apps at 60 Hz unless the app asks for more. This asks the window for
     * the display mode with the highest refresh rate at the current resolution, or, with
     * [limitTo60], for the mode closest to 60 Hz (the battery saver in settings).
     */
    fun applyRefreshRate(limitTo60: Boolean) {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return
        val current = display.mode
        var best = current
        for (mode in display.supportedModes) {
            if (mode.physicalWidth != current.physicalWidth || mode.physicalHeight != current.physicalHeight) continue
            val better = if (limitTo60) {
                abs(mode.refreshRate - 60f) < abs(best.refreshRate - 60f) - 0.5f
            } else {
                mode.refreshRate > best.refreshRate + 0.5f
            }
            if (better) best = mode
        }
        val attrs = window.attributes
        // Two ways of asking for the same thing; phones differ in which they listen to. Some
        // listen to neither when the user has fixed the refresh rate in system settings, which
        // is why the board also paces itself (see BoardView.limitTo60).
        val rate = if (limitTo60) best.refreshRate else 0f
        if (attrs.preferredDisplayModeId != best.modeId || attrs.preferredRefreshRate != rate) {
            attrs.preferredDisplayModeId = best.modeId
            attrs.preferredRefreshRate = rate
            window.attributes = attrs
        }
        frameMonitor.periodNanos = (1_000_000_000.0 / best.refreshRate).toLong()
    }

    /** The refresh rate the screen is actually running at right now. */
    fun currentRefreshRate(): Float {
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay?.refreshRate ?: 60f
    }

    /** True if this screen can run faster than 60 Hz at all. */
    fun supportsHighRefreshRate(): Boolean {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return false
        return display.supportedModes.any { it.refreshRate > 61f }
    }
}
