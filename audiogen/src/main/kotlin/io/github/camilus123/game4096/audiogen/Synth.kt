package io.github.camilus123.game4096.audiogen

import java.io.DataOutputStream
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val SAMPLE_RATE = 44100
const val TAU = 2.0 * PI

fun midiToHz(midi: Double): Double = 440.0 * 2.0.pow((midi - 69.0) / 12.0)

fun seconds(s: Double): Int = (s * SAMPLE_RATE).toInt()

/**
 * A stereo buffer. With [loop] set, anything written past the end wraps to the start, so the
 * tail of the last note and the reverb of the last bar land on the first bar. That is what
 * makes a music track loop with no seam: the join is part of the audio, not a cut.
 */
class Stereo(val length: Int, val loop: Boolean) {
    val left = FloatArray(length)
    val right = FloatArray(length)

    /** Mixes [count] samples of [wave] (a function of time in seconds) starting at [start]. */
    inline fun add(start: Int, count: Int, gain: Double, pan: Double, wave: (t: Double) -> Double) {
        // Equal-power pan: -1 is left, 0 centre, 1 right.
        val angle = (pan + 1.0) * PI / 4.0
        val gl = gain * cos(angle)
        val gr = gain * sin(angle)
        for (i in 0 until count) {
            var at = start + i
            if (at >= length) {
                if (!loop) break
                at %= length
            }
            val v = wave(i.toDouble() / SAMPLE_RATE)
            left[at] += (v * gl).toFloat()
            right[at] += (v * gr).toFloat()
        }
    }

    fun peak(): Float {
        var p = 0f
        for (i in 0 until length) {
            p = maxOf(p, abs(left[i]), abs(right[i]))
        }
        return p
    }

    fun rms(): Double {
        var sum = 0.0
        for (i in 0 until length) sum += left[i] * left[i] + right[i] * right[i]
        return sqrt(sum / (2.0 * length))
    }

    /**
     * How loud this sounds, as opposed to how big its samples are: the highest level reached
     * over any 50 ms, measured through an A-weighting filter, which discounts low notes the
     * way the ear does. Two sounds with the same value here are about equally loud to listen
     * to, even if one is a dull knock and the other a bright bell.
     */
    fun loudness(): Double {
        // Standard A-weighting filter for 44.1 kHz.
        val b = doubleArrayOf(0.255741125204258, -0.511482250408516, -0.255741125204258, 1.022964500817032, -0.255741125204258, -0.511482250408516, 0.255741125204258)
        val a = doubleArrayOf(1.0, -4.019576181115832, 6.189406442920694, -4.453198903544116, 1.420842949621876, -0.141825473830304, 0.004351177233495)
        val x = DoubleArray(length) { (left[it] + right[it]) * 0.5 }
        val y = DoubleArray(length)
        for (n in 0 until length) {
            var acc = 0.0
            for (k in 0..6) if (n - k >= 0) acc += b[k] * x[n - k]
            for (k in 1..6) if (n - k >= 0) acc -= a[k] * y[n - k]
            y[n] = acc
        }
        val window = seconds(0.05)
        val hop = seconds(0.01)
        var loudest = 0.0
        var start = 0
        while (start == 0 || start + window <= length) {
            var sum = 0.0
            val end = min(start + window, length)
            for (i in start until end) sum += y[i] * y[i]
            loudest = maxOf(loudest, sqrt(sum / (end - start)))
            start += hop
        }
        return loudest
    }

    /** Scales by [gain]. */
    fun scale(gain: Double) {
        val g = gain.toFloat()
        for (i in 0 until length) {
            left[i] *= g
            right[i] *= g
        }
    }

    /** Scales so the loudest sample sits at [target] (1.0 is full scale). */
    fun normalize(target: Double) {
        val p = peak()
        if (p <= 0f) return
        val g = (target / p).toFloat()
        for (i in 0 until length) {
            left[i] *= g
            right[i] *= g
        }
    }

    /** Short linear fades so a one-shot effect starts and ends at silence. */
    fun fadeEdges(inSamples: Int, outSamples: Int) {
        for (i in 0 until min(inSamples, length)) {
            val g = i.toFloat() / inSamples
            left[i] *= g
            right[i] *= g
        }
        for (i in 0 until min(outSamples, length)) {
            val g = i.toFloat() / outSamples
            left[length - 1 - i] *= g
            right[length - 1 - i] *= g
        }
    }

    /**
     * Adds a small-room reverb (four damped comb filters into two all-pass filters per
     * channel). For a looping buffer the filters are first run once over the whole track to
     * fill their memory, so the reverb at the very start already contains the end.
     */
    fun reverb(wet: Double, feedback: Double = 0.80) {
        reverbChannel(left, wet, feedback, 0)
        reverbChannel(right, wet, feedback, 23)
    }

