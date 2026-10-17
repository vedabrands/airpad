package com.airpad.app.sensor

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * High-performance Touch Filter with velocity-based acceleration and prediction.
 * Optimized for 120-240Hz polling rate.
 */
class TouchFilter(
    var deadzone: Float = 0.5f,
    var smoothingFactor: Float = 0.15f, // Lower = more responsive
    var sensitivity: Float = 1.5f,
    /** Velocity-based acceleration curve */
    var accelerationEnabled: Boolean = true,
    var accelThreshold: Float = 30f, // pixels per frame
    var accelGain: Float = 1.8f,
    var accelExponent: Float = 1.4f,
    /** Sub-pixel interpolation for ultra-smooth movement */
    var subpixelPrecision: Boolean = true,
    /** Predictive smoothing (reduces perceived latency) */
    var predictionEnabled: Boolean = true,
    var predictionFrames: Int = 2
) {
    private var smoothedDx = 0f
    private var smoothedDy = 0f
    private var subpixelX = 0f
    private var subpixelY = 0f

    // Velocity tracking for acceleration
    private var prevDx = 0f
    private var prevDy = 0f
    private var velocityX = 0f
    private var velocityY = 0f

    /**
     * Filters raw touch delta (dx, dy) with:
     * 1. Deadzone for tremor elimination
     * 2. Velocity-based non-linear acceleration
     * 3. EMA smoothing with configurable factor
     * 4. Sub-pixel accumulation for jitter-free output
     * 5. Optional prediction for reduced perceived latency
     */
    fun processDelta(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        val magnitude = hypot(rawDx, rawDy)
        if (magnitude < deadzone) {
            // Reset velocity on deadzone
            velocityX = 0f
            velocityY = 0f
            prevDx = 0f
            prevDy = 0f
            if (subpixelPrecision) {
                smoothedDx = 0f
                smoothedDy = 0f
            }
            return Pair(0f, 0f)
        }

        // Apply deadzone reduction smoothly
        val scale = (magnitude - deadzone) / magnitude
        val effectiveDx = rawDx * scale * sensitivity
        val effectiveDy = rawDy * scale * sensitivity

        // Calculate velocity for acceleration curve
        velocityX = effectiveDx - prevDx
        velocityY = effectiveDy - prevDy
        val speed = hypot(velocityX, velocityY)

        val accelMultiplier = if (accelerationEnabled && speed > accelThreshold) {
            1.0f + accelGain * ((speed - accelThreshold) / accelThreshold).pow(accelExponent)
        } else {
            1.0f
        }

        val acceleratedDx = effectiveDx * accelMultiplier
        val acceleratedDy = effectiveDy * accelMultiplier

        prevDx = effectiveDx
        prevDy = effectiveDy

        // Exponential Moving Average smoothing
        smoothedDx = smoothingFactor * smoothedDx + (1f - smoothingFactor) * acceleratedDx
        smoothedDy = smoothingFactor * smoothedDy + (1f - smoothingFactor) * acceleratedDy

        // Prediction: extrapolate based on current velocity
        var predDx = smoothedDx
        var predDy = smoothedDy
        if (predictionEnabled) {
            predDx += velocityX * predictionFrames * 0.5f
            predDy += velocityY * predictionFrames * 0.5f
        }

        if (subpixelPrecision) {
            subpixelX += predDx
            subpixelY += predDy

            val intDx = subpixelX.toInt().toFloat()
            val intDy = subpixelY.toInt().toFloat()

            subpixelX -= intDx
            subpixelY -= intDy

            return Pair(intDx, intDy)
        } else {
            return Pair(predDx, predDy)
        }
    }

    fun reset() {
        smoothedDx = 0f
        smoothedDy = 0f
        subpixelX = 0f
        subpixelY = 0f
        velocityX = 0f
        velocityY = 0f
        prevDx = 0f
        prevDy = 0f
    }
}