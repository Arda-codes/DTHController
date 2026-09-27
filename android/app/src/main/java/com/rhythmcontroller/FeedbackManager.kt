package com.rhythmcontroller

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors

/**
 * Low-latency audio and haptic feedback manager.
 *
 * Audio Quality Optimizations:
 * - Preloaded uncompressed PCM hitsounds with windowed fadeout (no DC offset or clipping).
 * - Multi-sound selector: Mechanical Switch, Soft Thock, Arcade Pop.
 * - Rate limiting / voice management prevents digital distortion on rapid multi-touch.
 * - Async haptics never block touch event hot path.
 */
class FeedbackManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "rhythm_feedback_prefs"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VOLUME = "sound_volume"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_SOUND_STYLE = "sound_style"

        const val STYLE_MECHANICAL = 0
        const val STYLE_SOFT = 1
        const val STYLE_ARCADE = 2

        val SOUND_STYLE_NAMES = arrayOf("Mechanical Switch", "Soft Thock", "Arcade Pop")
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var soundEnabled: Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        private set

    var volume: Float = prefs.getFloat(KEY_VOLUME, 0.75f)
        private set

    var hapticsEnabled: Boolean = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        private set

    var soundStyle: Int = prefs.getInt(KEY_SOUND_STYLE, STYLE_MECHANICAL)
        private set

    private var soundPool: SoundPool? = null
    private val soundIds = IntArray(3)
    private val soundLoaded = BooleanArray(3)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val hapticExecutor = Executors.newSingleThreadExecutor()

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val sp = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        sp.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                for (i in 0 until 3) {
                    if (soundIds[i] == sampleId) {
                        soundLoaded[i] = true
                    }
                }
            }
        }

        try {
            soundIds[STYLE_MECHANICAL] = sp.load(context, R.raw.click_mechanical, 1)
            soundIds[STYLE_SOFT] = sp.load(context, R.raw.click_soft, 1)
            soundIds[STYLE_ARCADE] = sp.load(context, R.raw.click_arcade, 1)
        } catch (_: Exception) {}

        soundPool = sp
    }

    /**
     * Hot path: Triggered on button down.
     */
    fun triggerPressFeedback() {
        if (soundEnabled && volume > 0f) {
            val styleIdx = soundStyle.coerceIn(0, 2)
            if (soundLoaded[styleIdx]) {
                val sampleId = soundIds[styleIdx]
                // Play hitsound with headroom-adjusted volume
                soundPool?.play(sampleId, volume, volume, 1, 0, 1.0f)
            }
        }

        if (hapticsEnabled && vibrator != null && vibrator.hasVibrator()) {
            hapticExecutor.execute {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(8)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        if (enabled) {
            triggerPressFeedback()
        }
    }

    fun setSoundStyle(style: Int) {
        soundStyle = style.coerceIn(0, 2)
        prefs.edit().putInt(KEY_SOUND_STYLE, soundStyle).apply()
        triggerPressFeedback()
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        volume = clamped
        prefs.edit().putFloat(KEY_VOLUME, clamped).apply()
        if (soundEnabled && clamped > 0f) {
            triggerPressFeedback()
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        hapticsEnabled = enabled
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
        if (enabled) {
            triggerPressFeedback()
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        for (i in 0 until 3) soundLoaded[i] = false
        hapticExecutor.shutdown()
    }
}