    private fun reverbChannel(data: FloatArray, wet: Double, feedback: Double, spread: Int) {
        val combDelays = intArrayOf(1557 + spread, 1617 + spread, 1491 + spread, 1422 + spread)
        val combs = Array(4) { FloatArray(combDelays[it]) }
        val combPos = IntArray(4)
        val damp = FloatArray(4)
        val apDelays = intArrayOf(225 + spread, 556 + spread)
        val aps = Array(2) { FloatArray(apDelays[it]) }
        val apPos = IntArray(2)
        val fb = feedback.toFloat()
        val out = FloatArray(length)
        val passes = if (loop) 2 else 1
        for (pass in 0 until passes) {
            for (i in 0 until length) {
                val x = data[i]
                var sum = 0f
                for (c in 0 until 4) {
                    val buf = combs[c]
                    val y = buf[combPos[c]]
                    damp[c] = y * 0.75f + damp[c] * 0.25f
                    buf[combPos[c]] = x + damp[c] * fb
                    combPos[c] = (combPos[c] + 1) % buf.size
                    sum += y
                }
                var v = sum * 0.25f
                for (a in 0 until 2) {
                    val buf = aps[a]
                    val delayed = buf[apPos[a]]
                    val input = v + delayed * 0.5f
                    buf[apPos[a]] = input
                    apPos[a] = (apPos[a] + 1) % buf.size
                    v = delayed - input * 0.5f
                }
                out[i] = v
            }
        }
        val w = wet.toFloat()
        for (i in 0 until length) data[i] += out[i] * w
    }

    fun writeWav(file: File) {
        file.parentFile?.mkdirs()
        val dataBytes = length * 4
        DataOutputStream(file.outputStream().buffered()).use { o ->
            fun le32(v: Int) { o.write(v); o.write(v shr 8); o.write(v shr 16); o.write(v shr 24) }
            fun le16(v: Int) { o.write(v); o.write(v shr 8) }
            o.writeBytes("RIFF"); le32(36 + dataBytes); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(2); le32(SAMPLE_RATE); le32(SAMPLE_RATE * 4); le16(4); le16(16)
            o.writeBytes("data"); le32(dataBytes)
            for (i in 0 until length) {
                le16((left[i].coerceIn(-1f, 1f) * 32767f).toInt())
                le16((right[i].coerceIn(-1f, 1f) * 32767f).toInt())
            }
        }
    }
}

// ---- Instruments: each is a function of time since the note started ------------------------

/** Rises over [attack] seconds, holds, and falls over [release] seconds after [held] seconds. */
fun envelope(t: Double, attack: Double, held: Double, release: Double): Double {
    val a = if (t < attack) t / attack else 1.0
    val r = if (t > held) (1.0 - (t - held) / release).coerceAtLeast(0.0) else 1.0
    return a * r * r
}

/** Soft electric piano: a sine bent by itself, brighter at the start of the note. */
fun epiano(hz: Double, t: Double, held: Double): Double {
    val w = TAU * hz * t
    val index = 1.4 * exp(-5.0 * t)
    val body = sin(w + index * sin(w)) + 0.18 * sin(2.0 * w) * exp(-4.0 * t)
    return body * exp(-2.0 * t) * envelope(t, 0.004, held, 0.25)
}

/** Glassy bell with an inharmonic shimmer that dies quickly. */
fun bell(hz: Double, t: Double): Double {
    val w = TAU * hz * t
    return (sin(w + 2.2 * exp(-3.0 * t) * sin(3.5 * w)) * exp(-1.7 * t) +
        0.25 * sin(2.0 * w) * exp(-3.5 * t)) * envelope(t, 0.003, 3.0, 0.5)
}

/** Slow, wide pad: three slightly detuned sines and a quiet octave. */
fun pad(hz: Double, t: Double, held: Double, attack: Double = 0.9, release: Double = 1.6): Double {
    val w = TAU * hz * t
    val tremolo = 1.0 + 0.06 * sin(TAU * 0.23 * t)
    return (sin(w) + sin(w * 1.004) + sin(w * 0.996) + 0.3 * sin(2.0 * w)) / 3.3 *
        tremolo * envelope(t, attack, held, release)
}

/** Round bass: a sine with a little second harmonic. */
fun bass(hz: Double, t: Double, held: Double): Double {
    val w = TAU * hz * t
    return (sin(w) + 0.25 * sin(2.0 * w)) * (0.6 + 0.4 * exp(-6.0 * t)) * envelope(t, 0.008, held, 0.09)
}

/** Soft kick: a sine dropping from 110 Hz to 45 Hz. */
fun kick(t: Double): Double {
    val phase = TAU * (45.0 * t + (110.0 - 45.0) * (1.0 - exp(-t * 30.0)) / 30.0)
    return sin(phase) * exp(-t * 16.0)
}

/** Deterministic noise source, so every run produces the same files. */
class Noise(seed: Long) {
    private val random = Random(seed)
    private var last = 0.0
    fun white(): Double = random.nextDouble() * 2.0 - 1.0

    /** White noise with the lows removed: a hiss. */
    fun bright(): Double {
        val x = white()
        val y = x - last
        last = x
        return y * 0.5
    }
}
