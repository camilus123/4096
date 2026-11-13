package io.github.camilus123.game4096.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.core.GameRules
import io.github.camilus123.game4096.core.Mode
import io.github.camilus123.game4096.core.Rules

private val GRID_SIZES = intArrayOf(4, 5, 6)
private val MODE_ROWS = listOf(
    listOf(Mode.CLASSIC, Mode.ZEN),
    listOf(Mode.TIME_ATTACK, Mode.LIMITED_MOVES),
)

@Composable
fun MenuScreen(
    gridSize: Int,
    rules: GameRules,
    hasSavedGame: Boolean,
    onGridSize: (Int) -> Unit,
    onRules: (GameRules) -> Unit,
    onPlay: () -> Unit,
    onScores: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 76.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.menu_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        SectionLabel(stringResource(R.string.grid))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (size in GRID_SIZES) {
                Choice(
                    selected = size == gridSize,
                    onClick = { onGridSize(size) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.grid_size, size), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(
                            R.string.grid_goal,
                            Rules.tileValue(Rules.defaultWinExponent(size)).toString(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.mode))
        for (row in MODE_ROWS) {
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (mode in row) {
                    Choice(
                        selected = mode == rules.mode,
                        onClick = { if (mode != rules.mode) onRules(defaultRules(mode)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Text(stringResource(modeNameRes(mode)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(modeDescriptionRes(mode)),
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        val variants = when (rules.mode) {
            Mode.TIME_ATTACK -> GameRules.TIME_VARIANTS
            Mode.LIMITED_MOVES -> GameRules.MOVE_VARIANTS
            else -> null
        }
        if (variants != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (v in variants) {
                    val option = GameRules(rules.mode, v)
                    Choice(
                        selected = v == rules.variant,
                        onClick = { onRules(option) },
                        modifier = Modifier.weight(1f),
                        verticalPadding = 10,
                    ) {
                        Text(variantLabel(option) ?: "", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(if (hasSavedGame) R.string.continue_game else R.string.play),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        QuietButton(
            onClick = onScores,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(text = stringResource(R.string.high_scores), fontSize = 17.sp)
        }
    }
}

/** The rules a mode starts with when first picked: the middle duration or budget. */
private fun defaultRules(mode: Mode): GameRules = when (mode) {
    Mode.CLASSIC -> GameRules.CLASSIC
    Mode.ZEN -> GameRules.ZEN
    Mode.TIME_ATTACK -> GameRules(mode, GameRules.TIME_VARIANTS[1])
    Mode.LIMITED_MOVES -> GameRules(mode, GameRules.MOVE_VARIANTS[1])
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun Choice(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Int = 14,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primary else scheme.background,
        contentColor = if (selected) scheme.onPrimary else scheme.onBackground,
        border = BorderStroke(2.dp, if (selected) scheme.primary else scheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(vertical = verticalPadding.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}
