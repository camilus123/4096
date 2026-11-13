package io.github.camilus123.game4096.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotTest {

    /** Plays one whole game (or [maxMoves] of it) and returns the game. */
    private fun playGame(difficulty: Difficulty, size: Int, seed: Long, maxMoves: Int = Int.MAX_VALUE): Game {
        val game = Game.new(size, seed)
        val bot = Bot(difficulty, seed * 31 + 7)
        val view = IntArray(size * size)
        while (game.moves < maxMoves) {
            game.copyCellsInto(view)
            val before = view.copyOf()
            val dir = bot.chooseMove(view, size) ?: break
            assertArrayEquals("the bot must not change the board it is shown", before, view)
            assertTrue("the bot chose a move that does nothing", game.move(dir) != Rules.NO_MOVE)
        }
        return game
    }

    @Test fun everyDifficultyPlaysLegalGamesToTheEndOnEveryGrid() {
        for (d in Difficulty.entries) {
            for (size in ALL_SIZES) {
                // Capped: on big grids a good bot plays for tens of thousands of moves.
                val game = playGame(d, size, seed = 3, maxMoves = 400)
                assertTrue(game.moves == 400 || game.isOver)
                assertTrue(game.score > 0)
            }
        }
    }

    @Test fun returnsNullOnlyWhenNoMoveIsPossible() {
        val stuck = board(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)
        val oneMove = board(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 4)
        for (d in Difficulty.entries) {
            val bot = Bot(d, 5)
            repeat(50) {
                assertNull(bot.chooseMove(stuck, 4))
                val dir = bot.chooseMove(oneMove, 4)
                assertNotNull(dir)
                assertTrue(Rules.canMove(oneMove, 4, dir!!))
            }
        }
    }

    @Test fun sameSeedPlaysTheSameGame() {
        for (d in Difficulty.entries) {
            val a = playGame(d, 4, seed = 9, maxMoves = 150)
            val b = playGame(d, 4, seed = 9, maxMoves = 150)
            assertEquals(a.score, b.score)
            assertEquals(a.moves, b.moves)
        }
    }

    @Test fun strongerDifficultiesScoreMore() {
        fun average(d: Difficulty, games: Int, cap: Int): Long {
            var total = 0L
            for (seed in 1L..games) total += playGame(d, 4, seed, cap).score
            return total / games
        }
        // Whole games for the two quick levels.
        val easy = average(Difficulty.EASY, 12, Int.MAX_VALUE)
        val medium = average(Difficulty.MEDIUM, 12, Int.MAX_VALUE)
        assertTrue("easy $easy should be beatable: a casual player scores a few thousand", easy in 600..4000)
        assertTrue("medium $medium vs easy $easy", medium > easy * 2)
        // Hard plays for thousands of moves, so compare how far each gets in the same number.
        val mediumEarly = average(Difficulty.MEDIUM, 4, 500)
        val hardEarly = average(Difficulty.HARD, 4, 500)
        assertTrue("hard $hardEarly vs medium $mediumEarly", hardEarly > mediumEarly)
    }

    @Test fun hardReachesBigTiles() {
        val game = playGame(Difficulty.HARD, 4, seed = 2, maxMoves = 1200)
        assertTrue("hard reached only ${Rules.tileValue(game.maxExponent)}", game.maxExponent >= 10)
    }

    @Test fun paceIsHumanAndFasterWhenStronger() {
        fun average(d: Difficulty): Long {
            val bot = Bot(d, 1)
            var total = 0L
            repeat(1000) {
                val ms = bot.nextDelayMs()
                assertTrue("$d took $ms ms", ms in 250L..1200L)
                total += ms
            }
            return total / 1000
        }
        val easy = average(Difficulty.EASY)
        val medium = average(Difficulty.MEDIUM)
        val hard = average(Difficulty.HARD)
        assertTrue(easy > medium && medium > hard)
    }
}
