package io.github.camilus123.game4096.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.core.Difficulty
import io.github.camilus123.game4096.core.GameRules
import io.github.camilus123.game4096.core.VersusConfig
import io.github.camilus123.game4096.core.VersusRule
import io.github.camilus123.game4096.core.Mode

fun modeNameRes(mode: Mode): Int = when (mode) {
    Mode.CLASSIC -> R.string.mode_classic
    Mode.ZEN -> R.string.mode_zen
    Mode.TIME_ATTACK -> R.string.mode_time
    Mode.LIMITED_MOVES -> R.string.mode_moves
    Mode.VERSUS -> R.string.mode_versus
}

fun modeDescriptionRes(mode: Mode): Int = when (mode) {
    Mode.CLASSIC -> R.string.mode_classic_desc
    Mode.ZEN -> R.string.mode_zen_desc
    Mode.TIME_ATTACK -> R.string.mode_time_desc
    Mode.LIMITED_MOVES -> R.string.mode_moves_desc
    Mode.VERSUS -> R.string.mode_versus_desc
}

/** "3 min", "100 moves", or null for modes without variants. */
@Composable
fun variantLabel(rules: GameRules): String? = when (rules.mode) {
    Mode.TIME_ATTACK -> stringResource(R.string.minutes, rules.variant / 60)
    Mode.LIMITED_MOVES -> stringResource(R.string.move_budget, rules.variant)
    else -> null
}

/** "Classic", "Time attack · 3 min". */
@Composable
fun rulesLabel(rules: GameRules): String {
    val name = stringResource(modeNameRes(rules.mode))
    val variant = variantLabel(rules)
    return if (variant == null) name else "$name · $variant"
}

/** 2:05 while there is plenty of time, 9.4 in the last ten seconds. */
fun formatClock(ms: Long): String {
    if (ms > 10_000L) {
        val totalSeconds = ms / 1000L
        val seconds = totalSeconds % 60L
        return "${totalSeconds / 60L}:${if (seconds < 10L) "0" else ""}$seconds"
    }
    return "${ms / 1000L}.${(ms % 1000L) / 100L}"
}

fun ruleNameRes(rule: VersusRule): Int = when (rule) {
    VersusRule.RACE -> R.string.rule_race
    VersusRule.TIMER -> R.string.rule_timer
    VersusRule.LAST_STANDING -> R.string.rule_last
}

fun difficultyNameRes(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
}

/** "First to the 512 tile wins." and its siblings. */
@Composable
fun ruleDescription(config: VersusConfig): String = when (config.rule) {
    VersusRule.RACE -> stringResource(R.string.rule_race_desc, config.variant.toString())
    VersusRule.TIMER -> stringResource(R.string.rule_timer_desc, stringResource(R.string.minutes, config.variant / 60))
    VersusRule.LAST_STANDING -> stringResource(R.string.rule_last_desc)
}

/** "Race · 512", "Timed · 3 min", "Last standing". */
@Composable
fun versusLabel(config: VersusConfig): String {
    val name = stringResource(ruleNameRes(config.rule))
    return when (config.rule) {
        VersusRule.RACE -> "$name · ${config.variant}"
        VersusRule.TIMER -> "$name · " + stringResource(R.string.minutes, config.variant / 60)
        VersusRule.LAST_STANDING -> name
    }
}
