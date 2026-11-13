package io.github.camilus123.game4096.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {

    private fun Game.cells(): IntArray = IntArray(cellCount).also { copyCellsInto(it) }

    /** Plays the first legal direction (in a seeded random order) until the game ends or [maxMoves]. */
    private fun playOut(game: Game, picker: SeededRandom, maxMoves: Int = Int.MAX_VALUE) {
        val dirs = Direction.entries
        while (!game.isOver && game.moves < maxMoves) {
            val first = picker.nextInt(4)
            for (k in 0 until 4) {
                if (game.move(dirs[(first + k) % 4]) != Rules.NO_MOVE) break
            }
        }
    }

    @Test fun newGameStartsWithTwoTiles() {
        for (size in ALL_SIZES) {
            for (seed in 0L until 200L) {
                val g = Game.new(size, seed)
                val cells = g.cells()
                assertEquals(size * size - 2, Rules.countEmpty(cells))
                assertTrue(cells.all { it in 0..2 })
                assertEquals(0L, g.score)
                assertEquals(0, g.moves)
                assertFalse(g.isOver)
                assertFalse(g.hasWon)
                assertFalse(g.canUndo)
            }
        }
    }

    @Test fun sameSeedGivesTheSameGame() {
        for (size in ALL_SIZES) {
            val a = Game.new(size, 42)
            val b = Game.new(size, 42)
            val pa = SeededRandom(1)
            val pb = SeededRandom(1)
            playOut(a, pa, 300)
            playOut(b, pb, 300)
            assertArrayEquals(a.cells(), b.cells())
            assertEquals(a.score, b.score)
            assertEquals(a.moves, b.moves)
            assertNotEquals(Game.new(size, 43).cells().toList(), Game.new(size, 44).cells().toList())
        }
    }

    @Test fun aMoveSpawnsExactlyOneTileAndCountsTheScore() {
        for (size in ALL_SIZES) {
            val g = Game.new(size, 5)
            val picker = SeededRandom(8)
            val expected = IntArray(size * size)
            var total = 0L
            repeat(400) {
                if (g.isOver) return@repeat
                val dir = Direction.entries[picker.nextInt(4)]
                val before = g.cells()
                val movesBefore = g.moves
                val gain = Rules.move(before, size, dir, expected)
                val reported = g.move(dir)
                assertEquals(gain, reported)
                val after = g.cells()
                if (gain == Rules.NO_MOVE) {
                    assertArrayEquals(before, after) // nothing spawns after a swipe that did nothing
                    assertEquals(movesBefore, g.moves)
                } else {
                    total += gain
                    val spawn = g.lastSpawnIndex
                    assertEquals(0, expected[spawn])
                    assertTrue(after[spawn] == 1 || after[spawn] == 2)
                    expected[spawn] = after[spawn]
                    assertArrayEquals(expected, after)
                    assertEquals(movesBefore + 1, g.moves)
                }
                assertEquals(total, g.score)
            }
        }
    }

    @Test fun everyGameEndsAndEndsStuck() {
        for (size in ALL_SIZES) {
            for (seed in 1L..20L) {
                val g = Game.new(size, seed)
                playOut(g, SeededRandom(seed * 31))
                assertTrue(g.isOver)
                assertEquals(0, Rules.countEmpty(g.cells()))
                for (dir in Direction.entries) {
                    assertFalse(g.canMove(dir))
                    assertEquals(Rules.NO_MOVE, g.move(dir))
                }
                assertTrue(g.score > 0)
            }
        }
    }

    @Test fun undoIsOffByDefault() {
        val g = Game.new(4, 1)
        playOut(g, SeededRandom(2), 10)
        assertFalse(g.canUndo)
        assertFalse(g.undo())
    }

    @Test fun undoRestoresBoardScoreAndMoveCount() {
        for (size in ALL_SIZES) {
            val g = Game.new(size, 9, undoLimit = Game.UNLIMITED_UNDO)
            val picker = SeededRandom(4)
            val boards = ArrayList<IntArray>()
            val scores = ArrayList<Long>()
            while (g.moves < 150 && !g.isOver) {
                val before = g.cells()
                val scoreBefore = g.score
                if (g.move(Direction.entries[picker.nextInt(4)]) != Rules.NO_MOVE) {
                    boards.add(before)
                    scores.add(scoreBefore)
                }
            }
            assertEquals(boards.size, g.undoDepth)
            for (i in boards.indices.reversed()) {
                assertTrue(g.undo())
                assertArrayEquals(boards[i], g.cells())
                assertEquals(scores[i], g.score)
                assertEquals(i, g.moves)
            }
            assertFalse(g.canUndo)
            assertFalse(g.undo())
        }
    }

    @Test fun undoThenTheSameMoveGivesTheSameTile() {
        val g = Game.new(5, 77, undoLimit = Game.UNLIMITED_UNDO)
        playOut(g, SeededRandom(6), 40)
        val dir = Direction.entries.first { g.canMove(it) }
        g.move(dir)
        val first = g.cells()
        assertTrue(g.undo())
        g.move(dir)
        assertArrayEquals(first, g.cells())
    }

    @Test fun undoLimitKeepsOnlyTheMostRecentMoves() {
        val g = Game.new(4, 3, undoLimit = 3)
        playOut(g, SeededRandom(5), 20)
        assertEquals(3, g.undoDepth)
        assertTrue(g.undo())
        assertTrue(g.undo())
        assertTrue(g.undo())
        assertFalse(g.undo())
        assertEquals(17, g.moves)
    }

    @Test fun undoWorksAfterGameOver() {
        val g = Game.new(4, 12, undoLimit = Game.UNLIMITED_UNDO)
        playOut(g, SeededRandom(12))
        assertTrue(g.isOver)
        assertTrue(g.undo())
        assertFalse(g.isOver)
    }

    @Test fun winIsDetectedAtTheTargetTile() {
        val almost = IntArray(16).also { it[0] = 10; it[1] = 10; it[15] = 1 }
        val g = Game.restore(GameState(4, almost, 0L, 0, 1L))
        assertFalse(g.hasWon)
        assertEquals(2048L, g.move(Direction.LEFT))
        assertTrue(g.hasWon)
        assertFalse(g.isOver) // "keep playing" is allowed
        assertEquals(2048L, g.score)

        val small = Game.restore(GameState(4, almost.copyOf(), 0L, 0, 1L), winExponent = 12)
        small.move(Direction.LEFT)
        assertFalse(small.hasWon)
    }

    @Test fun saveAndRestoreContinuesIdentically() {
        for (size in ALL_SIZES) {
            val original = Game.new(size, 21, undoLimit = 5)
            playOut(original, SeededRandom(3), 60)
            val copy = Game.restore(
                original.snapshot(),
                undoLimit = 5,
                history = original.historySnapshot(),
            )
            assertArrayEquals(original.cells(), copy.cells())
            assertEquals(original.score, copy.score)
            assertEquals(original.moves, copy.moves)
            assertEquals(original.undoDepth, copy.undoDepth)

            playOut(original, SeededRandom(9), 120)
            playOut(copy, SeededRandom(9), 120)
            assertArrayEquals(original.cells(), copy.cells())
            assertEquals(original.score, copy.score)

            repeat(5) {
                assertTrue(original.undo())
                assertTrue(copy.undo())
                assertArrayEquals(original.cells(), copy.cells())
            }
        }
    }

    @Test fun snapshotsDoNotAliasTheLiveBoard() {
        val g = Game.new(4, 2)
        val snap = g.snapshot()
        val frozen = snap.cells.copyOf()
        playOut(g, SeededRandom(1), 10)
        assertArrayEquals(frozen, snap.cells)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedGridSizes() {
        Game.new(1, 0)
    }
}
