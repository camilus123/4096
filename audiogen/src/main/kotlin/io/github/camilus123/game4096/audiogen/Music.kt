package io.github.camilus123.game4096.audiogen

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp

/** A chord: [root] as a MIDI note in the bass register, [tones] as semitones above it. */
class Chord(val root: Int, vararg val tones: Int)

private fun maj7(root: Int) = Chord(root, 0, 4, 7, 11)
private fun min7(root: Int) = Chord(root, 0, 3, 7, 10)
private fun six(root: Int) = Chord(root, 0, 4, 7, 9)
private fun sus7(root: Int) = Chord(root, 0, 5, 7, 10)
private fun maj9(root: Int) = Chord(root, 0, 4, 7, 11, 14)
private fun min9(root: Int) = Chord(root, 0, 3, 7, 10, 14)

/**
 * A looping piece of music being written into a [Stereo] buffer. Positions are in beats.
 * One bar is four beats, and the chord changes once per bar.
 */
class Song(val bpm: Double, val progression: List<Chord>, val bars: Int, private val seed: Long) {
    private val beatSeconds = 60.0 / bpm
    val out = Stereo(seconds(bars * 4 * beatSeconds), loop = true)

    fun chordAt(bar: Int): Chord = progression[bar % progression.size]

    private fun at(beat: Double): Int = (beat * beatSeconds * SAMPLE_RATE).toInt()

    /** A generator that gives the same numbers for the same [id]: this is how a motif repeats. */
    fun random(id: Int): Random = Random(seed * 1_000_003L + id)

    fun pad(bar: Int, gain: Double, attack: Double = 0.9, release: Double = 1.6) {
        val chord = chordAt(bar)
        val held = 4 * beatSeconds
        for ((i, tone) in chord.tones.withIndex()) {
            var midi = chord.root + 12 + tone
            while (midi > 67) midi -= 12
            while (midi < 52) midi += 12
            val hz = midiToHz(midi.toDouble())
            val pan = -0.5 + i.toDouble() / (chord.tones.size - 1)
            out.add(at(bar * 4.0), seconds(held + release), gain, pan) { t -> pad(hz, t, held, attack, release) }
        }
    }

    /** Eighth-note broken chord. [pattern] indexes into the chord's tones. */
    fun arpeggio(bar: Int, pattern: IntArray, octave: Int, gain: Double) {
        val chord = chordAt(bar)
        for (step in 0 until 8) {
            val tone = chord.tones[pattern[step % pattern.size] % chord.tones.size]
            val hz = midiToHz((chord.root + 12 * octave + tone).toDouble())
            val accent = if (step % 2 == 0) 1.0 else 0.72
            val pan = if (step % 2 == 0) -0.25 else 0.25
            out.add(at(bar * 4.0 + step * 0.5), seconds(1.2), gain * accent, pan) { t -> epiano(hz, t, 0.35) }
        }
    }

    fun bassLine(bar: Int, beats: DoubleArray, lengths: DoubleArray, gain: Double) {
        val hz = midiToHz(chordAt(bar).root.toDouble())
        for (i in beats.indices) {
            val held = lengths[i] * beatSeconds
            out.add(at(bar * 4.0 + beats[i]), seconds(held + 0.1), gain, 0.0) { t -> bass(hz, t, held) }
        }
    }

    fun drums(bar: Int, kickGain: Double, hatGain: Double) {
        for (beat in intArrayOf(0, 2)) {
            out.add(at(bar * 4.0 + beat), seconds(0.3), kickGain, 0.0) { t -> kick(t) }
        }
        val noise = Noise(seed + bar)
        for (step in 0 until 4) {
            out.add(at(bar * 4.0 + step + 0.5), seconds(0.05), hatGain, 0.3) { t -> noise.bright() * exp(-t * 70.0) }
        }
    }

    /**
     * A short phrase on [scale]. The same [motif] number always gives the same rhythm and the
     * same contour; the first note is pulled to the nearest note of the bar's chord, so a
     * repeated motif follows the harmony.
     */
    fun melody(bar: Int, motif: Int, scale: IntArray, gain: Double) {
        val r = random(motif)
        val rhythm = RHYTHMS[r.nextInt(RHYTHMS.size)]
        val chord = chordAt(bar)
        var index = nearestChordTone(scale, chord, scale.size / 2 + r.nextInt(3) - 1)
        for ((i, beat) in rhythm.withIndex()) {
            if (i > 0) index = (index + STEPS[r.nextInt(STEPS.size)]).coerceIn(0, scale.size - 1)
            val hz = midiToHz(scale[index].toDouble())
            val pan = -0.3 + 0.6 * r.nextDouble()
            out.add(at(bar * 4.0 + beat), seconds(2.5), gain, pan) { t -> bell(hz, t) }
        }
    }

