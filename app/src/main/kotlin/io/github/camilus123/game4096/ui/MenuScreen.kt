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
import io.github.camilus123.game4096.core.Difficulty
import io.github.camilus123.game4096.core.GameRules
import io.github.camilus123.game4096.core.Mode
import io.github.camilus123.game4096.core.Rules
import io.github.camilus123.game4096.core.VersusConfig
import io.github.camilus123.game4096.core.VersusRule
import io.github.camilus123.game4096.game.GameViewModel

private val GRID_SIZES = intArrayOf(4, 5, 6)
private val MODE_ROWS = listOf(
    listOf(Mode.CLASSIC, Mode.ZEN),
    listOf(Mode.TIME_ATTACK, Mode.LIMITED_MOVES),
    listOf(Mode.VERSUS),
)

@Composable
fun MenuScreen(vm: GameViewModel) {
    val gridSize = vm.gridSize
    val mode = vm.mode
    val rules = vm.rules
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
            fontSize = 68.sp,
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
                    onClick = { vm.selectGrid(size) },
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
                for (m in row) {
                    Choice(
                        selected = m == mode,
                        onClick = { vm.selectMode(m) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Text(stringResource(modeNameRes(m)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(modeDescriptionRes(m)),
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        when (mode) {
            Mode.TIME_ATTACK, Mode.LIMITED_MOVES -> {
                val variants = if (mode == Mode.TIME_ATTACK) GameRules.TIME_VARIANTS else GameRules.MOVE_VARIANTS
                ChipRow {
                    for (v in variants) {
                        val option = GameRules(mode, v)
                        Chip(variantLabel(option) ?: "", v == rules.variant) { vm.selectRules(option) }
                    }
                }
            }
            Mode.VERSUS -> VersusOptions(vm)
            else -> Unit
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = vm::play,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(if (vm.canContinue()) R.string.continue_game else R.string.play),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        QuietButton(
            onClick = vm::showScores,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(text = stringResource(R.string.high_scores), fontSize = 17.sp)
        }
    }
}

@Composable
private fun VersusOptions(vm: GameViewModel) {
    val config = vm.versusConfig
    SmallLabel(stringResource(R.string.win_rule))
    ChipRow {
        for (rule in VersusRule.entries) {
            Chip(stringResource(ruleNameRes(rule)), rule == config.rule) {
                if (rule != config.rule) vm.selectVersus(config.with(rule = rule, variant = VersusConfig.defaultVariant(rule)))
            }
        }
    }
    val variants = when (config.rule) {
        VersusRule.RACE -> VersusConfig.RACE_TARGETS
        VersusRule.TIMER -> VersusConfig.TIMER_SECONDS
        VersusRule.LAST_STANDING -> null
    }
    if (variants != null) {
        ChipRow {
            for (v in variants) {
                val label = if (config.rule == VersusRule.RACE) v.toString() else stringResource(R.string.minutes, v / 60)
                Chip(label, v == config.variant) { vm.selectVersus(config.with(variant = v)) }
            }
        }
    }
    Text(
        text = ruleDescription(config),
        modifier = Modifier.padding(bottom = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )
    SmallLabel(stringResource(R.string.difficulty))
    ChipRow {
        for (d in Difficulty.entries) {
            Chip(stringResource(difficultyNameRes(d)), d == config.difficulty) {
                vm.selectVersus(config.with(difficulty = d))
            }
        }
    }
    val record = vm.versusRecord(vm.gridSize, config.difficulty)
    if (record.played > 0) {
        Text(
            text = stringResource(R.string.versus_record, record.wins, record.losses, record.draws),
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Choice(selected = selected, onClick = onClick, modifier = Modifier.weight(1f), verticalPadding = 10) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
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
