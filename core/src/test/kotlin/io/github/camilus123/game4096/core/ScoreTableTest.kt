package io.github.camilus123.game4096.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTableTest {

    private fun entry(score: Long, time: Long = 0L) = ScoreEntry(score, 5, 10, time)

    @Test fun keepsTheBestScoresInOrder() {
        var t = ScoreTable.empty(capacity = 3)
        assertTrue(t.isEmpty)
        assertEquals(0L, t.best)
        for (s in longArrayOf(50, 200, 100, 150, 10)) t = t.plus(entry(s))
        assertEquals(listOf(200L, 150L, 100L), t.entries.map { it.score })
        assertEquals(200L, t.best)
    }

    @Test fun aScoreTooLowForAFullTableChangesNothing() {
        var t = ScoreTable.empty(capacity = 2).plus(entry(30)).plus(entry(20))
        assertEquals(-1, t.rankFor(10))
        assertEquals(-1, t.rankFor(20)) // ties do not push out an earlier result
        assertSame(t, t.plus(entry(10)))
        assertEquals(1, t.rankFor(25))
        assertEquals(0, t.rankFor(31))
        t = t.plus(entry(25))
        assertEquals(listOf(30L, 25L), t.entries.map { it.score })
    }

    @Test fun equalScoresKeepTheEarlierOneFirst() {
        val t = ScoreTable.empty().plus(entry(100, time = 1)).plus(entry(100, time = 2))
        assertEquals(listOf(1L, 2L), t.entries.map { it.timeMillis })
    }

    @Test fun roundTrip() {
        var t = ScoreTable.empty()
        for (i in 1..12) t = t.plus(ScoreEntry(i * 1000L, i, i * 7, 1_700_000_000_000L + i))
        val back = ScoreTable.decode(t.encode())
        assertEquals(10, back.entries.size)
        for (i in t.entries.indices) {
            assertEquals(t.entries[i].score, back.entries[i].score)
            assertEquals(t.entries[i].maxExponent, back.entries[i].maxExponent)
            assertEquals(t.entries[i].moves, back.entries[i].moves)
            assertEquals(t.entries[i].timeMillis, back.entries[i].timeMillis)
        }
        assertEquals("", ScoreTable.empty().encode())
    }

    @Test fun damagedTextKeepsWhatCanBeRead() {
        assertTrue(ScoreTable.decode(null).isEmpty)
        assertTrue(ScoreTable.decode("").isEmpty)
        assertTrue(ScoreTable.decode("nonsense").isEmpty)
        val t = ScoreTable.decode("100,5,10,1;x,5,10,1;300,6,20,2;50,5;-4,1,1,1;70,99,1,1")
        assertEquals(listOf(300L, 100L), t.entries.map { it.score })
    }
}
