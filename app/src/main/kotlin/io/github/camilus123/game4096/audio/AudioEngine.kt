package io.github.camilus123.game4096.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import io.github.camilus123.game4096.R
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * The real audio: SoundPool for effects (Android's low-latency path for short sounds), and
 * for music a decoded track looped through an AudioTrack.
 *
 * Manners:
 *  - music asks for audio focus, pauses when another app or a phone call takes it, and
 *    resumes when it is given back;
 *  - with music off (volume 0 or muted) focus is never requested, so whatever the player was
 *    already listening to keeps playing;
 *  - everything stops while the app is in the background.
 *
 * Effects are triggered straight from the main thread, which is cheap (SoundPool only queues
 * the request). Music is controlled from a private thread, decoded on another and written
 * to the audio device by a third, so none of it touches the main thread. Audio must never take the game down, so
 * every platform call is guarded.
 */
class AudioEngine(context: Context) : GameAudio {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---- Effects -----------------------------------------------------------------------

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val sfxIds = IntArray(Sfx.entries.size)
    private val mergeIds = IntArray(MERGE_RESOURCES.size)

    /** Indexed by SoundPool sample id; true once the sample is decoded and playable. */
    private val loaded = BooleanArray(64)

    @Volatile private var effectsVolume = 0.5f
    @Volatile private var musicVolume = 0.5f
    @Volatile private var muted = false
    @Volatile private var active = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId in loaded.indices) loaded[sampleId] = true
        }
        try {
            for (s in Sfx.entries) sfxIds[s.ordinal] = pool.load(appContext, s.resource, 1)
            for (i in MERGE_RESOURCES.indices) mergeIds[i] = pool.load(appContext, MERGE_RESOURCES[i], 1)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not load sound effects", e)
        }
    }

    override fun play(sfx: Sfx) = playSample(sfxIds[sfx.ordinal], sfx.volume)

    override fun playMerge(exponent: Int) {
        val index = (exponent - FIRST_MERGE_EXPONENT).coerceIn(0, mergeIds.size - 1)
        // Small merges happen on nearly every move and stay in the background; big ones are rarer and louder.
        playSample(mergeIds[index], (0.26f + 0.055f * index).coerceAtMost(0.8f))
    }

    private fun playSample(id: Int, volume: Float) {
        if (!active || muted || id <= 0 || id >= loaded.size || !loaded[id]) return
        val v = volume * effectsVolume
        if (v <= 0f) return
        try {
            pool.play(id, v, v, 1, 0, 1f)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not play effect", e)
        }
    }

    // ---- Music --------------------------------------------------------------------------
    //
    // A track is decoded once into memory and fed to an AudioTrack by a small writer thread
    // that wraps from the last sample straight to the first. That makes the loop exact to the
    // sample on every phone; MediaPlayer's own looping leaves a short gap on many. Changing
    // track starts the new one silent and fades the two across each other.
    //
    // The fields below are touched only on the control thread ("audio").

    private val thread = HandlerThread("audio").apply { start() }
    private val handler = Handler(thread.looper)
    private val decoder = Executors.newSingleThreadExecutor { r -> Thread(r, "audio-decode").apply { priority = Thread.MIN_PRIORITY + 1 } }

    private var wanted = MusicTrack.NONE
    private var current: LoopPlayer? = null
    private var decoding = MusicTrack.NONE
    private val pcmCache = LinkedHashMap<MusicTrack, Pcm>()
    private val undecodable = HashSet<MusicTrack>()
    private var hasFocus = false
    private var pausedByFocus = false

    // Fallback for a track that could not be decoded: plain MediaPlayer looping.
    private var fallback: MediaPlayer? = null
    private var fallbackTrack = MusicTrack.NONE

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        handler.post {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasFocus = true
                    pausedByFocus = false
                    updateMusic()
                }
                // A call, a navigation prompt, another player. Ducking is done by the system.
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    hasFocus = change != AudioManager.AUDIOFOCUS_LOSS && hasFocus
                    pausedByFocus = true
                    updateMusic()
                }
            }
        }
    }

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(musicAttributes)
        .setOnAudioFocusChangeListener(focusListener, handler)
        .build()

    override fun setMusic(track: MusicTrack) {
        handler.post {
            wanted = track
            updateMusic()
        }
    }

    override fun setVolumes(effects: Float, music: Float, muted: Boolean) {
        effectsVolume = effects.coerceIn(0f, 1f)
        musicVolume = music.coerceIn(0f, 1f)
        this.muted = muted
        handler.post { updateMusic() }
    }

    override fun setActive(active: Boolean) {
        this.active = active
        handler.post {
            // Coming back to the app is the player asking for sound again.
            if (active) pausedByFocus = false
            updateMusic()
        }
        if (!active) {
            try {
                pool.autoPause()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not pause effects", e)
            }
        }
    }

    /** Perceived loudness is roughly the square of the slider position. */
    private fun musicGain(): Float = musicVolume * musicVolume

    /** Brings the players in line with what is wanted. Safe to call at any time. */
    private fun updateMusic() {
        try {
            val audible = active && !muted && musicVolume > 0f && wanted != MusicTrack.NONE
            if (!audible || pausedByFocus) {
                current?.setPaused(true)
                fallback?.let { if (it.isPlaying) it.pause() }
                if (!audible && hasFocus) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    hasFocus = false
                }
                return
            }
            if (!hasFocus) {
                hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (!hasFocus) return
            }
            if (wanted in undecodable) {
                playFallback()
                return
            }
            stopFallback()
            if (current?.which != wanted) {
                val pcm = pcmCache[wanted]
                if (pcm == null) {
                    // Keep the old track going while the new one is decoded, then come back here.
                    requestDecode(wanted)
                } else {
                    current?.fadeOutAndStop()
                    current = LoopPlayer(wanted, pcm).also { it.start() }
                }
            }
            current?.let {
                it.targetGain = musicGain()
                it.setPaused(false)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Music failed", e)
        }
    }

    private fun requestDecode(track: MusicTrack) {
        if (decoding == track) return
        decoding = track
        decoder.execute {
            val pcm = try {
                decode(track)
            } catch (e: Exception) {
                Log.w(TAG, "Could not decode ${track.name}", e)
                null
            }
            handler.post {
                if (decoding == track) decoding = MusicTrack.NONE
                if (pcm == null) {
                    undecodable.add(track)
                } else {
                    // Two decoded tracks are kept (about 27 MB), so going between the menu and a
                    // game does not decode again. Older ones are dropped, never the one playing.
                    pcmCache[track] = pcm
                    val it = pcmCache.keys.iterator()
                    while (pcmCache.size > MAX_CACHED_TRACKS && it.hasNext()) {
                        val key = it.next()
                        if (key != track && key != current?.which) it.remove()
                    }
                }
                updateMusic()
            }
        }
    }

    /** Decodes a whole OGG resource to 16-bit PCM. Runs on the decode thread. */
    private fun decode(track: MusicTrack): Pcm? {
        val afd = appContext.resources.openRawResourceFd(track.resource) ?: return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    format = f
                    break
                }
            }
            if (format == null) return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val decoderCodec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoderCodec
            decoderCodec.configure(format, null, null, 0)
            decoderCodec.start()

            var samples = ShortArray(track.frames * channels + 16384)
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleRounds = 0
            while (!outputDone && idleRounds < 2000) {
                var progressed = false
                if (!inputDone) {
                    val inIndex = decoderCodec.dequeueInputBuffer(5_000)
                    if (inIndex >= 0) {
                        val buffer = decoderCodec.getInputBuffer(inIndex) ?: return null
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoderCodec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoderCodec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }
                val outIndex = decoderCodec.dequeueOutputBuffer(info, 5_000)
                if (outIndex >= 0) {
                    val buffer = decoderCodec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val n = shorts.remaining()
                        if (count + n > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, count + n))
                        shorts.get(samples, count, n)
                        count += n
                    }
                    decoderCodec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    progressed = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = decoderCodec.outputFormat
                    sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        out.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT
                    ) {
                        return null
                    }
                    progressed = true
                }
                idleRounds = if (progressed) 0 else idleRounds + 1
            }
            if (!outputDone || channels !in 1..2) return null
            // The generator knows the exact length of the loop; cut anything a decoder appended.
            val exact = minOf(count, track.frames * channels)
            if (exact < sampleRate * channels) return null // under a second: something went wrong
            if (count != track.frames * channels) Log.i(TAG, "${track.name}: decoded ${count / channels} frames, expected ${track.frames}")
            return Pcm(samples, exact, sampleRate, channels)
        } finally {
            try {
                codec?.stop()
            } catch (ignored: RuntimeException) {
            }
            try {
                codec?.release()
            } catch (ignored: RuntimeException) {
            }
            extractor.release()
            afd.close()
        }
    }

    /** [length] samples of [samples] are the loop; the array may be a little longer. */
    private class Pcm(val samples: ShortArray, val length: Int, val sampleRate: Int, val channels: Int)

    /**
     * Plays one decoded track in an endless loop on its own thread. Volume changes, including
     * the fade in at the start and the fade out before stopping, are ramps, never steps.
     */
    private inner class LoopPlayer(val which: MusicTrack, private val pcm: Pcm) : Thread("music-" + which.name) {
        @Volatile var targetGain = 0f
        @Volatile private var paused = false
        @Volatile private var stopWhenSilent = false
        @Volatile private var quit = false
        private val lock = Object()

        fun setPaused(value: Boolean) {
            if (paused == value) return
            paused = value
            synchronized(lock) { lock.notifyAll() }
        }

        fun fadeOutAndStop() {
            stopWhenSilent = true
            targetGain = 0f
            if (paused) quit = true // nobody is listening: no need to fade
            synchronized(lock) { lock.notifyAll() }
        }

        override fun run() {
            var track: AudioTrack? = null
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val mask = if (pcm.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                val minBytes = AudioTrack.getMinBufferSize(pcm.sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
                val out = AudioTrack.Builder()
                    .setAudioAttributes(musicAttributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(pcm.sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(mask)
                            .build(),
                    )
                    .setBufferSizeInBytes(maxOf(minBytes * 2, 16384))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track = out
                out.setVolume(0f)

                val chunk = CHUNK_FRAMES * pcm.channels
                val gainStep = CHUNK_FRAMES / (FADE_SECONDS * pcm.sampleRate)
                var gain = 0f
                var position = 0
                var playing = false
                while (!quit) {
                    if (paused) {
                        if (playing) {
                            out.pause()
                            playing = false
                        }
                        synchronized(lock) { while (paused && !quit) lock.wait() }
                        continue
                    }
                    if (!playing) {
                        out.play()
                        playing = true
                    }
                    val target = targetGain
                    if (gain != target) {
                        gain = if (gain < target) minOf(target, gain + gainStep) else maxOf(target, gain - gainStep)
                        out.setVolume(gain)
                    }
                    if (stopWhenSilent && gain <= 0f) break
                    val n = minOf(chunk, pcm.length - position)
                    val written = out.write(pcm.samples, position, n)
                    if (written < 0) break
                    position += written
                    if (position >= pcm.length) position = 0 // the loop: last sample, then the first
                }
            } catch (e: InterruptedException) {
                // Asked to stop.
            } catch (e: RuntimeException) {
                Log.w(TAG, "Music playback failed", e)
            } finally {
                try {
                    track?.pause()
                    track?.flush()
                    track?.release()
                } catch (ignored: RuntimeException) {
                }
            }
        }
    }

    private fun playFallback() {
        current?.fadeOutAndStop()
        current = null
        if (fallbackTrack != wanted || fallback == null) {
            stopFallback()
            val created = MediaPlayer.create(appContext, wanted.resource, musicAttributes, audioManager.generateAudioSessionId())
                ?: return
            created.isLooping = true
            fallback = created
            fallbackTrack = wanted
        }
        val v = musicGain()
        fallback?.let {
            it.setVolume(v, v)
            if (!it.isPlaying) it.start()
        }
    }

    private fun stopFallback() {
        try {
            fallback?.release()
        } catch (ignored: RuntimeException) {
        }
        fallback = null
        fallbackTrack = MusicTrack.NONE
    }

    companion object {
        private const val TAG = "AudioEngine"
        private const val MAX_CACHED_TRACKS = 2
        private const val CHUNK_FRAMES = 1024

        /** Time for a full-scale volume change: the fade in, the fade out and the crossfade. */
        private const val FADE_SECONDS = 1.4f
        private const val FIRST_MERGE_EXPONENT = 2

        /** Merge sounds by the exponent of the tile created, from 4 (exponent 2) to 8192 (13). */
        private val MERGE_RESOURCES = intArrayOf(
            R.raw.sfx_merge_02, R.raw.sfx_merge_03, R.raw.sfx_merge_04, R.raw.sfx_merge_05,
            R.raw.sfx_merge_06, R.raw.sfx_merge_07, R.raw.sfx_merge_08, R.raw.sfx_merge_09,
            R.raw.sfx_merge_10, R.raw.sfx_merge_11, R.raw.sfx_merge_12, R.raw.sfx_merge_13,
        )

        @Volatile private var instance: AudioEngine? = null

        fun get(context: Context): AudioEngine =
            instance ?: synchronized(this) {
                instance ?: AudioEngine(context).also { instance = it }
            }
    }
}
