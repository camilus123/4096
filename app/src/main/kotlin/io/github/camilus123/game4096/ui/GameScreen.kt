package io.github.camilus123.game4096.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.core.EndReason
import io.github.camilus123.game4096.core.Mode
import io.github.camilus123.game4096.core.Rules
import io.github.camilus123.game4096.core.SessionStatus
import io.github.camilus123.game4096.game.BoardPalette
import io.github.camilus123.game4096.game.BoardView
import io.github.camilus123.game4096.game.GameViewModel

@Composable
fun GameScreen(vm: GameViewModel, dark: Boolean, onMenu: () -> Unit) {
    val session = vm.session ?: return
    val game = session.game
    val rules = session.rules
    val status = vm.status
    val boardVersion = vm.boardVersion
    val boardDescription = stringResource(R.string.board_description)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = rulesLabel(rules) + " · " + stringResource(R.string.grid_size, game.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Stat(stringResource(R.string.score), vm.score.toString())
            Spacer(Modifier.width(8.dp))
            Stat(stringResource(R.string.best), vm.best.toString())
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietButton(onClick = onMenu) {
                Text(stringResource(R.string.menu), maxLines = 1)
            }
            FilledTonalButton(onClick = vm::newGame, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.new_game), maxLines = 1)
            }
            if (rules.mode == Mode.ZEN) {
                FilledTonalButton(
                    onClick = vm::undo,
                    enabled = vm.canUndo,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.undo), maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            when (rules.mode) {
                Mode.TIME_ATTACK -> Counter(
                    label = stringResource(R.string.time),
                    value = formatClock(vm.displayTimeMs),
                    urgent = vm.displayTimeMs <= 10_000L,
                )
                Mode.LIMITED_MOVES -> Counter(
                    label = stringResource(R.string.moves_left),
                    value = vm.movesLeft.toString(),
                    urgent = vm.movesLeft <= 5,
                )
                Mode.ZEN -> Unit // the undo button takes this space
                else -> Counter(label = stringResource(R.string.moves), value = vm.moves.toString(), urgent = false)
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // The board takes the whole area so a swipe can start anywhere below the header.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> BoardView(context) },
                update = { view ->
                    view.palette = if (dark) BoardPalette.DARK else BoardPalette.LIGHT
                    view.onMoved = vm::onMoved
                    view.moveGate = vm::canAcceptMove
                    view.quality = vm.quality
                    view.limitTo60 = vm.settings.limitFps
                    view.inputEnabled = status == SessionStatus.PLAYING
                    view.contentDescription = boardDescription
                    view.bind(game)
                    view.showVersion(boardVersion)
                },
            )
            if (status != SessionStatus.PLAYING) {
                ResultOverlay(
                    status = status,
                    reason = vm.endReason,
                    winTile = Rules.tileValue(game.winExponent).toString(),
                    score = vm.score.toString(),
                    rank = vm.finalRank,
                    onKeepPlaying = vm::keepPlaying,
                    onUndo = vm::undo,
                    onNewGame = vm::newGame,
                )
            }
        }
        if (vm.settings.showFrameStats) FrameStatsLine(vm.quality)
    }
}

/** Small label over a number, right-aligned in the button row: moves, moves left, or the clock. */
@Composable
private fun Counter(label: String, value: String, urgent: Boolean) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label.uppercase(),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = value,
            maxLines = 1,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (urgent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .widthIn(min = 76.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultOverlay(
    status: SessionStatus,
    reason: EndReason,
    winTile: String,
    score: String,
    rank: Int,
    onKeepPlaying: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
) {
    val title: String
    val text: String
    when (status) {
        SessionStatus.WON -> {
            title = stringResource(R.string.win_title)
            text = stringResource(R.string.win_text, winTile)
        }
        SessionStatus.STUCK -> {
            title = stringResource(R.string.stuck_title)
            text = stringResource(R.string.stuck_text)
        }
        else -> {
            title = stringResource(
                when (reason) {
                    EndReason.TIME_UP -> R.string.time_up_title
                    EndReason.OUT_OF_MOVES -> R.string.out_of_moves_title
                    else -> R.string.game_over_title
                },
            )
            text = if (reason == EndReason.NO_MOVES) stringResource(R.string.game_over_text, score)
            else stringResource(R.string.final_score, score)
        }
    }
    val swallow = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            // Swallow touches so the board underneath cannot be swiped.
            .clickable(interactionSource = swallow, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (status == SessionStatus.OVER && rank >= 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (rank == 0) stringResource(R.string.new_best)
                    else stringResource(R.string.table_rank, rank + 1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(24.dp))
            when (status) {
                SessionStatus.WON -> {
                    Button(onClick = onKeepPlaying, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.keep_playing))
                    }
                    Spacer(Modifier.height(8.dp))
                    QuietButton(onClick = onNewGame) { Text(stringResource(R.string.new_game)) }
                }
                SessionStatus.STUCK -> {
                    Button(onClick = onUndo, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.undo))
                    }
                    Spacer(Modifier.height(8.dp))
                    QuietButton(onClick = onNewGame) { Text(stringResource(R.string.new_game)) }
                }
                else -> Button(onClick = onNewGame, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.try_again))
                }
            }
        }
    }
}