    private fun nearestChordTone(scale: IntArray, chord: Chord, around: Int): Int {
        var best = around
        var bestDistance = Int.MAX_VALUE
        for (i in scale.indices) {
            val pitchClass = ((scale[i] - chord.root) % 12 + 12) % 12
            if (chord.tones.none { it % 12 == pitchClass }) continue
            val distance = abs(i - around)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    /** Reverb, then set the loudness: the same average level for every track, never clipping. */
    fun master(wet: Double): Stereo {
        out.reverb(wet)
        val rms = out.rms()
        val peak = out.peak().toDouble()
        val gain = minOf(TARGET_RMS / rms, 0.89 / peak)
        out.normalize(peak * gain)
        return out
    }

    private companion object {
        const val TARGET_RMS = 0.085
        val RHYTHMS = arrayOf(
            doubleArrayOf(0.0, 1.5, 2.0, 3.0),
            doubleArrayOf(0.0, 2.0, 2.5),
            doubleArrayOf(0.5, 1.5, 3.0),
            doubleArrayOf(0.0, 1.0, 2.0, 3.5),
            doubleArrayOf(0.0, 2.5),
        )
        val STEPS = intArrayOf(-2, -1, -1, 1, 1, 2)
    }
}

/** Menu: warm and unhurried. C major, 84 bpm, 24 bars (about 69 seconds). */
fun menuMusic(): Stereo {
    val song = Song(
        bpm = 84.0,
        progression = listOf(maj7(48), min7(45), maj7(41), six(43), min7(40), min7(45), min7(50), sus7(43)),
        bars = 24,
        seed = 11,
    )
    val scale = intArrayOf(72, 74, 76, 79, 81, 84, 86)
    val up = intArrayOf(0, 2, 1, 2, 3, 2, 1, 2)
    val wave = intArrayOf(0, 1, 2, 3, 2, 1, 2, 3)
    val motifs = intArrayOf(0, 1, 0, 2, 0, 1, 3, 4, 5, 1, 5, 2, 0, 1, 3, 6)
    for (bar in 0 until song.bars) {
        val pass = bar / 8
        song.pad(bar, 0.10)
        song.arpeggio(bar, if (pass == 2) wave else up, octave = 2, gain = if (pass == 0) 0.20 else 0.16)
        if (pass >= 1) {
            song.bassLine(bar, doubleArrayOf(0.0, 2.0), doubleArrayOf(1.8, 1.5), 0.26)
            song.melody(bar, motifs[bar - 8], scale, 0.17)
        }
    }
    return song.master(wet = 0.24)
}

/** Gameplay: light and steady so it can run for a long time. A minor, 100 bpm, 32 bars (about 77 seconds). */
fun gameMusic(): Stereo {
    val song = Song(
        bpm = 100.0,
        progression = listOf(min7(45), maj7(41), maj7(48), six(43), min7(45), maj7(41), min7(50), min7(40)),
        bars = 32,
        seed = 23,
    )
    val scale = intArrayOf(69, 72, 74, 76, 79, 81, 84)
    val a = intArrayOf(0, 2, 1, 3, 2, 1, 3, 2)
    val b = intArrayOf(0, 1, 2, 3, 0, 2, 1, 3)
    val groove = doubleArrayOf(0.0, 1.5, 2.0, 3.5)
    val grooveLengths = doubleArrayOf(1.2, 0.4, 1.2, 0.4)
    val motifs = intArrayOf(0, 1, 0, 2, 0, 1, 3, 4, 5, 6, 5, 2, 5, 6, 3, 7, 0, 8, 0, 2, 0, 1, 3, 4)
    for (bar in 0 until song.bars) {
        val section = bar / 8
        val breakdown = section == 3 && bar % 8 < 4
        song.pad(bar, if (breakdown) 0.13 else 0.08)
        if (!breakdown) {
            song.arpeggio(bar, if (section == 2) b else a, octave = 2, gain = 0.17)
            song.bassLine(bar, groove, grooveLengths, 0.27)
            song.drums(bar, kickGain = if (section == 0) 0.0 else 0.30, hatGain = 0.035)
        }
        if (section >= 1) song.melody(bar, motifs[bar - 8], scale, 0.16)
    }
    return song.master(wet = 0.20)
}

/** Zen: slow pads, a low drone and a few bells. F major, 60 bpm, 24 bars (96 seconds). */
fun zenMusic(): Stereo {
    val song = Song(
        bpm = 60.0,
        progression = listOf(maj9(41), min7(45), min9(38), maj7(46), maj9(41), six(36), min9(38), sus7(36)),
        bars = 24,
        seed = 37,
    )
    val scale = intArrayOf(65, 67, 69, 72, 74, 77, 79, 81)
    for (bar in 0 until song.bars) {
        song.pad(bar, 0.12, attack = 1.8, release = 3.2)
        val droneHz = midiToHz(song.chordAt(bar).root.toDouble())
        song.out.add(seconds(bar * 4.0), seconds(6.0), 0.16, 0.0) { t -> kotlin.math.sin(TAU * droneHz * t) * envelope(t, 1.5, 3.5, 2.5) }
        // A bell phrase in two bars out of three, so there is room to breathe.
        if (bar % 3 != 2) song.melody(bar, 100 + bar, scale, 0.11)
    }
    return song.master(wet = 0.40)
}
