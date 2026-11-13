package io.github.camilus123.game4096.core

enum class VersusRule(val id: String) {
    /** First to make the target tile wins. Running out of moves first loses. */
    RACE("race"),

    /** Highest score when the clock runs out wins. */
    TIMER("timer"),

    /** Whoever runs out of moves first loses. */
    LAST_STANDING("last");

    companion object {
        fun parse(id: String?): VersusRule? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One way to play against the computer. [variant] is the target tile value for [VersusRule.RACE]
 * (for example 1024), the number of seconds for [VersusRule.TIMER], and 0 for
 * [VersusRule.LAST_STANDING].
 */
class VersusConfig(val rule: VersusRule, val variant: Int, val difficulty: Difficulty) {

    init {
        when (rule) {
            VersusRule.RACE -> require(variant >= 8 && variant and (variant - 1) == 0) { "race target must be a tile value" }
            VersusRule.TIMER -> require(variant > 0) { "timer needs a duration" }
            VersusRule.LAST_STANDING -> require(variant == 0) { "last one standing has no variant" }
        }
    }

    val id: String get() = "${rule.id}:$variant:${difficulty.id}"

    /** Exponent of the race target, or 0 for other rules. */
    val targetExponent: Int get() = if (rule == VersusRule.RACE) Integer.numberOfTrailingZeros(variant) else 0

    val timeLimitMs: Long get() = if (rule == VersusRule.TIMER) variant * 1000L else 0L

    fun with(rule: VersusRule = this.rule, variant: Int = this.variant, difficulty: Difficulty = this.difficulty) =
        VersusConfig(rule, variant, difficulty)

    override fun equals(other: Any?): Boolean =
        other is VersusConfig && other.rule == rule && other.variant == variant && other.difficulty == difficulty

    override fun hashCode(): Int = (rule.ordinal * 31 + variant) * 31 + difficulty.ordinal
    override fun toString(): String = id

    companion object {
        val RACE_TARGETS = intArrayOf(256, 512, 1024, 2048)
        val TIMER_SECONDS = intArrayOf(120, 180, 300)

        val DEFAULT = VersusConfig(VersusRule.RACE, 512, Difficulty.EASY)

        /** The variant a rule starts with when first picked. */
        fun defaultVariant(rule: VersusRule): Int = when (rule) {
            VersusRule.RACE -> 512
            VersusRule.TIMER -> 180
            VersusRule.LAST_STANDING -> 0
        }

        fun parse(id: String?): VersusConfig? {
            val parts = id?.split(':') ?: return null
            if (parts.size != 3) return null
            val rule = VersusRule.parse(parts[0]) ?: return null
            val variant = parts[1].toIntOrNull() ?: return null
            val difficulty = Difficulty.parse(parts[2]) ?: return null
            return try {
                VersusConfig(rule, variant, difficulty)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class Outcome { UNDECIDED, PLAYER_WON, BOT_WON, DRAW }

/**
 * A match against the computer: two boards under identical rules and spawn odds, each with
 * its own random generator, plus the rule that decides the winner.
 *
 * The opponent does not move until the player has made a first move, so a match never starts
 * behind the player's back.
 */
class VersusMatch(
    val config: VersusConfig,
    val player: Game,
    val bot: Game,
    timeLeftMs: Long = config.timeLimitMs,
) {
    init {
        require(player.size == bot.size) { "both boards must be the same size" }
    }

    /** Milliseconds left under [VersusRule.TIMER]. Meaningless under other rules. */
    var timeLeftMs: Long = if (config.rule == VersusRule.TIMER) timeLeftMs.coerceIn(0L, config.timeLimitMs) else 0L
        private set

    val started: Boolean get() = player.moves > 0

    val outcome: Outcome
        get() = when (config.rule) {
            VersusRule.RACE -> {
                val target = config.targetExponent
                val playerThere = player.maxExponent >= target
                val botThere = bot.maxExponent >= target
                when {
                    playerThere && botThere -> Outcome.DRAW
                    playerThere -> Outcome.PLAYER_WON
                    botThere -> Outcome.BOT_WON
                    player.isOver -> Outcome.BOT_WON
                    bot.isOver -> Outcome.PLAYER_WON
                    else -> Outcome.UNDECIDED
                }
            }
            VersusRule.LAST_STANDING -> when {
                player.isOver && bot.isOver -> Outcome.DRAW
                player.isOver -> Outcome.BOT_WON
                bot.isOver -> Outcome.PLAYER_WON
                else -> Outcome.UNDECIDED
            }
            VersusRule.TIMER -> {
                val playerDone = player.isOver
                val botDone = bot.isOver
                when {
                    timeLeftMs <= 0L || (playerDone && botDone) -> byScore()
                    // A side that is stuck keeps its score; the match is decided once the other passes it.
                    playerDone && bot.score > player.score -> Outcome.BOT_WON
                    botDone && player.score > bot.score -> Outcome.PLAYER_WON
                    else -> Outcome.UNDECIDED
                }
            }
        }

    private fun byScore(): Outcome = when {
        player.score > bot.score -> Outcome.PLAYER_WON
        bot.score > player.score -> Outcome.BOT_WON
        else -> Outcome.DRAW
    }

    val isDecided: Boolean get() = outcome != Outcome.UNDECIDED

    val playerCanMove: Boolean get() = !isDecided && !player.isOver

    val botCanMove: Boolean get() = started && !isDecided && !bot.isOver

    val clockRunning: Boolean get() = config.rule == VersusRule.TIMER && started && !isDecided

    fun advanceClock(elapsedMs: Long) {
        if (elapsedMs > 0L && clockRunning) timeLeftMs = maxOf(0L, timeLeftMs - elapsedMs)
    }

    /** `<config id>|<time left>|<player save>|<bot save>` */
    fun encode(): String =
        config.id + "|" + timeLeftMs + "|" + SaveCodec.encode(player, false, maxHistory = 0) +
            "|" + SaveCodec.encode(bot, false, maxHistory = 0)

    companion object {
        fun new(config: VersusConfig, size: Int, seed: Long): VersusMatch {
            // Two unrelated seeds from one, so the boards do not mirror each other.
            val mixer = SeededRandom(seed)
            val win = Rules.defaultWinExponent(size)
            return VersusMatch(config, Game.new(size, mixer.nextLong(), win), Game.new(size, mixer.nextLong(), win))
        }

        /** Never throws; returns null for anything it cannot fully read. */
        fun decode(text: String?): VersusMatch? {
            val parts = text?.split('|') ?: return null
            if (parts.size != 4) return null
            val config = VersusConfig.parse(parts[0]) ?: return null
            val time = parts[1].toLongOrNull() ?: return null
            val player = SaveCodec.decode(parts[2]) ?: return null
            val bot = SaveCodec.decode(parts[3]) ?: return null
            if (player.state.size != bot.state.size) return null
            val win = Rules.defaultWinExponent(player.state.size)
            return VersusMatch(config, Game.restore(player.state, win), Game.restore(bot.state, win), time)
        }
    }
}

/** Wins, losses and draws against one difficulty on one grid. */
class VersusRecord(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {

    val played: Int get() = wins + losses + draws

    fun plus(outcome: Outcome): VersusRecord = when (outcome) {
        Outcome.PLAYER_WON -> VersusRecord(wins + 1, losses, draws)
        Outcome.BOT_WON -> VersusRecord(wins, losses + 1, draws)
        Outcome.DRAW -> VersusRecord(wins, losses, draws + 1)
        Outcome.UNDECIDED -> this
    }

    fun encode(): String = "$wins,$losses,$draws"

    companion object {
        fun decode(text: String?): VersusRecord {
            val f = text?.split(',') ?: return VersusRecord()
            if (f.size != 3) return VersusRecord()
            val w = f[0].toIntOrNull() ?: return VersusRecord()
            val l = f[1].toIntOrNull() ?: return VersusRecord()
            val d = f[2].toIntOrNull() ?: return VersusRecord()
            return if (w < 0 || l < 0 || d < 0) VersusRecord() else VersusRecord(w, l, d)
        }
    }
}
