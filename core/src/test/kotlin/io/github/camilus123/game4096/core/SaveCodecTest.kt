package io.github.camilus123.game4096.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCodecTest {

    private fun Game.cells(): IntArray = IntArray(cellCount).also { copyCellsInto(it) }

    private fun play(game: Game, seed: Long, moves: Int) {
        val picker = SeededRandom(seed)
        while (!game.isOver && game.moves < moves) game.move(Direction.entries[picker.nextInt(4)])
    }

    @Test fun roundTripContinuesTheSameGame() {
        for (size in ALL_SIZES) {
            val original = Game.new(size, 100L + size)
            play(original, 1, 80)
            val saved = SaveCodec.decode(SaveCodec.encode(original, winAcknowledged = false))
            assertNotNull(saved)
            val copy = SaveCodec.restore(saved!!, original.winExponent, undoLimit = 0)
            assertArrayEquals(original.cells(), copy.cells())
            assertEquals(original.score, copy.score)
            assertEquals(original.moves, copy.moves)
            assertFalse(saved.winAcknowledged)

            play(original, 2, 200)
            play(copy, 2, 200)
            assertArrayEquals(original.cells(), copy.cells())
            assertEquals(original.score, copy.score)
        }
    }

    @Test fun undoHistoryAndWinFlagSurvive() {
        val original = Game.new(5, 9, undoLimit = Game.UNLIMITED_UNDO)
        play(original, 3, 40)
        val saved = SaveCodec.decode(SaveCodec.encode(original, winAcknowledged = true))!!
        assertTrue(saved.winAcknowledged)
        val copy = SaveCodec.restore(saved, original.winExponent, Game.UNLIMITED_UNDO)
        assertEquals(40, copy.undoDepth)
        repeat(40) {
            assertTrue(original.undo())
            assertTrue(copy.undo())
            assertArrayEquals(original.cells(), copy.cells())
            assertEquals(original.score, copy.score)
        }
        assertFalse(copy.canUndo)
    }

    @Test fun savedHistoryIsCapped() {
        val game = Game.new(4, 4, undoLimit = Game.UNLIMITED_UNDO)
        play(game, 5, 60)
        val saved = SaveCodec.decode(SaveCodec.encode(game, false, maxHistory = 10))!!
        assertEquals(10, saved.history.size)
        assertEquals(game.moves - 1, saved.history.last().moves)
    }

    @Test fun negativeRngStateSurvives() {
        val state = GameState(4, IntArray(16).also { it[3] = 5 }, 12L, 3, Long.MIN_VALUE + 5)
        val back = SaveCodec.decode(SaveCodec.encode(SavedGame(state, emptyList(), false)))!!
        assertEquals(Long.MIN_VALUE + 5, back.state.rngState)
        assertEquals(5, back.state.cells[3])
    }

    @Test fun extraNumberSurvives() {
        val game = Game.new(4, 1)
        assertEquals(123_456L, SaveCodec.decode(SaveCodec.encode(game, false, extra = 123_456L))!!.extra)
        assertEquals(0L, SaveCodec.decode(SaveCodec.encode(game, false))!!.extra)
    }

    @Test fun versionOneSavesAreStillRead() {
        val v1 = "1;4;1;40,7,-99,0 1 0 0 0 0 2 0 0 0 0 0 0 0 0 3"
        val saved = SaveCodec.decode(v1)!!
        assertTrue(saved.winAcknowledged)
        assertEquals(0L, saved.extra)
        assertEquals(40L, saved.state.score)
        assertEquals(7, saved.state.moves)
        assertEquals(-99L, saved.state.rngState)
        assertEquals(3, saved.state.cells[15])
    }

    @Test fun damagedSavesAreRejectedNotCrashed() {
        val good = SaveCodec.encode(Game.new(4, 1), false)
        assertNotNull(SaveCodec.decode(good))
        assertNull(SaveCodec.decode(null))
        assertNull(SaveCodec.decode(""))
        assertNull(SaveCodec.decode("garbage"))
        assertNull(SaveCodec.decode(good.replaceFirst("2;", "9;")))            // unknown version
        assertNull(SaveCodec.decode(good.replaceFirst(";4;", ";5;")))          // size does not match cells
        assertNull(SaveCodec.decode(good.replaceFirst(";4;", ";99;")))
        assertNull(SaveCodec.decode(good.dropLast(2)))                          // truncated
        assertNull(SaveCodec.decode(good.dropLast(1) + "x"))                    // not a number
        assertNull(SaveCodec.decode(good.dropLast(1) + "99"))                   // impossible tile
        assertNull(SaveCodec.decode("$good;1,2,3"))                             // broken history entry
        for (cut in 0 until good.length) SaveCodec.decode(good.substring(0, cut)) // must never throw
    }
}
