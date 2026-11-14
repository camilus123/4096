package io.github.camilus123.game4096.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.camilus123.game4096.MainActivity
import io.github.camilus123.game4096.game.Quality
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * One line of live frame timing under the board, refreshed once a second so that showing it
 * does not disturb what it measures:
 *
 * `120 Hz  118 fps  med 2.1  p99 6.3 ms  slow 0.4%  High`
 *
 * "fps" is frames actually drawn in the last second, which is low whenever the board is at
 * rest: a still board draws nothing. Judge smoothness by "slow", the share of frames that
 * missed their deadline.
 */
@Composable
fun FrameStatsLine(quality: Quality) {
    val activity = LocalActivity.current as? MainActivity ?: return
    var line by remember { mutableStateOf("") }
    LaunchedEffect(activity, quality) {
        val monitor = activity.frameMonitor
        monitor.reset()
        var lastFrames = 0
        while (true) {
            delay(1000)
            val stats = monitor.snapshot()
            val fps = stats.frames - lastFrames
            lastFrames = stats.frames
            line = String.format(
                Locale.US,
                "%.0f Hz  %d fps  med %.1f  p99 %.1f ms  slow %.1f%%  %s",
                activity.currentRefreshRate(), fps, stats.medianMs, stats.p99Ms, stats.slowPercent,
                quality.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() },
            )
        }
    }
    Text(
        text = line,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        textAlign = TextAlign.Center,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        maxLines = 1,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
