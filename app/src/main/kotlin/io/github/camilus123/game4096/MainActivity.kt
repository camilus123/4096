package io.github.camilus123.game4096

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.camilus123.game4096.data.Languages
import io.github.camilus123.game4096.ui.App

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Before Android 13 the in-app language choice is applied here.
        super.attachBaseContext(Languages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        setContent { App() }
    }

    /**
     * Many phones run apps at 60 Hz unless the app asks for more. Pick the display mode with
     * the highest refresh rate at the current resolution and ask the window to use it.
     */
    private fun requestHighestRefreshRate() {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return
        val current = display.mode
        var best = current
        for (mode in display.supportedModes) {
            if (mode.physicalWidth == current.physicalWidth &&
                mode.physicalHeight == current.physicalHeight &&
                mode.refreshRate > best.refreshRate + 0.5f
            ) {
                best = mode
            }
        }
        val attrs = window.attributes
        attrs.preferredDisplayModeId = best.modeId
        window.attributes = attrs
    }
}
