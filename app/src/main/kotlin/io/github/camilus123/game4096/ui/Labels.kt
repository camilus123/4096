package io.github.camilus123.game4096.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.camilus123.game4096.R
import io.github.camilus123.game4096.core.GameRules
import io.github.camilus123.game4096.core.Mode

fun modeNameRes(mode: Mode): Int = when (mode) {
    Mode.CLASSIC -> R.string.mode_classic
    Mode.ZEN -> R.string.mode_zen
    Mode.TIME_ATTACK -> R.string.mode_time
    Mode.LIMITED_MOVES -> R.string.mode_moves
}

fun modeDescriptionRes(mode: Mode): Int = when (mode) {
    Mode.CLASSIC -> R.string.mode_classic_desc
    Mode.ZEN -> R.string.mode_zen_desc
    Mode.TIME_ATTACK -> R.string.mode_time_desc
    Mode.LIMITED_MOVES -> R.string.mode_moves_desc
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
