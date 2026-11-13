package io.github.camilus123.game4096.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    /** Slides a single row left and returns (result, gain). */
    private fun slideRow(vararg values: Int): Pair<List<Long>, Long> {
        val n = values.size
        val src = IntArray(n * n)
        board(*values).copyInto(src)
        val dst = IntArray(n * n)
        val gain = Rules.move(src, n, Direction.LEFT, dst)
        return dst.copyOfRange(0, n).values() to gain
    }

    @Test fun slidesTilesToTheEdge() {
        assertEquals(listOf(2L, 0L, 0L, 0L) to 0L, slideRow(0, 0, 0, 2))
        assertEquals(listOf(2L, 4L, 0L, 0L) to 0L, slideRow(0, 2, 0, 4))
    }

    @Test fun mergesEqualNeighbours() {
        assertEquals(listOf(4L, 0L, 0L, 0L) to 4L, slideRow(2, 2, 0, 0))
        assertEquals(listOf(4L, 0L, 0L, 0L) to 4L, slideRow(2, 0, 0, 2))
    }

    @Test fun fourEqualTilesMakeTwoPairs() {
        assertEquals(listOf(4L, 4L, 0L, 0L) to 8L, slideRow(2, 2, 2, 2))
    }

    @Test fun aTileMergesOnlyOncePerMove() {
        assertEquals(listOf(4L, 4L, 0L, 0L) to 4L, slideRow(4, 2, 2, 0))
        assertEquals(listOf(8L, 4L, 0L, 0L) to 8L, slideRow(4, 4, 4, 0))
        // The freshly made 4 must not merge with the 4 that follows it.
        assertEquals(listOf(4L, 4L, 0L, 0L) to 4L, slideRow(2, 2, 4, 0))
    }

    @Test fun threeEqualTilesMergeTowardsTheSwipeEdge() {
        val src = board(2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val dst = IntArray(16)
        Rules.move(src, 4, Direction.LEFT, dst)
        assertEquals(listOf(4L, 2L, 0L, 0L), dst.copyOfRange(0, 4).values())
        Rules.move(src, 4, Direction.RIGHT, dst)
        assertEquals(listOf(0L, 0L, 2L, 4L), dst.copyOfRange(0, 4).values())
    }

    @Test fun unequalTilesDoNotMerge() {
        val (row, gain) = slideRow(2, 4, 8, 16)
        assertEquals(listOf(2L, 4L, 8L, 16L), row)
        assertEquals(Rules.NO_MOVE, gain)
    }

    @Test fun scoreIsTheSumOfMergedTiles() {
        assertEquals(listOf(8L, 16L, 0L, 0L) to 24L, slideRow(4, 4, 8, 8))
        assertEquals(4096L, slideRow(2048, 2048, 0, 0).second)
    }

    @Test fun allFourDirectionsOnAFullBoard() {
        val src = board(
            2, 2, 4, 4,
            0, 2, 0, 2,
            8, 0, 0, 8,
            2, 4, 8, 16,
        )
        val dst = IntArray(16)

        assertEquals(32L, Rules.move(src, 4, Direction.LEFT, dst))
        assertArrayEquals(board(4, 8, 0, 0, 4, 0, 0, 0, 16, 0, 0, 0, 2, 4, 8, 16), dst)

        assertEquals(32L, Rules.move(src, 4, Direction.RIGHT, dst))
        assertArrayEquals(board(0, 0, 4, 8, 0, 0, 0, 4, 0, 0, 0, 16, 2, 4, 8, 16), dst)

        assertEquals(4L, Rules.move(src, 4, Direction.UP, dst))
        assertArrayEquals(board(2, 4, 4, 4, 8, 4, 8, 2, 2, 0, 0, 8, 0, 0, 0, 16), dst)

        assertEquals(4L, Rules.move(src, 4, Direction.DOWN, dst))
        assertArrayEquals(board(0, 0, 0, 4, 2, 0, 0, 2, 8, 4, 4, 8, 2, 4, 8, 16), dst)
    }

    @Test fun moveThatChangesNothingIsReported() {
        val src = board(
            2, 0, 0, 0,
            4, 0, 0, 0,
            2, 0, 0, 0,
            4, 0, 0, 0,
        )
        val dst = IntArray(16)
        assertEquals(Rules.NO_MOVE, Rules.move(src, 4, Direction.LEFT, dst))
        assertArrayEquals(src, dst)
        assertEquals(Rules.NO_MOVE, Rules.move(src, 4, Direction.UP, dst))
        assertEquals(0L, Rules.move(src, 4, Direction.RIGHT, dst)) // moved, nothing merged
        assertFalse(Rules.canMove(src, 4, Direction.LEFT))
        assertFalse(Rules.canMove(src, 4, Direction.DOWN))
        assertTrue(Rules.canMove(src, 4, Direction.RIGHT))
    }

    @Test fun moveWorksInPlace() {
        val rng = SeededRandom(7)
        for (size in ALL_SIZES) {
            repeat(2000) {
                val src = randomBoard(size, rng)
                for (dir in Direction.entries) {
                    val expected = IntArray(size * size)
                    val expectedGain = Rules.move(src, size, dir, expected)
                    val inPlace = src.copyOf()
                    assertEquals(expectedGain, Rules.move(inPlace, size, dir, inPlace))
                    assertArrayEquals(expected, inPlace)
                }
            }
        }
    }

    @Test fun traceRecordsWhereEveryTileWent() {
        val src = board(
            2, 2, 0, 4,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
        )
        val dst = IntArray(16)
        val trace = MoveTrace(16)
        Rules.move(src, 4, Direction.LEFT, dst, trace)
        assertEquals(0, trace.dest[0])
        assertEquals(0, trace.dest[1])
        assertEquals(-1, trace.dest[2])
        assertEquals(1, trace.dest[3])
        assertTrue(trace.merged[0])
        assertFalse(trace.merged[1])
        // A second move must not keep stale entries.
        Rules.move(board(0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 4, Direction.LEFT, dst, trace)
        assertEquals(-1, trace.dest[0])
        assertEquals(0, trace.dest[3])
        assertFalse(trace.merged[0])
    }

    @Test fun traceIsConsistentWithTheResult() {
        val rng = SeededRandom(11)
        for (size in ALL_SIZES) {
            val n = size * size
            val trace = MoveTrace(n)
            val dst = IntArray(n)
            repeat(2000) {
                val src = randomBoard(size, rng)
                for (dir in Direction.entries) {
                    Rules.move(src, size, dir, dst, trace)
                    val arrivals = IntArray(n)
                    for (i in 0 until n) {
                        val to = trace.dest[i]
                        assertEquals(src[i] == 0, to == -1)
                        if (to != -1) arrivals[to]++
                    }
                    for (i in 0 until n) {
                        when (arrivals[i]) {
                            0 -> assertEquals(0, dst[i])
                            1 -> assertFalse(trace.merged[i])
                            2 -> assertTrue(trace.merged[i])
                            else -> throw AssertionError("three tiles merged into one cell")
                        }
                    }
                    for (i in 0 until n) {
                        val to = trace.dest[i]
                        if (to != -1) {
                            assertEquals(if (trace.merged[to]) src[i] + 1 else src[i], dst[to])
                        }
                    }
                }
            }
        }
    }

    @Test fun movesConserveTheSumOfTheTiles() {
        val rng = SeededRandom(3)
        for (size in ALL_SIZES) {
            val dst = IntArray(size * size)
            repeat(3000) {
                val src = randomBoard(size, rng)
                for (dir in Direction.entries) {
                    val gain = Rules.move(src, size, dir, dst)
                    assertEquals(src.sumOfValues(), dst.sumOfValues())
                    assertEquals(gain != Rules.NO_MOVE, Rules.canMove(src, size, dir))
                    assertEquals(gain != Rules.NO_MOVE, !src.contentEquals(dst))
                }
            }
        }
    }

    @Test fun directionsAreMirrorImagesOfEachOther() {
        val rng = SeededRandom(5)
        for (size in ALL_SIZES) {
            val n = size * size
            repeat(2000) {
                val src = randomBoard(size, rng)
                val left = IntArray(n)
                val leftGain = Rules.move(src, size, Direction.LEFT, left)

                val mirrored = IntArray(n) { src[(it / size) * size + (size - 1 - it % size)] }
                val right = IntArray(n)
                assertEquals(leftGain, Rules.move(mirrored, size, Direction.RIGHT, right))
                assertArrayEquals(left, IntArray(n) { right[(it / size) * size + (size - 1 - it % size)] })

                val transposed = IntArray(n) { src[(it % size) * size + it / size] }
                val up = IntArray(n)
                assertEquals(leftGain, Rules.move(transposed, size, Direction.UP, up))
                assertArrayEquals(left, IntArray(n) { up[(it % size) * size + it / size] })

                val flipped = IntArray(n) { transposed[(size - 1 - it / size) * size + it % size] }
                val down = IntArray(n)
                assertEquals(leftGain, Rules.move(flipped, size, Direction.DOWN, down))
                assertArrayEquals(up, IntArray(n) { down[(size - 1 - it / size) * size + it % size] })
            }
        }
    }

    @Test fun gameOverNeedsAFullBoardWithNoEqualNeighbours() {
        val stuck = board(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2,
        )
        assertFalse(Rules.hasMoves(stuck, 4))
        for (dir in Direction.entries) assertFalse(Rules.canMove(stuck, 4, dir))

        val oneGap = stuck.copyOf().also { it[5] = 0 }
        assertTrue(Rules.hasMoves(oneGap, 4))

        val horizontalPair = stuck.copyOf().also { it[15] = it[14] }
        assertTrue(Rules.hasMoves(horizontalPair, 4))

        val verticalPair = stuck.copyOf().also { it[15] = it[11] }
        assertTrue(Rules.hasMoves(verticalPair, 4))

        // Equal tiles across a row wrap (end of one row, start of the next) are not neighbours.
        val wrap = board(
            2, 4, 8, 16,
            16, 32, 64, 128,
            2, 4, 8, 16,
            32, 64, 128, 256,
        )
        assertFalse(Rules.hasMoves(wrap, 4))
    }

    @Test fun hasMovesAgreesWithCanMoveOnEverySize() {
        val rng = SeededRandom(13)
        for (size in ALL_SIZES) {
            repeat(3000) {
                val b = randomBoard(size, rng, emptyChance = 12)
                assertEquals(Direction.entries.any { Rules.canMove(b, size, it) }, Rules.hasMoves(b, size))
            }
        }
    }

    @Test fun spawnFillsOnlyEmptyCellsWithTwosAndFours() {
        val rng = SeededRandom(1)
        for (size in ALL_SIZES) {
            val cells = IntArray(size * size)
            cells[0] = 5
            repeat(size * size - 1) {
                val before = cells.copyOf()
                val at = Rules.spawn(cells, rng)
                assertEquals(0, before[at])
                assertTrue(cells[at] == 1 || cells[at] == 2)
                for (i in cells.indices) if (i != at) assertEquals(before[i], cells[i])
            }
            assertEquals(0, Rules.countEmpty(cells))
            assertEquals(-1, Rules.spawn(cells, rng))
            assertEquals(5, cells[0])
        }
    }

    @Test fun spawnOddsAreNinetyTen() {
        val rng = SeededRandom(2024)
        val trials = 200_000
        var fours = 0
        val cells = IntArray(16)
        repeat(trials) {
            cells.fill(0)
            Rules.spawn(cells, rng)
            if (cells.any { it == 2 }) fours++
        }
        val share = fours.toDouble() / trials
        assertTrue("share of 4s was $share", share > 0.095 && share < 0.105)
    }

    @Test fun spawnPositionIsUniform() {
        val rng = SeededRandom(99)
        val trials = 160_000
        val hits = IntArray(16)
        val cells = IntArray(16)
        repeat(trials) {
            cells.fill(0)
            hits[Rules.spawn(cells, rng)]++
        }
        for (h in hits) assertTrue("cell hit $h times", h > 9_500 && h < 10_500)
    }

    @Test fun winTilesPerGrid() {
        assertEquals(2048L, Rules.tileValue(Rules.defaultWinExponent(4)))
        assertEquals(4096L, Rules.tileValue(Rules.defaultWinExponent(5)))
        assertEquals(8192L, Rules.tileValue(Rules.defaultWinExponent(6)))
    }

    @Test fun bigTilesDoNotOverflow() {
        val src = IntArray(36).also { it[0] = 34; it[1] = 34 }
        val dst = IntArray(36)
        assertEquals(1L shl 35, Rules.move(src, 6, Direction.LEFT, dst))
        assertEquals(35, dst[0])
    }

    private fun randomBoard(size: Int, rng: SeededRandom, emptyChance: Int = 3): IntArray =
        IntArray(size * size) { if (rng.nextInt(emptyChance) == 0) 0 else 1 + rng.nextInt(4) }
}
