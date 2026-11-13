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
import io.github.camilus123.game4096.core.Rules
import io.github.camilus123.game4096.game.BoardPalette
import io.github.camilus123.game4096.game.BoardView
import io.github.camilus123.game4096.game.GameStatus
import io.github.camilus123.game4096.game.GameViewModel

@Composable
fun GameScreen(vm: GameViewModel, dark: Boolean, onMenu: () -> Unit) {
    val game = vm.game ?: return
    val status = vm.status
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
                    text = stringResource(R.string.mode_classic) + " · " +
                        stringResource(R.string.grid_size, game.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Stat(stringResource(R.string.score), vm.score.toString())
            Spacer(Modifier.width(8.dp))
            Stat(stringResource(R.string.best), vm.best.toString())
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton(onClick = onMenu) {
                Text(stringResource(R.string.menu))
            }
            FilledTonalButton(onClick = vm::newGame, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.new_game))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.moves) + ": " + vm.moves,
                modifier = Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
                    view.inputEnabled = status == GameStatus.PLAYING
                    view.contentDescription = boardDescription
                    view.bind(game)
                },
            )
            if (status != GameStatus.PLAYING) {
                ResultOverlay(
                    status = status,
                    winTile = Rules.tileValue(game.winExponent).toString(),
                    score = vm.score.toString(),
                    rank = vm.finalRank,
                    onKeepPlaying = vm::keepPlaying,
                    onNewGame = vm::newGame,
                )
            }
        }
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
    status: GameStatus,
    winTile: String,
    score: String,
    rank: Int,
    onKeepPlaying: () -> Unit,
    onNewGame: () -> Unit,
) {
    val won = status == GameStatus.WON
    val swallow = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f))
            // Swallow touches so the board underneath cannot be swiped.
            .clickable(interactionSource = swallow, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(if (won) R.string.win_title else R.string.game_over_title),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (won) stringResource(R.string.win_text, winTile)
                else stringResource(R.string.game_over_text, score),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!won && rank >= 0) {
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
            if (won) {
                Button(onClick = onKeepPlaying, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.keep_playing))
                }
                Spacer(Modifier.height(8.dp))
                QuietButton(onClick = onNewGame) {
                    Text(stringResource(R.string.new_game))
                }
            } else {
                Button(onClick = onNewGame, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.try_again))
                }
            }
        }
    }
}
