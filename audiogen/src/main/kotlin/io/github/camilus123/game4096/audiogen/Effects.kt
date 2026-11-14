package io.github.camilus123.game4096.audiogen

import kotlin.math.exp
import kotlin.math.sin

/** Notes of a major pentatonic scale, as semitones above the root, over three octaves. */
private val PENTATONIC = intArrayOf(0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24, 26, 28, 31, 33, 36)

private fun oneShot(seconds: Double) = Stereo(seconds(seconds), loop = false)

private fun Stereo.finish(peak: Double, wet: Double = 0.10): Stereo {
    if (wet > 0.0) reverb(wet, feedback = 0.6)
    fadeEdges(seconds(0.002), seconds(0.02))
    normalize(peak)
    return this
}

/** Tiles sliding: a short breath of filtered noise over a faint low thump. */
fun slideEffect(): Stereo {
    val out = oneShot(0.11)
    val noise = Noise(1)
    var smooth = 0.0
    out.add(0, seconds(0.09), 0.9, 0.0) { t ->
        // Low-passed noise whose cutoff opens then closes: a soft "fff".
        val cutoff = 0.08 + 0.25 * sin(Math.PI * (t / 0.09))
        smooth += cutoff * (noise.white() - smooth)
        smooth * sin(Math.PI * (t / 0.09))
    }
    out.add(0, seconds(0.06), 0.35, 0.0) { t -> sin(TAU * 140.0 * t) * exp(-t * 60.0) }
    return out.finish(0.5, wet = 0.0)
}

/**
 * Two tiles merging into the tile with this [exponent] (2 is the "4" tile).
 *
 * Small merges happen on almost every move, so they are kept short, round and soft: a
 * marimba-like knock with hardly any overtones. Each bigger tile is one step higher on a
 * pentatonic scale. From 128 up the note rings longer and an octave joins in, and from 1024
 * up it becomes a small rising chord, so the rare big merges are the ones that stand out.
 */
fun mergeEffect(exponent: Int): Stereo {
    val step = (exponent - 2).coerceIn(0, PENTATONIC.size - 1)
    val midi = 57.0 + PENTATONIC[step] // starts on A3
    val hz = midiToHz(midi)
    val big = exponent >= 7
    val huge = exponent >= 10
    if (!big) {
        val out = oneShot(0.22)
        out.add(0, seconds(0.2), 1.0, 0.0) { t ->
            val w = TAU * hz * t
            (sin(w) * exp(-t * 16.0) + 0.16 * sin(2.0 * w) * exp(-t * 28.0)) * envelope(t, 0.006, 0.2, 0.02)
        }
        return out.finish(0.8, wet = 0.04)
    }
    val length = if (huge) 0.75 else 0.45
    val out = oneShot(length + 0.25)
    out.add(0, seconds(length), 1.0, 0.0) { t -> epiano(hz, t, length * 0.5) * exp(-t * 2.0) }
    out.add(seconds(0.035), seconds(length), 0.35, 0.25) { t -> bell(hz * 2.0, t) * exp(-t * 2.0) }
    if (huge) {
        // Root, fifth, octave in quick succession.
        out.add(seconds(0.07), seconds(length), 0.5, -0.3) { t -> bell(midiToHz(midi + 7.0), t) }
        out.add(seconds(0.14), seconds(length), 0.55, 0.3) { t -> bell(midiToHz(midi + 12.0), t) }
        out.add(0, seconds(0.5), 0.5, 0.0) { t -> bass(hz / 2.0, t, 0.3) }
    }
    out.finish(0.8, wet = 0.16)
    // Big merges ring longer and brighter, which the ear hears as louder at the same sample
    // level. Bring each one down to the loudness of the largest small merge (the 64 tile), so
    // a big merge is richer and longer than a small one, not louder.
    out.scale(minOf(1.0, bigMergeLoudness / out.loudness()))
    return out
}

/** The loudness every merge of 128 and up is matched to: that of the merge that makes 64. */
private val bigMergeLoudness: Double by lazy { mergeEffect(6).loudness() }

/** A new tile appearing: a tiny soft blip. */
fun spawnEffect(): Stereo {
    val out = oneShot(0.09)
    out.add(0, seconds(0.08), 1.0, 0.0) { t -> sin(TAU * (720.0 + 500.0 * t / 0.08) * t) * exp(-t * 45.0) }
    return out.finish(0.4, wet = 0.0)
}

/** A button press: a short dry tick. */
fun tapEffect(): Stereo {
    val out = oneShot(0.05)
    val noise = Noise(2)
    out.add(0, seconds(0.04), 1.0, 0.0) { t -> (sin(TAU * 1250.0 * t) + 0.3 * noise.bright()) * exp(-t * 110.0) }
    return out.finish(0.5, wet = 0.0)
}

/** Undo: two quick falling notes. */
fun undoEffect(): Stereo {
    val out = oneShot(0.3)
    out.add(0, seconds(0.2), 1.0, -0.1) { t -> epiano(midiToHz(76.0), t, 0.05) }
    out.add(seconds(0.07), seconds(0.2), 1.0, 0.1) { t -> epiano(midiToHz(72.0), t, 0.06) }
    return out.finish(0.6)
}

/** Winning: a rising major arpeggio that settles on a chord. */
fun winEffect(): Stereo {
    val out = oneShot(2.0)
    val notes = doubleArrayOf(60.0, 64.0, 67.0, 72.0, 76.0)
    for (i in notes.indices) {
        val hz = midiToHz(notes[i])
        out.add(seconds(i * 0.09), seconds(1.4), 0.7, -0.4 + 0.2 * i) { t -> bell(hz, t) + 0.5 * epiano(hz, t, 0.5) }
    }
    for (m in doubleArrayOf(48.0, 60.0, 64.0, 67.0)) {
        val hz = midiToHz(m)
        out.add(seconds(0.45), seconds(1.5), 0.3, 0.0) { t -> pad(hz, t, 0.7, attack = 0.15, release = 0.7) }
    }
    return out.finish(0.85, wet = 0.22)
}

/** Game over: three notes stepping down into a low, soft minor chord. */
fun gameOverEffect(): Stereo {
    val out = oneShot(1.9)
    val notes = doubleArrayOf(67.0, 63.0, 60.0)
    for (i in notes.indices) {
        val hz = midiToHz(notes[i])
        out.add(seconds(i * 0.2), seconds(1.0), 0.7, 0.0) { t -> epiano(hz, t, 0.4) }
    }
    for (m in doubleArrayOf(36.0, 48.0, 51.0, 55.0)) {
        val hz = midiToHz(m)
        out.add(seconds(0.6), seconds(1.3), 0.3, 0.0) { t -> pad(hz, t, 0.5, attack = 0.2, release = 0.7) }
    }
    return out.finish(0.8, wet = 0.2)
}
