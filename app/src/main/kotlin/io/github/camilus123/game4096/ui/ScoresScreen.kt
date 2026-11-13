package io.github.camilus123.game4096.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.core.Rules
import io.github.camilus123.game4096.core.ScoreTable
import io.github.camilus123.game4096.data.GameKey
import io.github.camilus123.game4096.game.GameViewModel
import java.text.DateFormat
import java.util.Date

private val GRID_SIZES = intArrayOf(4, 5, 6)

/** Modes that keep a score table, with their display names. Later milestones add to this. */
private val SCORED_MODES = listOf(GameKey.CLASSIC to R.string.mode_classic)

@Composable
fun ScoresScreen(vm: GameViewModel, onBack: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.high_scores),
                modifier = Modifier.weight(1f),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            QuietButton(onClick = onBack) {
                Text(stringResource(R.string.menu))
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            for (size in GRID_SIZES) {
                Text(
                    text = stringResource(R.string.grid_size, size),
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                for ((modeId, nameRes) in SCORED_MODES) {
                    ModeTable(stringResource(nameRes), vm.scores(size, modeId), dateFormat)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeTable(modeName: String, table: ScoreTable, dateFormat: DateFormat) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = modeName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant,
        )
        if (table.isEmpty) {
            Text(
                text = stringResource(R.string.no_scores),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(Modifier.padding(top = 6.dp)) {
            HeaderCell("#", Modifier.width(28.dp), TextAlign.Start)
            HeaderCell(stringResource(R.string.score), Modifier.weight(1.2f), TextAlign.Start)
            HeaderCell(stringResource(R.string.best_tile), Modifier.weight(1f), TextAlign.End)
            HeaderCell(stringResource(R.string.moves), Modifier.weight(1f), TextAlign.End)
            HeaderCell(stringResource(R.string.date), Modifier.weight(1.2f), TextAlign.End)
        }
        table.entries.forEachIndexed { index, e ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Cell((index + 1).toString(), Modifier.width(28.dp), TextAlign.Start, bold = false)
                Cell(e.score.toString(), Modifier.weight(1.2f), TextAlign.Start, bold = true)
                Cell(Rules.tileValue(e.maxExponent).toString(), Modifier.weight(1f), TextAlign.End, bold = false)
                Cell(e.moves.toString(), Modifier.weight(1f), TextAlign.End, bold = false)
                Cell(dateFormat.format(Date(e.timeMillis)), Modifier.weight(1.2f), TextAlign.End, bold = false)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        textAlign = align,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Cell(text: String, modifier: Modifier, align: TextAlign, bold: Boolean) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = align,
        maxLines = 1,
        fontSize = 16.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
