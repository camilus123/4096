package io.github.camilus123.game4096.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersusTest {

    private val stuck = board(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)

    private fun game(cells: IntArray, score: Long = 0, moves: Int = 1) = Game.restore(GameState(4, cells, score, moves, 1))

    private fun open(score: Long = 0, moves: Int = 1, top: Int = 3) =
        game(IntArray(16).also { it[0] = top; it[5] = 1 }, score, moves)

    private fun race(target: Int) = VersusConfig(VersusRule.RACE, target, Difficulty.EASY)
    private val timer = VersusConfig(VersusRule.TIMER, 120, Difficulty.MEDIUM)
    private val last = VersusConfig(VersusRule.LAST_STANDING, 0, Difficulty.HARD)

    @Test fun configIdsParseBack() {
        val all = listOf(race(256), race(2048), timer, last, VersusConfig.DEFAULT)
        for (c in all) assertEquals(c, VersusConfig.parse(c.id))
        assertEquals(10, race(1024).targetExponent)
        assertEquals(120_000L, timer.timeLimitMs)
        for (bad in listOf(null, "", "race", "race:300:easy", "race:512:godlike", "chess:1:easy", "last:5:hard", "timer:0:easy")) {
            assertNull(bad, VersusConfig.parse(bad))
        }
    }

    @Test fun aNewMatchGivesBothSidesTheSameRulesAndDifferentBoards() {
        for (size in ALL_SIZES) {
            val m = VersusMatch.new(VersusConfig.DEFAULT, size, 42)
            assertEquals(size, m.player.size)
            assertEquals(size, m.bot.size)
            assertEquals(m.player.winExponent, m.bot.winExponent)
            assertEquals(Outcome.UNDECIDED, m.outcome)
            assertFalse("the opponent waits for the player's first move", m.botCanMove)
            assertTrue(m.playerCanMove)
            m.player.move(Direction.entries.first { m.player.canMove(it) })
            assertTrue(m.botCanMove)
        }
        val a = VersusMatch.new(VersusConfig.DEFAULT, 4, 1)
        val pa = IntArray(16).also { a.player.copyCellsInto(it) }
        val ba = IntArray(16).also { a.bot.copyCellsInto(it) }
        assertFalse(pa.contentEquals(ba))
    }

    @Test fun raceIsWonByTheFirstToTheTarget() {
        assertEquals(Outcome.UNDECIDED, VersusMatch(race(512), open(top = 8), open(top = 8)).outcome)
        assertEquals(Outcome.PLAYER_WON, VersusMatch(race(512), open(top = 9), open(top = 8)).outcome)
        assertEquals(Outcome.BOT_WON, VersusMatch(race(512), open(top = 8), open(top = 9)).outcome)
        assertEquals(Outcome.PLAYER_WON, VersusMatch(race(256), open(top = 8), open(top = 7)).outcome)
    }

    @Test fun inARaceRunningOutOfMovesLoses() {
        assertEquals(Outcome.BOT_WON, VersusMatch(race(512), game(stuck), open()).outcome)
        assertEquals(Outcome.PLAYER_WON, VersusMatch(race(512), open(), game(stuck)).outcome)
    }

    @Test fun lastOneStandingIsLostByWhoeverGetsStuck() {
        assertEquals(Outcome.UNDECIDED, VersusMatch(last, open(), open()).outcome)
        assertEquals(Outcome.BOT_WON, VersusMatch(last, game(stuck, score = 9999), open(score = 1)).outcome)
        assertEquals(Outcome.PLAYER_WON, VersusMatch(last, open(score = 1), game(stuck, score = 9999)).outcome)
    }

    @Test fun timerIsDecidedByScoreWhenTimeRunsOut() {
        val m = VersusMatch(timer, open(score = 500), open(score = 400))
        assertTrue(m.clockRunning)
        m.advanceClock(119_000)
        assertEquals(Outcome.UNDECIDED, m.outcome)
        m.advanceClock(5_000)
        assertEquals(0L, m.timeLeftMs)
        assertEquals(Outcome.PLAYER_WON, m.outcome)
        assertFalse(m.playerCanMove)
        assertFalse(m.botCanMove)

        assertEquals(Outcome.BOT_WON, VersusMatch(timer, open(score = 1), open(score = 2), timeLeftMs = 0).outcome)
        assertEquals(Outcome.DRAW, VersusMatch(timer, open(score = 7), open(score = 7), timeLeftMs = 0).outcome)
    }

    @Test fun timerClockWaitsForThePlayersFirstMove() {
        val m = VersusMatch(timer, open(moves = 0), open(moves = 0))
        assertFalse(m.clockRunning)
        m.advanceClock(60_000)
        assertEquals(120_000L, m.timeLeftMs)
    }

    @Test fun onTheTimerAStuckSideKeepsItsScoreUntilItIsPassed() {
        // The player is stuck but ahead: the opponent gets the rest of the time to catch up.
        val ahead = VersusMatch(timer, game(stuck, score = 900), open(score = 400))
        assertEquals(Outcome.UNDECIDED, ahead.outcome)
        assertFalse(ahead.playerCanMove)
        assertTrue(ahead.botCanMove)
        // Once passed, there is nothing left to wait for.
        assertEquals(Outcome.BOT_WON, VersusMatch(timer, game(stuck, score = 900), open(score = 901)).outcome)
        assertEquals(Outcome.PLAYER_WON, VersusMatch(timer, open(score = 901), game(stuck, score = 900)).outcome)
        assertEquals(Outcome.PLAYER_WON, VersusMatch(timer, game(stuck, score = 5), game(stuck, score = 4)).outcome)
    }

    @Test fun aWholeMatchAgainstEachDifficultyReachesAResult() {
        for (d in Difficulty.entries) {
            val m = VersusMatch.new(VersusConfig(VersusRule.RACE, 256, d), 4, 11)
            val playerBot = Bot(Difficulty.MEDIUM, 1) // stands in for the human
            val bot = Bot(d, 2)
            val view = IntArray(16)
            var turns = 0
            while (!m.isDecided && turns++ < 5000) {
                if (m.playerCanMove) {
                    m.player.copyCellsInto(view)
                    playerBot.chooseMove(view, 4)?.let { m.player.move(it) }
                }
                if (m.botCanMove) {
                    m.bot.copyCellsInto(view)
                    bot.chooseMove(view, 4)?.let { m.bot.move(it) }
                }
            }
            assertTrue("no result against $d", m.isDecided)
        }
    }

    @Test fun matchSurvivesSavingAndLoading() {
        val m = VersusMatch.new(timer, 5, 3)
        repeat(20) { k ->
            Direction.entries.firstOrNull { m.player.canMove(it) }?.let { m.player.move(it) }
            if (k % 2 == 0) Direction.entries.lastOrNull { m.bot.canMove(it) }?.let { m.bot.move(it) }
        }
        m.advanceClock(33_000)
        val back = VersusMatch.decode(m.encode())
        assertNotNull(back)
        back!!
        assertEquals(m.config, back.config)
        assertEquals(87_000L, back.timeLeftMs)
        assertEquals(m.player.score, back.player.score)
        assertEquals(m.bot.score, back.bot.score)
        assertEquals(m.player.moves, back.player.moves)
        assertEquals(m.bot.moves, back.bot.moves)
        // Both boards continue exactly as they would have.
        val dir = Direction.entries.first { m.player.canMove(it) }
        m.player.move(dir); back.player.move(dir)
        val a = IntArray(25).also { m.player.copyCellsInto(it) }
        val b = IntArray(25).also { back.player.copyCellsInto(it) }
        assertTrue(a.contentEquals(b))

        val text = m.encode()
        for (cut in 0 until text.length step 3) VersusMatch.decode(text.substring(0, cut)) // never throws
        assertNull(VersusMatch.decode(null))
        assertNull(VersusMatch.decode("race:512:easy|0|x|y"))
    }

    @Test fun recordCountsWinsLossesAndDraws() {
        var r = VersusRecord()
        r = r.plus(Outcome.PLAYER_WON).plus(Outcome.PLAYER_WON).plus(Outcome.BOT_WON).plus(Outcome.DRAW).plus(Outcome.UNDECIDED)
        assertEquals(2, r.wins)
        assertEquals(1, r.losses)
        assertEquals(1, r.draws)
        assertEquals(4, r.played)
        val back = VersusRecord.decode(r.encode())
        assertEquals(2, back.wins)
        assertEquals(1, back.losses)
        assertEquals(1, back.draws)
        assertEquals(0, VersusRecord.decode("rubbish").played)
        assertEquals(0, VersusRecord.decode("1,-2,3").played)
        assertEquals(0, VersusRecord.decode(null).played)
    }
}
