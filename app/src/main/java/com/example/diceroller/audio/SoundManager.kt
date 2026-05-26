package com.example.diceroller.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.example.diceroller.R
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "SoundManager"

class SoundManager(private val context: Context) {

    private val sfxAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(sfxAttributes)
        .build()

    private var menuMusicPlayer: MediaPlayer? = null
    private var gameMusicPlayer: MediaPlayer? = null

    private var diceRollId = 0
    private var diceLandId = 0

    // Device native output rate — avoids HAL resampler artifacts on generated tones
    private val toneSampleRate: Int = run {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
    }

    // PCM buffers kept in memory; AudioTracks are built per-play and released
    private lateinit var buttonClickBuffer: ShortArray
    private lateinit var pointsBankedBuffer: ShortArray
    private lateinit var turnLostBuffer: ShortArray
    private lateinit var winFanfareBuffer: ShortArray
    private lateinit var loseSoundBuffer: ShortArray
    private lateinit var cpuThinkingBuffer: ShortArray

    private val audioExecutor = Executors.newSingleThreadExecutor()

    var enabled = true

    fun load() {
        diceRollId = soundPool.load(context, R.raw.sound_dice_roll, 1)
        diceLandId = soundPool.load(context, R.raw.sound_dice_land, 1)
        generateBuffers()
    }

    private fun generateBuffers() {
        val sr = toneSampleRate
        buttonClickBuffer = generateTone(1000.0, 0.05, sr)
        pointsBankedBuffer = generateSweep(500.0, 1200.0, 0.2, sr)
        turnLostBuffer = generateSweep(800.0, 300.0, 0.3, sr)
        winFanfareBuffer = generateFanfare(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 0.15, sr)
        loseSoundBuffer = generateFanfare(doubleArrayOf(400.0, 300.0, 200.0), 0.2, sr)
        cpuThinkingBuffer = generateTone(600.0, 0.03, sr)
    }

    private fun generateTone(freqHz: Double, durationSec: Double, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val padSamples = (sampleRate * 0.005).toInt()
        val totalSamples = numSamples + padSamples * 2
        val buffer = ShortArray(totalSamples)
        val fadeIn = (numSamples * 0.1).toInt().coerceAtLeast(1)
        val fadeOut = (numSamples * 0.25).toInt().coerceAtLeast(1)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = when {
                i < fadeIn -> i.toDouble() / fadeIn
                i >= numSamples - fadeOut -> (numSamples - i).toDouble() / fadeOut
                else -> 1.0
            }
            val sample = (sin(2.0 * PI * freqHz * t) * envelope * Short.MAX_VALUE * 0.6)
            buffer[i + padSamples] = sample.toInt().toShort()
        }
        return buffer
    }

    private fun generateSweep(startHz: Double, endHz: Double, durationSec: Double, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val padSamples = (sampleRate * 0.005).toInt()
        val totalSamples = numSamples + padSamples * 2
        val buffer = ShortArray(totalSamples)
        val fadeIn = (numSamples * 0.1).toInt().coerceAtLeast(1)
        val fadeOut = (numSamples * 0.25).toInt().coerceAtLeast(1)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val freq = startHz + (endHz - startHz) * progress
            val envelope = when {
                i < fadeIn -> i.toDouble() / fadeIn
                i >= numSamples - fadeOut -> (numSamples - i).toDouble() / fadeOut
                else -> 1.0
            }
            val sample = (sin(2.0 * PI * freq * t) * envelope * Short.MAX_VALUE * 0.6)
            buffer[i + padSamples] = sample.toInt().toShort()
        }
        return buffer
    }

    private fun generateFanfare(freqs: DoubleArray, noteDuration: Double, sampleRate: Int): ShortArray {
        val noteBuffers = freqs.map { generateTone(it, noteDuration, sampleRate) }
        val totalSize = noteBuffers.sumOf { it.size }
        val result = ShortArray(totalSize)
        var offset = 0
        for (buf in noteBuffers) {
            buf.copyInto(result, offset)
            offset += buf.size
        }
        return result
    }

    private fun playBuffer(buffer: ShortArray) {
        if (!enabled) return
        audioExecutor.execute {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(sfxAttributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(toneSampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                if (track.state != AudioTrack.STATE_INITIALIZED) return@execute
                track.write(buffer, 0, buffer.size)
                track.play()
                val durationMs = (buffer.size * 1000L) / toneSampleRate + 80
                Thread.sleep(durationMs)
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack playback error", e)
            } finally {
                try { track?.stop() } catch (_: Exception) {}
                try { track?.release() } catch (_: Exception) {}
            }
        }
    }

    // --- Public playback methods ---

    fun playDiceRoll() {
        if (enabled) soundPool.play(diceRollId, 1f, 1f, 1, 0, 1f)
    }

    fun playDiceLand() {
        if (enabled) soundPool.play(diceLandId, 1f, 1f, 1, 0, 1f)
    }

    fun playButtonClick() = playBuffer(buttonClickBuffer)
    fun playPointsBanked() = playBuffer(pointsBankedBuffer)
    fun playTurnLost() = playBuffer(turnLostBuffer)
    fun playWinFanfare() = playBuffer(winFanfareBuffer)
    fun playLoseSound() = playBuffer(loseSoundBuffer)
    fun playCpuThinking() = playBuffer(cpuThinkingBuffer)

    // --- Music ---

    private fun createMusicPlayer(resId: Int): MediaPlayer? {
        return try {
            val afd = context.resources.openRawResourceFd(resId) ?: return null
            MediaPlayer().apply {
                setAudioAttributes(musicAttributes)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(0.5f, 0.5f)
                prepare()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create music player for $resId", e)
            null
        }
    }

    fun startMenuMusic() {
        if (!enabled) return
        stopGameMusic()
        if (menuMusicPlayer == null) {
            menuMusicPlayer = createMusicPlayer(R.raw.music_menu)
        }
        menuMusicPlayer?.start()
    }

    fun stopMenuMusic() {
        menuMusicPlayer?.let {
            try { if (it.isPlaying) it.pause() } catch (_: Exception) {}
        }
    }

    fun startGameMusic() {
        if (!enabled) return
        stopMenuMusic()
        if (gameMusicPlayer == null) {
            gameMusicPlayer = createMusicPlayer(R.raw.music_game)
        }
        gameMusicPlayer?.start()
    }

    fun stopGameMusic() {
        gameMusicPlayer?.let {
            try { if (it.isPlaying) it.pause() } catch (_: Exception) {}
        }
    }

    fun pauseAll() {
        try {
            menuMusicPlayer?.let { if (it.isPlaying) it.pause() }
            gameMusicPlayer?.let { if (it.isPlaying) it.pause() }
        } catch (_: Exception) {}
    }

    fun resumeMusic(isInGame: Boolean) {
        if (!enabled) return
        if (isInGame) gameMusicPlayer?.start()
        else menuMusicPlayer?.start()
    }

    fun release() {
        soundPool.release()
        menuMusicPlayer?.release()
        gameMusicPlayer?.release()
        menuMusicPlayer = null
        gameMusicPlayer = null
        audioExecutor.shutdown()
    }
}
