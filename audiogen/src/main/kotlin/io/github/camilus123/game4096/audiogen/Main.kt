package io.github.camilus123.game4096.audiogen

import java.io.File

/**
 * Writes every sound of the game as a WAV file into the folder given as the first argument.
 * The output is deterministic: the same code always produces the same samples.
 */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "build/audio")
    val sounds = LinkedHashMap<String, Stereo>()
    sounds["sfx_slide"] = slideEffect()
    for (e in 2..13) sounds["sfx_merge_%02d".format(e)] = mergeEffect(e)
    sounds["sfx_spawn"] = spawnEffect()
    sounds["sfx_tap"] = tapEffect()
    sounds["sfx_undo"] = undoEffect()
    sounds["sfx_win"] = winEffect()
    sounds["sfx_over"] = gameOverEffect()
    sounds["music_menu"] = menuMusic()
    sounds["music_game"] = gameMusic()
    sounds["music_zen"] = zenMusic()
    for ((name, sound) in sounds) {
        sound.writeWav(File(dir, "$name.wav"))
        val loudness = if (name.startsWith("sfx_")) "  loudness %.3f".format(sound.loudness()) else ""
        println("%-14s %6.2f s  peak %.2f  rms %.3f%s".format(name, sound.length.toDouble() / SAMPLE_RATE, sound.peak(), sound.rms(), loudness))
    }
    println("Wrote ${sounds.size} files to ${dir.absolutePath}")
}
