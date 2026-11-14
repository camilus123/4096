package io.github.camilus123.game4096.perf

import android.app.Activity
import android.os.Looper
import io.github.camilus123.game4096.audio.AudioEngine
import io.github.camilus123.game4096.game.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrameMonitorTest {

    private val ms = 1_000_000L
    private val deadline = 8_333_333L // 120 Hz

    private fun monitor() = FrameMonitor(Robolectric.buildActivity(Activity::class.java).setup().get().window)

    @Test fun countsFramesAndSlowFramesAgainstTheDeadline() {
        val m = monitor()
        repeat(85) { m.record(3 * ms, deadline) }
        repeat(14) { m.record(8 * ms, deadline) } // just inside the deadline
        m.record(20 * ms, deadline)                // missed
        val s = m.snapshot()
        assertEquals(100, s.frames)
        assertEquals(1, s.slowFrames)
        assertEquals(1f, s.slowPercent, 0.001f)
        assertEquals(3f, s.medianMs, 0.001f)
        assertEquals(8f, s.p90Ms, 0.001f)
        assertEquals(20f, s.worstMs, 0.001f)
        assertEquals(100, m.frameCount())
    }

    @Test fun resetForgetsEverything() {
        val m = monitor()
        repeat(50) { m.record(30 * ms, deadline) }
        m.reset()
        assertEquals(0, m.snapshot().frames)
        assertEquals(0f, m.snapshot().slowPercent, 0f)
        m.record(2 * ms, deadline)
        assertEquals(1, m.snapshot().frames)
        assertEquals(0, m.snapshot().slowFrames)
    }

    @Test fun reportsSustainedSlowFramesOnlyWhileWatching() {
        val m = monitor()
        var calls = 0
        m.onSustainedSlowFrames = { calls++ }

        // Not watching: even a terrible stretch is ignored.
        repeat(FrameMonitor.WINDOW) { m.record(40 * ms, deadline) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, calls)

        m.watching = true
        // A few slow frames in a window are normal and must not trigger anything.
        repeat(FrameMonitor.WINDOW) { i -> m.record(if (i % 20 == 0) 40 * ms else 3 * ms, deadline) } // 5% slow
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, calls)

        repeat(FrameMonitor.WINDOW) { i -> m.record(if (i % 4 == 0) 40 * ms else 3 * ms, deadline) } // 25% slow
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls)
    }

    @Test fun keepsOnlyRecentFramesForPercentilesButCountsAll() {
        val m = monitor()
        repeat(FrameMonitor.CAPACITY) { m.record(50 * ms, deadline) }
        repeat(FrameMonitor.CAPACITY) { m.record(2 * ms, deadline) }
        val s = m.snapshot()
        assertEquals(2 * FrameMonitor.CAPACITY, s.frames)
        assertEquals(FrameMonitor.CAPACITY, s.slowFrames)
        assertEquals("old frames have left the percentile window", 2f, s.worstMs, 0.001f)
    }

    @Test fun aFrameReportedTwiceIsCountedOnce() {
        val m = monitor()
        // What a Xiaomi on Android 16 did: every frame of a 120 Hz screen reported twice.
        for (frame in 1L..120L) {
            m.record(4 * ms, deadline, frameId = frame * deadline)
            m.record(4 * ms, deadline, frameId = frame * deadline)
        }
        assertEquals(120, m.snapshot().frames)
        assertEquals(120, m.duplicateCount())
        m.reset()
        assertEquals(0, m.duplicateCount())
        // Without an id nothing can be told apart, so nothing is dropped.
        repeat(10) { m.record(4 * ms, deadline) }
        assertEquals(10, m.snapshot().frames)
    }

    @Test fun devicesAreSortedIntoStartingLevels() {
        val gb = 1_000_000_000L
        assertEquals(Quality.LOW, DeviceClass.classify(lowRamDevice = true, totalMemoryBytes = 8 * gb, cores = 8))
        assertEquals(Quality.LOW, DeviceClass.classify(false, 2 * gb, 8))
        assertEquals(Quality.LOW, DeviceClass.classify(false, 8 * gb, 4))
        // The reference phone: sold as 3 or 4 GB, reports a little less.
        assertEquals(Quality.MEDIUM, DeviceClass.classify(false, 2_800_000_000L, 8))
        assertEquals(Quality.MEDIUM, DeviceClass.classify(false, 3_700_000_000L, 8))
        assertEquals(Quality.HIGH, DeviceClass.classify(false, 7_600_000_000L, 8))
        assertTrue(Quality.HIGH.lower() == Quality.MEDIUM && Quality.MEDIUM.lower() == Quality.LOW && Quality.LOW.lower() == Quality.LOW)
    }

    @Test fun mergeVolumeClimbsToTheSixtyFourTileThenAlmostLevelsOff() {
        val v = FloatArray(12) { AudioEngine.mergeVolume(it) } // index 0 is the merge making 4, 4 is 64, 11 is 8192
        for (i in 1..4) assertTrue("small merges get louder with the tile", v[i] > v[i - 1] + 0.04f)
        for (i in 5..11) {
            assertTrue("big merges never get quieter", v[i] > v[i - 1])
            assertTrue("and barely louder: step ${v[i] - v[i - 1]}", v[i] - v[i - 1] < 0.015f)
        }
        assertTrue("8192 is at most 15% above 64, was ${v[11] / v[4]}", v[11] / v[4] < 1.15f)
    }
}
