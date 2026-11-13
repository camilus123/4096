package io.github.camilus123.game4096.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.camilus123.game4096.core.Game

enum class GameStatus { PLAYING, WON, OVER }

/** Holds the game in play across activity recreation and mirrors it into Compose state. */
class GameViewModel : ViewModel() {

    var game: Game? by mutableStateOf(null)
        private set

    var score by mutableLongStateOf(0L)
        private set

    var moves by mutableIntStateOf(0)
        private set

    var status by mutableStateOf(GameStatus.PLAYING)
        private set

    private var winAcknowledged = false

    fun newGame(size: Int) {
        winAcknowledged = false
        game = Game.new(size, seed = System.nanoTime() xor (System.currentTimeMillis() shl 20))
        sync()
    }

    /** Called by the board after every move that changed it. Not called per frame. */
    fun onMoved() = sync()

    fun keepPlaying() {
        winAcknowledged = true
        sync()
    }

    private fun sync() {
        val g = game ?: return
        score = g.score
        moves = g.moves
        status = when {
            g.hasWon && !winAcknowledged -> GameStatus.WON
            g.isOver -> GameStatus.OVER
            else -> GameStatus.PLAYING
        }
    }
}
