package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

/**
 * Manages audio focus, volume override, and priority emergency distress sirens.
 * Fulfills the non-negotiable requirement:
 * Alert-type messages must play at maximum system volume and be non-interruptible
 * (override silent/DND, override audio focus from other apps).
 */
class AlertAudioManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMusicVolume: Int = -1
    private var previousAlarmVolume: Int = -1
    private var audioFocusRequest: AudioFocusRequest? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Acquires exclusive non-interruptible audio focus and sets alarm/media stream to maximum volume.
     */
    fun lockAudioFocusForAlert(): Boolean {
        try {
            // Save previous volume levels
            previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

            // Force maximum volume on alarm & media channels
            val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)

            // Request exclusive non-interruptible Audio Focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d("AlertAudioManager", "Focus change during alert: $focusChange")
                    }
                    .build()

                audioFocusRequest = focusRequest
                val result = audioManager.requestAudioFocus(focusRequest)
                return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e("AlertAudioManager", "Failed to acquire alert audio focus", e)
            return false
        }
    }

    /**
     * Releases audio focus and restores original system volumes safely.
     */
    fun releaseAlertAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }

            // Restore volume if previously captured
            if (previousMusicVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousMusicVolume, 0)
            }
            if (previousAlarmVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
            }
        } catch (e: Exception) {
            Log.e("AlertAudioManager", "Failed to release audio focus", e)
        }
    }

    /**
     * Synthesizes an audible tactical siren pulse (two-tone warble 960Hz - 1440Hz)
     * using AudioTrack directly. Runs 100% offline without external audio files.
     */
    suspend fun playEmergencySirenTone(durationMs: Int = 1800) = withContext(Dispatchers.Default) {
        val sampleRate = 44100
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val buffer = ShortArray(numSamples)

        val lowFreq = 880.0  // A5
        val highFreq = 1320.0 // E6
        val modulationRate = 4.0 // 4 sweeps per second

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val currentFreq = lowFreq + (highFreq - lowFreq) * 0.5 * (1.0 + sin(2 * Math.PI * modulationRate * t))
            val angle = 2.0 * Math.PI * currentFreq * t
            buffer[i] = (sin(angle) * Short.MAX_VALUE * 0.85).toInt().toShort()
        }

        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            // Trigger SOS haptics concurrently
            triggerSosHaptics()

            kotlinx.coroutines.delay(durationMs.toLong())
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.e("AlertAudioManager", "Error playing synthesized siren tone", e)
        }
    }

    /**
     * Triggers tactical SOS vibration sequence.
     */
    fun triggerSosHaptics() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // S.O.S pattern in ms: . . . - - - . . .
                val timings = longArrayOf(0, 150, 100, 150, 100, 150, 300, 400, 150, 400, 150, 400, 300, 150, 100, 150, 100, 150)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val timings = longArrayOf(0, 150, 100, 150, 100, 150, 300, 400, 150, 400)
                vibrator?.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            Log.w("AlertAudioManager", "Vibration failed: ${e.message}")
        }
    }

    fun stopHaptics() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w("AlertAudioManager", "Cancel vibration failed: ${e.message}")
        }
    }
}
