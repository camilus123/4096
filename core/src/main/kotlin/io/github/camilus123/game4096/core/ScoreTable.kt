package io.github.camilus123.game4096.core

/** One finished game on a high-score table. */
class ScoreEntry(
    val score: Long,
    /** Exponent of the biggest tile reached (11 is 2048). */
    val maxExponent: Int,
    val moves: Int,
    /** When the game ended, in milliseconds since the Unix epoch. */
    val timeMillis: Long,
)

/**
 * The best [capacity] results for one grid size and mode, highest score first.
 * Among equal scores the earlier result stays ahead. Immutable: [plus] returns a new table.
 */
class ScoreTable private constructor(val entries: List<ScoreEntry>, val capacity: Int) {

    val best: Long get() = if (entries.isEmpty()) 0L else entries[0].score

    val isEmpty: Boolean get() = entries.isEmpty()

    /** Rank (0 is first) that [score] would take, or -1 if it would not make the table. */
    fun rankFor(score: Long): Int {
        var rank = 0
        while (rank < entries.size && entries[rank].score >= score) rank++
        return if (rank < capacity) rank else -1
    }

    fun plus(entry: ScoreEntry): ScoreTable {
        val rank = rankFor(entry.score)
        if (rank < 0) return this
        val list = ArrayList<ScoreEntry>(entries.size + 1)
        list.addAll(entries)
        list.add(rank, entry)
        while (list.size > capacity) list.removeAt(list.size - 1)
        return ScoreTable(list, capacity)
    }

    /** `<score>,<maxExponent>,<moves>,<time>` per entry, joined by `;`. */
    fun encode(): String {
        val sb = StringBuilder()
        for (i in entries.indices) {
            val e = entries[i]
            if (i > 0) sb.append(';')
            sb.append(e.score).append(',').append(e.maxExponent).append(',').append(e.moves).append(',').append(e.timeMillis)
        }
        return sb.toString()
    }

    companion object {
        const val DEFAULT_CAPACITY = 10

        fun empty(capacity: Int = DEFAULT_CAPACITY): ScoreTable = ScoreTable(emptyList(), capacity)

        /** Never throws. Entries that cannot be read are skipped; the rest are kept and re-sorted. */
        fun decode(text: String?, capacity: Int = DEFAULT_CAPACITY): ScoreTable {
            var table = empty(capacity)
            if (text.isNullOrEmpty()) return table
            for (part in text.split(';')) {
                val f = part.split(',')
                if (f.size != 4) continue
                val score = f[0].toLongOrNull() ?: continue
                val maxExponent = f[1].toIntOrNull() ?: continue
                val moves = f[2].toIntOrNull() ?: continue
                val time = f[3].toLongOrNull() ?: continue
                if (score < 0 || moves < 0 || maxExponent !in 0..62) continue
                table = table.plus(ScoreEntry(score, maxExponent, moves, time))
            }
            return table
        }
    }
}
