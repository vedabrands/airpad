package com.airpad.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.airpad.app.data.models.HapticProfile

class HapticManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var currentProfile = HapticProfile()

    fun setProfile(profile: HapticProfile) {
        currentProfile = profile
    }

    /**
     * Short tactile confirmation for standard gamepad button presses.
     */
    fun performButtonPress() {
        val intensity = currentProfile.intensity
        when (currentProfile.buttonPressPattern) {
            "LIGHT_CLICK" -> _vibrate(18L, intensity.coerceIn(0.3f, 1.0f))
            "MEDIUM_CLICK" -> _vibrate(25L, intensity.coerceIn(0.5f, 1.0f))
            "HEAVY_RUMBLE" -> _vibrate(40L, intensity)
            "DOUBLE_PULSE" -> _doublePulse(intensity)
            else -> _vibrate(18L, intensity.coerceIn(0.3f, 1.0f))
        }
    }

    /**
     * Subtle tick for analog trigger thresholds and slider adjustments.
     */
    fun performTick() {
        _vibrate(10L, currentProfile.intensity.coerceIn(0.4f, 0.7f))
    }

    /**
     * Pronounced click for mode switches and pairing events.
     */
    fun performHeavyClick() {
        _vibrate(35L, currentProfile.intensity)
    }

    /**
     * Edge-reach tick when stick hits outer boundary.
     */
    fun performEdgeTick() {
        if (!currentProfile.stickEdgeFeedback) return
        _vibrate(8L, currentProfile.intensity.coerceIn(0.3f, 0.6f))
    }

    /**
     * Turbo rapid-fire tick feedback.
     */
    fun performTurboTick() {
        if (currentProfile.turboTickPattern != "RAPID_TICK") return
        _vibrate(6L, currentProfile.intensity.coerceIn(0.2f, 0.5f))
    }

    /**
     * Trigger threshold vibration (50% or 90% mark).
     */
    fun performTriggerThreshold(isHalfWay: Boolean) {
        if (!currentProfile.triggerThresholdFeedback) return
        val duration = if (isHalfWay) 15L else 20L
        val intensity = if (isHalfWay) 0.5f else 0.7f
        _vibrate(duration, currentProfile.intensity * intensity)
    }

    private fun _vibrate(durationMs: Long, intensity: Float) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        val amplitude = (intensity * 255).toInt().coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createOneShot(durationMs, amplitude)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(durationMs)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun _doublePulse(intensity: Float) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        // Custom double pulse (no built-in DOUBLE_TICK effect in Android API)
        _vibrate(12L, intensity)
        Thread {
            try { Thread.sleep(30L) } catch (_: InterruptedException) {}
            _vibrate(12L, intensity)
        }.start()
    }
}
