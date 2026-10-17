package com.airpad.app.sensor

import com.airpad.app.data.models.StickConfig
import com.airpad.app.data.models.StickCurveType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign

class StickFilter(
    var config: StickConfig = StickConfig()
) {
    private var prevX = 0f
    private var prevY = 0f

    // Dynamic anti-drift zero bias tracking
    private var driftBiasX = 0f
    private var driftBiasY = 0f
    private var driftSampleCount = 0

    // Edge reach state tracking to prevent duplicate haptic firings
    private var wasAtEdge = false

    /**
     * Filters raw thumbstick inputs in range [-1.0f, 1.0f].
     * Applies:
     * 1. Anti-drift baseline subtraction
     * 2. Inner and Outer Radial Deadzone normalization
     * 3. Configured Response Curves (Linear, Exponential, Logarithmic, Wide-Center, Digital Step)
     * 4. Directional sensitivity & X/Y axis inversion
     * 5. Exponential Moving Average (EMA) smoothing with spring tension snapback
     *
     * Returns:
     * - `filteredShorts`: Pair<Short, Short> in range [-32768, 32767] for Xbox 360 controller emulation
     * - `isAtEdge`: Boolean indicating if stick crossed outer radius threshold
     */
    fun processStick(
        rawX: Float,
        rawY: Float,
        onEdgeReached: (() -> Unit)? = null
    ): Pair<Short, Short> {
        // 1. Anti-drift compensation when near rest
        var inputX = rawX
        var inputY = rawY

        if (config.antiDriftCompensation) {
            val rawMag = hypot(rawX, rawY)
            if (rawMag < config.deadzone) {
                driftBiasX = 0.95f * driftBiasX + 0.05f * rawX
                driftBiasY = 0.95f * driftBiasY + 0.05f * rawY
                driftSampleCount++
            }
            if (driftSampleCount > 20) {
                inputX -= driftBiasX
                inputY -= driftBiasY
            }
        }

        val magnitude = hypot(inputX, inputY)
        val innerDz = config.deadzone.coerceIn(0.0f, 0.4f)
        val outerDz = config.outerDeadzone.coerceIn(0.5f, 1.0f)

        // Deadzone check
        if (magnitude < innerDz || magnitude == 0f) {
            // Apply Spring Tension Simulation for smooth return-to-center snapback
            if (config.springTension > 0f) {
                prevX *= (1.0f - config.springTension).coerceIn(0.5f, 0.95f)
                prevY *= (1.0f - config.springTension).coerceIn(0.5f, 0.95f)
                if (abs(prevX) < 0.001f) prevX = 0f
                if (abs(prevY) < 0.001f) prevY = 0f
            } else {
                prevX = 0f
                prevY = 0f
            }
            wasAtEdge = false
            val finalShortX = (prevX * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            val finalShortY = (prevY * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            return Pair(finalShortX, finalShortY)
        }

        // 2. Radial Deadzone Normalization [0.0f .. 1.0f]
        val clampedMag = magnitude.coerceIn(innerDz, outerDz)
        val normalizedMag = ((clampedMag - innerDz) / (outerDz - innerDz)).coerceIn(0f, 1f)

        // Trigger Edge Reach Haptics
        val isAtEdge = normalizedMag >= 0.95f
        if (isAtEdge && !wasAtEdge && config.stickEdgeHaptics) {
            onEdgeReached?.invoke()
        }
        wasAtEdge = isAtEdge

        // 3. Response Curve Transformation
        val curvedMag = applyCurve(normalizedMag, config.curveType, config.curveExponent)

        // 4. Directional Vectors & Axis Inversion
        val dirX = inputX / magnitude
        val dirY = inputY / magnitude

        val signX = if (config.invertX) -1.0f else 1.0f
        val signY = if (config.invertY) -1.0f else 1.0f

        val targetX = (dirX * curvedMag * config.sensitivityX * signX).coerceIn(-1.0f, 1.0f)
        val targetY = (dirY * curvedMag * config.sensitivityY * signY).coerceIn(-1.0f, 1.0f)

        // 5. Exponential Moving Average (EMA) Smoothing
        val smoothFactor = config.smoothingFactor.coerceIn(0f, 0.9f)
        val smoothX = (smoothFactor * prevX) + ((1f - smoothFactor) * targetX)
        val smoothY = (smoothFactor * prevY) + ((1f - smoothFactor) * targetY)

        prevX = smoothX
        prevY = smoothY

        val finalShortX = (smoothX * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        val finalShortY = (smoothY * 32767f).toInt().coerceIn(-32768, 32767).toShort()

        return Pair(finalShortX, finalShortY)
    }

    private fun applyCurve(
        norm: Float,
        curveType: StickCurveType,
        exponent: Float
    ): Float {
        val exp = exponent.coerceIn(1.0f, 4.0f)
        return when (curveType) {
            StickCurveType.LINEAR -> norm
            StickCurveType.EXPONENTIAL -> norm.pow(exp)
            StickCurveType.LOGARITHMIC -> 1.0f - (1.0f - norm).pow(exp)
            StickCurveType.WIDE_CENTER -> {
                // S-Curve: Flattened center for micro-aim stability, sharp ramp at edges
                if (norm < 0.5f) {
                    0.5f * (norm * 2.0f).pow(exp)
                } else {
                    1.0f - 0.5f * ((1.0f - norm) * 2.0f).pow(exp)
                }
            }
            StickCurveType.DIGITAL_STEP -> {
                if (norm >= 0.35f) 1.0f else 0.0f
            }
        }
    }

    fun updateConfig(newConfig: StickConfig) {
        this.config = newConfig
    }

    fun reset() {
        prevX = 0f
        prevY = 0f
        driftBiasX = 0f
        driftBiasY = 0f
        driftSampleCount = 0
        wasAtEdge = false
    }
}
