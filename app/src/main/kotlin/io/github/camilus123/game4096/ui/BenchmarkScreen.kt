package io.github.camilus123.game4096.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.camilus123.game4096.BuildConfig
import io.github.camilus123.game4096.MainActivity
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.core.Bot
import io.github.camilus123.game4096.core.Difficulty
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.game.BoardPalette
import io.github.camilus123.game4096.game.BoardView
import io.github.camilus123.game4096.game.GameViewModel
import io.github.camilus123.game4096.game.Quality
import kotlinx.coroutines.delay
import java.util.Locale

private const val PHASE_SECONDS = 10
private const val MOVE_EVERY_MS = 260L
private const val GRID = 6

/**
 * The performance test: the game plays itself on the largest grid for ten seconds at each
 * quality level while the platform's frame timing is recorded, then shows the numbers so they
 * can be copied out. Every level gets exactly the same game and the same moves.
 */
@Composable
fun BenchmarkScreen(vm: GameViewModel, dark: Boolean, onBack: () -> Unit) {
    val activity = LocalActivity.current as? MainActivity
    val scheme = MaterialTheme.colorScheme
    var run by remember { mutableIntStateOf(0) } // 0 = not started; increases with each start
    var phase by remember { mutableStateOf<Quality?>(null) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf("") }
    var board by remember { mutableStateOf<BoardView?>(null) }
    var game by remember { mutableStateOf(Game.new(GRID, 42L)) }

    LaunchedEffect(run) {
        if (run == 0 || activity == null) return@LaunchedEffect
        val monitor = activity.frameMonitor
        val lines = StringBuilder()
        lines.append(deviceLine(activity)).append('\n')
        lines.append("level   frames  fps   med   p90   p99   worst  slow\n")
        val cells = IntArray(GRID * GRID)
        for (quality in Quality.entries) {
            phase = quality
            // The same seed for the board and for the player at every level: identical work.
            var g = Game.new(GRID, 42L)
            game = g
            val player = Bot(Difficulty.EASY, 7L)
            delay(700) // let the new board settle before measuring
            monitor.reset()
            val started = System.nanoTime()
            val moves = PHASE_SECONDS * 1000 / MOVE_EVERY_MS
            for (i in 0 until moves) {
                secondsLeft = PHASE_SECONDS - (i * MOVE_EVERY_MS / 1000).toInt()
                g.copyCellsInto(cells)
                val dir = player.chooseMove(cells, GRID)
                if (dir == null) {
                    g = Game.new(GRID, 43L + i)
                    game = g
                } else {
                    board?.swipe(dir)
                }
                delay(MOVE_EVERY_MS)
            }
            val seconds = (System.nanoTime() - started) / 1e9
            val s = monitor.snapshot()
            lines.append(
                String.format(
                    Locale.US, "%-7s %-7d %-5.0f %-5.1f %-5.1f %-5.1f %-6.1f %.1f%%\n",
                    quality.name.lowercase(Locale.US), s.frames, s.frames / seconds,
                    s.medianMs, s.p90Ms, s.p99Ms, s.worstMs, s.slowPercent,
                ),
            )
        }
        phase = null
        report = lines.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.benchmark_title),
                modifier = Modifier.weight(1f),
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Black,
                color = scheme.onBackground,
            )
            QuietButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.benchmark_explanation, PHASE_SECONDS),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))

        val running = phase
        if (running != null) {
            Text(
                text = stringResource(R.string.benchmark_running, running.name, secondsLeft),
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    BoardView(context).apply {
                        // The test drives the board; fingers must not change the workload.
                        interactive = false
                        inputEnabled = true
                    }.also { board = it }
                },
                update = { view ->
                    view.palette = if (dark) BoardPalette.DARK else BoardPalette.LIGHT
                    view.quality = running ?: vm.quality
                    view.ambientWhenPassive = true
                    view.bind(game)
                },
                onRelease = { board = null },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (running == null) {
            Button(
                onClick = {
                    report = ""
                    run++
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(if (report.isEmpty()) R.string.benchmark_start else R.string.benchmark_again), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (report.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = report,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.secondaryContainer)
                    .padding(10.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = scheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.benchmark_legend),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            QuietButton(
                onClick = {
                    val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("4096 performance", report))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.benchmark_copy))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun deviceLine(activity: MainActivity): String {
    val metrics = activity.resources.displayMetrics
    return String.format(
        Locale.US, "4096 %s | %s %s | Android %s | %.0f Hz | %dx%d | %dx%d grid",
        BuildConfig.VERSION_NAME, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE,
        activity.currentRefreshRate(), metrics.widthPixels, metrics.heightPixels, GRID, GRID,
    )
}
