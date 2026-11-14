package io.github.camilus123.game4096.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import io.github.camilus123.game4096.data.HapticLevel

/**
 * Vibration on merges, at a strength the player chooses.
 *
 * This drives the vibration motor directly (which is why the app holds the VIBRATE
 * permission): the system's "haptic feedback" for views has one fixed, very light strength.
 * The vibration is tagged as game/media rather than touch feedback, so the phone's own
 * touch-feedback setting does not silence it.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = try {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (v != null && v.hasVibrator()) v else null
    } catch (e: RuntimeException) {
        null
    }

    /** A tap for a merge that made the tile with this [exponent]. Merges of 128 and up are longer. */
    fun merge(exponent: Int, level: HapticLevel) {
        val v = vibrator ?: return
        if (level == HapticLevel.OFF) return
        val big = exponent >= BIG_MERGE_EXPONENT
        val millis: Long
        val amplitude: Int
        if (level == HapticLevel.HIGH) {
            // As strong as the motor goes.
            millis = if (big) 70L else 45L
            amplitude = 255
        } else {
            millis = if (big) 34L else 22L
            amplitude = if (big) 170 else 125
        }
        try {
            // Phones without amplitude control only know on and off; there the length does the work.
            val effect = VibrationEffect.createOneShot(millis, if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
            }
        } catch (e: RuntimeException) {
            Log.w("Haptics", "Could not vibrate", e)
        }
    }

    private companion object {
        const val BIG_MERGE_EXPONENT = 7
    }
}
