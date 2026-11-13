package io.github.camilus123.game4096.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.core.Rules

private val GRID_SIZES = intArrayOf(4, 5, 6)

@Composable
fun MenuScreen(
    gridSize: Int,
    hasSavedGame: Boolean,
    onGridSize: (Int) -> Unit,
    onPlay: () -> Unit,
    onScores: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 84.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.menu_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.grid),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (size in GRID_SIZES) {
                GridChoice(
                    size = size,
                    selected = size == gridSize,
                    onClick = { onGridSize(size) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onPlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(if (hasSavedGame) R.string.continue_game else R.string.play) +
                    "  ·  " + stringResource(R.string.mode_classic),
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

@Composable
private fun GridChoice(size: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.grid_size, size),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
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
