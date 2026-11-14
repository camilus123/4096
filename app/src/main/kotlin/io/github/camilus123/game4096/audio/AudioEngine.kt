package io.github.camilus123.game4096.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import io.github.camilus123.game4096.R

/**
 * The real audio: SoundPool for effects (Android's low-latency path for short sounds) and a
 * looping MediaPlayer for music.
 *
 * Manners:
 *  - music asks for audio focus, pauses when another app or a phone call takes it, and
 *    resumes when it is given back;
 *  - with music off (volume 0 or muted) focus is never requested, so whatever the player was
 *    already listening to keeps playing;
 *  - everything stops while the app is in the background.
 *
 * Effects are triggered straight from the main thread, which is cheap (SoundPool only queues
 * the request). Everything to do with MediaPlayer happens on a private thread, because
 * preparing a player reads and decodes a file. Audio must never take the game down, so
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

    @Volatile private var effectsVolume = 0.8f
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
        playSample(mergeIds[index], MERGE_VOLUME)
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

    // ---- Music (everything below runs on the audio thread) -------------------------------

    private val thread = HandlerThread("audio").apply { start() }
    private val handler = Handler(thread.looper)

    private var wanted = MusicTrack.NONE
    private var playing = MusicTrack.NONE
    private var player: MediaPlayer? = null
    private var hasFocus = false
    private var pausedByFocus = false
    private var fade = 1f

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

    private val fadeStep = object : Runnable {
        override fun run() {
            fade = (fade + 0.08f).coerceAtMost(1f)
            applyMusicVolume()
            if (fade < 1f) handler.postDelayed(this, 40L)
        }
    }

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

    /** Brings the player in line with what is wanted. Safe to call at any time. */
    private fun updateMusic() {
        try {
            val audible = active && !muted && musicVolume > 0f && wanted != MusicTrack.NONE
            if (!audible) {
                player?.let { if (it.isPlaying) it.pause() }
                if (hasFocus) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    hasFocus = false
                }
                return
            }
            if (pausedByFocus) {
                player?.let { if (it.isPlaying) it.pause() }
                return
            }
            if (!hasFocus) {
                hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                if (!hasFocus) return
            }
            if (playing != wanted || player == null) {
                player?.release()
                player = null
                playing = MusicTrack.NONE
                val created = MediaPlayer.create(appContext, wanted.resource, musicAttributes, audioManager.generateAudioSessionId())
                    ?: return
                created.isLooping = true
                player = created
                playing = wanted
                fade = 0f
                handler.removeCallbacks(fadeStep)
                handler.post(fadeStep)
            }
            applyMusicVolume()
            player?.let { if (!it.isPlaying) it.start() }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Music failed", e)
            try {
                player?.release()
            } catch (ignored: RuntimeException) {
            }
            player = null
            playing = MusicTrack.NONE
        }
    }

    private fun applyMusicVolume() {
        // Perceived loudness is roughly the square of the slider position.
        val v = musicVolume * musicVolume * fade
        try {
            player?.setVolume(v, v)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not set music volume", e)
        }
    }

    companion object {
        private const val TAG = "AudioEngine"
        private const val MERGE_VOLUME = 0.75f
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
