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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.core.Outcome
import io.github.camilus123.game4096.core.Rules
import io.github.camilus123.game4096.core.VersusMatch
import io.github.camilus123.game4096.core.VersusRule
import io.github.camilus123.game4096.game.BoardPalette
import io.github.camilus123.game4096.game.BoardView
import io.github.camilus123.game4096.game.GameViewModel

/**
 * Versus AI: the opponent's live board sits small at the top with its score, the player's
 * board takes the rest of the screen.
 */
@Composable
fun VersusScreen(vm: GameViewModel, dark: Boolean, onMenu: () -> Unit) {
    val match = vm.match ?: return
    val config = match.config
    val outcome = vm.outcome
    val palette = if (dark) BoardPalette.DARK else BoardPalette.LIGHT
    val boardDescription = stringResource(R.string.board_description)
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.versus_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onBackground,
                )
                Text(
                    text = versusLabel(config) + " · " + stringResource(R.string.grid_size, match.player.size),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onBackground,
                )
            }
            QuietButton(onClick = onMenu) { Text(stringResource(R.string.menu), maxLines = 1) }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = vm::newGame, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.new_match), maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))

        // The opponent: live board on the left, its numbers on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.secondaryContainer)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AndroidView(
                modifier = Modifier.size(132.dp),
                factory = { context ->
                    BoardView(context).apply {
                        interactive = false
                        inputEnabled = false
                    }
                },
                update = { view ->
                    view.palette = palette
                    view.bind(match.bot)
                    vm.onOpponentMoved = view::showLatestMove
                },
                onRelease = { vm.onOpponentMoved = null },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.computer) + " · " +
                        stringResource(difficultyNameRes(config.difficulty)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSecondaryContainer,
                )
                Text(
                    text = vm.opponentScore.toString(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = scheme.onSecondaryContainer,
                )
                Text(
                    text = if (vm.matchStarted || outcome != Outcome.UNDECIDED) {
                        stringResource(R.string.versus_best_tile, Rules.tileValue(vm.opponentTile).toString())
                    } else {
                        stringResource(R.string.versus_waiting)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // The player's numbers, and whatever the win rule is counting.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.you), vm.score.toString())
            Pill(stringResource(R.string.best_tile), Rules.tileValue(vm.playerTile).toString())
            Spacer(Modifier.weight(1f))
            when (config.rule) {
                VersusRule.RACE -> Pill(stringResource(R.string.target), config.variant.toString())
                VersusRule.TIMER -> Pill(
                    stringResource(R.string.time),
                    formatClock(vm.displayTimeMs),
                    urgent = vm.displayTimeMs <= 10_000L,
                )
                VersusRule.LAST_STANDING -> Unit
            }
        }
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> BoardView(context) },
                update = { view ->
                    view.palette = palette
                    view.onMoved = vm::onMoved
                    view.moveGate = vm::canAcceptMove
                    view.inputEnabled = outcome == Outcome.UNDECIDED
                    view.contentDescription = boardDescription
                    view.bind(match.player)
                },
            )
            if (outcome != Outcome.UNDECIDED) {
                VersusResult(match, outcome, vm, onMenu)
            }
        }
    }
}

@Composable
private fun Pill(label: String, value: String, urgent: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant)
            .widthIn(min = 72.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label.uppercase(), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        Text(
            text = value,
            maxLines = 1,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (urgent) scheme.secondary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VersusResult(match: VersusMatch, outcome: Outcome, vm: GameViewModel, onMenu: () -> Unit) {
    val config = match.config
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(
        when (outcome) {
            Outcome.PLAYER_WON -> R.string.win_title
            Outcome.BOT_WON -> R.string.lose_title
            else -> R.string.draw_title
        },
    )
    // Why it ended, worked out from the final boards.
    val target = config.variant.toString()
    val reason = when {
        config.rule == VersusRule.TIMER && match.timeLeftMs <= 0L -> stringResource(R.string.reason_time)
        config.rule == VersusRule.RACE && outcome == Outcome.PLAYER_WON && match.player.maxExponent >= config.targetExponent ->
            stringResource(R.string.reason_reached_you, target)
        config.rule == VersusRule.RACE && outcome == Outcome.BOT_WON && match.bot.maxExponent >= config.targetExponent ->
            stringResource(R.string.reason_reached_bot, target)
        outcome == Outcome.PLAYER_WON -> stringResource(R.string.reason_stuck_bot)
        outcome == Outcome.BOT_WON -> stringResource(R.string.reason_stuck_you)
        else -> ""
    }
    val record = vm.versusRecord
    val swallow = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.background.copy(alpha = 0.92f))
            .clickable(interactionSource = swallow, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = if (outcome == Outcome.PLAYER_WON) scheme.secondary else scheme.onBackground,
            )
            if (reason.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(reason, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = scheme.onBackground)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.versus_result, vm.score.toString(), vm.opponentScore.toString()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.versus_record, record.wins, record.losses, record.draws),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = vm::newGame, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.rematch))
            }
            Spacer(Modifier.height(8.dp))
            QuietButton(onClick = onMenu) { Text(stringResource(R.string.menu)) }
        }
    }
}
