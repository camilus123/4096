package io.github.camilus123.game4096.audio

import io.github.camilus123.game4096.R

/** One-shot sound effects. [volume] balances them against each other before the user's setting. */
enum class Sfx(val resource: Int, val volume: Float) {
    SLIDE(R.raw.sfx_slide, 0.35f),
    SPAWN(R.raw.sfx_spawn, 0.30f),
    TAP(R.raw.sfx_tap, 0.50f),
    UNDO(R.raw.sfx_undo, 0.60f),
    WIN(R.raw.sfx_win, 1.00f),
    OVER(R.raw.sfx_over, 0.90f),
}

/**
 * [frames] is the exact length of the loop as the generator wrote it (see the lengths printed
 * by tools/generate-audio.sh, times 44100). Playback loops at exactly this point.
 */
enum class MusicTrack(val resource: Int, val frames: Int) {
    NONE(0, 0),
    MENU(R.raw.music_menu, 3_024_000),
    GAME(R.raw.music_game, 3_386_880),
    ZEN(R.raw.music_zen, 4_233_600),
}

/**
 * What the game asks of its audio. The view model talks to this interface so the game logic
 * can be tested without a sound card.
 */
interface GameAudio {
    fun play(sfx: Sfx)

    /** The merge sound for a new tile with this exponent (2 is the "4" tile); bigger tiles sound bigger. */
    fun playMerge(exponent: Int)

    /** Which music should be playing. Calling it again with the same track changes nothing. */
    fun setMusic(track: MusicTrack)

    /** Volumes are 0 to 1. */
    fun setVolumes(effects: Float, music: Float, muted: Boolean)

    /** False while the app is in the background: everything goes quiet. */
    fun setActive(active: Boolean)
}

/** Audio that does nothing, for tests. */
object SilentAudio : GameAudio {
    override fun play(sfx: Sfx) = Unit
    override fun playMerge(exponent: Int) = Unit
    override fun setMusic(track: MusicTrack) = Unit
    override fun setVolumes(effects: Float, music: Float, muted: Boolean) = Unit
    override fun setActive(active: Boolean) = Unit
}
