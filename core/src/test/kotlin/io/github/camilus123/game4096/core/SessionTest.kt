package io.github.camilus123.game4096.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private val stuckCells = board(
        2, 4, 2, 4,
        4, 2, 4, 2,
        2, 4, 2, 4,
        4, 2, 4, 2,
    )

    private fun play(s: Session, count: Int, seed: Long = 1) {
        val picker = SeededRandom(seed)
        val target = s.game.moves + count
        while (s.game.moves < target && s.acceptsMoves) s.game.move(Direction.entries[picker.nextInt(4)])
    }

    @Test fun ruleIdsAreStableAndParseBack() {
        assertEquals("classic", GameRules.CLASSIC.id)
        assertEquals("zen", GameRules.ZEN.id)
        assertEquals("time_180", GameRules(Mode.TIME_ATTACK, 180).id)
        assertEquals("moves_50", GameRules(Mode.LIMITED_MOVES, 50).id)
        assertEquals(8, GameRules.ALL.size)
        assertEquals(8, GameRules.ALL.map { it.id }.toSet().size)
        for (r in GameRules.ALL) assertEquals(r, GameRules.parse(r.id))
        assertNull(GameRules.parse(null))
        assertNull(GameRules.parse("chess"))
        assertNull(GameRules.parse("time"))
        assertNull(GameRules.parse("time_x"))
        assertNull(GameRules.parse("classic_3"))
    }

    @Test fun everyModeIsPlayableOnEveryGrid() {
        for (rules in GameRules.ALL) {
            for (size in ALL_SIZES) {
                val s = Session.new(rules, size, 7)
                assertEquals(SessionStatus.PLAYING, s.status)
                play(s, 20)
                assertEquals("$rules on $size", 20, s.game.moves)
            }
        }
    }

    @Test fun classicEndsWhenNoMovesAreLeft() {
        val s = Session(GameRules.CLASSIC, Game.restore(GameState(4, stuckCells, 10, 5, 1)))
        assertEquals(SessionStatus.OVER, s.status)
        assertEquals(EndReason.NO_MOVES, s.endReason)
        assertFalse(s.acceptsMoves)
        assertFalse(s.undo())
    }

    @Test fun classicAnnouncesTheWinOnce() {
        val cells = IntArray(16).also { it[0] = 10; it[1] = 10 }
        val s = Session(GameRules.CLASSIC, Game.restore(GameState(4, cells, 0, 0, 1)))
        s.game.move(Direction.LEFT)
        assertEquals(SessionStatus.WON, s.status)
        assertFalse(s.acceptsMoves)
        s.winAcknowledged = true
        assertEquals(SessionStatus.PLAYING, s.status)
    }

    @Test fun zenHasUnlimitedUndoAndNeverForcesGameOver() {
        val s = Session.new(GameRules.ZEN, 4, 11)
        val picker = SeededRandom(3)
        while (!s.game.isOver) s.game.move(Direction.entries[picker.nextInt(4)])
        assertEquals(SessionStatus.STUCK, s.status)
        assertFalse(s.acceptsMoves)
        val moves = s.game.moves
        assertEquals("the whole game can be taken back", moves, s.game.undoDepth)
        assertTrue(s.undo())
        assertEquals(SessionStatus.PLAYING, s.status)
        repeat(moves - 1) { assertTrue(s.undo()) }
        assertFalse(s.undo())
        assertEquals(0, s.game.moves)
        assertEquals(0L, s.game.score)
    }

    @Test fun otherModesHaveNoUndo() {
        for (rules in GameRules.ALL.filter { it.mode != Mode.ZEN }) {
            val s = Session.new(rules, 4, 2)
            play(s, 5)
            assertFalse(rules.id, s.undo())
            assertEquals(5, s.game.moves)
        }
    }

    @Test fun timeAttackClockStartsWithTheFirstMove() {
        val s = Session.new(GameRules(Mode.TIME_ATTACK, 60), 4, 5)
        assertEquals(60_000L, s.timeLeftMs)
        assertFalse(s.clockRunning)
        s.advanceClock(5_000)
        assertEquals("no time passes before the first move", 60_000L, s.timeLeftMs)
        play(s, 1)
        assertTrue(s.clockRunning)
        s.advanceClock(5_000)
        assertEquals(55_000L, s.timeLeftMs)
    }

    @Test fun timeAttackEndsWhenTheClockRunsOut() {
        val s = Session.new(GameRules(Mode.TIME_ATTACK, 60), 5, 5)
        play(s, 10)
        s.advanceClock(59_999)
        assertEquals(SessionStatus.PLAYING, s.status)
        s.advanceClock(500)
        assertEquals(0L, s.timeLeftMs)
        assertEquals(SessionStatus.OVER, s.status)
        assertEquals(EndReason.TIME_UP, s.endReason)
        assertFalse(s.acceptsMoves)
        s.advanceClock(1_000)
        assertEquals(0L, s.timeLeftMs)
    }

    @Test fun timeAttackDoesNotStopForTheWinTile() {
        val cells = IntArray(16).also { it[0] = 10; it[1] = 10 }
        val s = Session(GameRules(Mode.TIME_ATTACK, 60), Game.restore(GameState(4, cells, 0, 0, 1)))
        s.game.move(Direction.LEFT)
        assertTrue(s.game.hasWon)
        assertEquals(SessionStatus.PLAYING, s.status)
    }

    @Test fun limitedMovesEndsWhenTheBudgetIsSpent() {
        for (budget in GameRules.MOVE_VARIANTS) {
            val s = Session.new(GameRules(Mode.LIMITED_MOVES, budget), 6, 9)
            assertEquals(budget, s.movesLeft)
            play(s, budget - 1)
            assertEquals(1, s.movesLeft)
            assertEquals(SessionStatus.PLAYING, s.status)
            play(s, 1)
            assertEquals(0, s.movesLeft)
            assertEquals(SessionStatus.OVER, s.status)
            assertEquals(EndReason.OUT_OF_MOVES, s.endReason)
            assertEquals(budget, s.game.moves)
        }
        assertEquals(-1, Session.new(GameRules.CLASSIC, 4, 1).movesLeft)
    }

    @Test fun saveAndRestoreKeepsClockUndoAndWinFlag() {
        val timed = Session.new(GameRules(Mode.TIME_ATTACK, 180), 5, 3)
        play(timed, 12)
        timed.advanceClock(42_500)
        val timedBack = Session.restore(timed.rules, SaveCodec.decode(SaveCodec.encode(timed.toSavedGame()))!!)
        assertEquals(137_500L, timedBack.timeLeftMs)
        assertEquals(timed.game.score, timedBack.game.score)
        assertTrue(timedBack.clockRunning)

        val zen = Session.new(GameRules.ZEN, 4, 3)
        play(zen, 30)
        zen.winAcknowledged = true
        val zenBack = Session.restore(zen.rules, SaveCodec.decode(SaveCodec.encode(zen.toSavedGame()))!!)
        assertEquals(30, zenBack.game.undoDepth)
        assertTrue(zenBack.winAcknowledged)
        assertTrue(zenBack.undo())

        val limited = Session.new(GameRules(Mode.LIMITED_MOVES, 50), 4, 3)
        play(limited, 20)
        val limitedBack = Session.restore(limited.rules, SaveCodec.decode(SaveCodec.encode(limited.toSavedGame()))!!)
        assertEquals(30, limitedBack.movesLeft)
    }

    @Test fun aClockValueOutsideTheLimitIsClamped() {
        val game = Game.new(4, 1)
        assertEquals(60_000L, Session(GameRules(Mode.TIME_ATTACK, 60), game, timeLeftMs = 999_999).timeLeftMs)
        assertEquals(0L, Session(GameRules(Mode.TIME_ATTACK, 60), game, timeLeftMs = -5).timeLeftMs)
    }
}
