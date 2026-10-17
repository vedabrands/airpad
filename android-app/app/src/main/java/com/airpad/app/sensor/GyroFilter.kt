package com.airpad.app.sensor

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign

class GyroFilter(
    var deadzone: Float = 0.010f,
    var smoothingFactor: Float = 0.35f,
    var sensitivityX: Float = 1.4f,
    var sensitivityY: Float = 1.4f,
    var aimingSensitivityX: Float = 0.8f,
    var aimingSensitivityY: Float = 0.8f,
    var holdToAimEnabled: Boolean = false,
    var nonLinearCurveExponent: Float = 1.4f,
    var accelerationEnabled: Boolean = true,
    var accelThreshold: Float = 0.5f,
    var accelGain: Float = 1.4f,
    var accelExponent: Float = 1.3f,
    var autoDriftCalibration: Boolean = true
) {
    private var prevSmoothX = 0f
    private var prevSmoothY = 0f
    private var subpixelX = 0f
    private var subpixelY = 0f

    // Dynamic gyro drift bias tracking
    private var driftBiasX = 0f
    private var driftBiasY = 0f
    private var driftSampleCount = 0
    private val driftLock = Any()

    private var isAimingModeActive = false

    /**
     * Laser-Pointer Gyro Mouse Delta Processing:
     * 1. Deadzone reduction for hand tremor elimination
     * 2. Dynamic drift compensation
     * 3. Non-linear power curve acceleration
     * 4. Sensitivity scaling (general vs aiming mode)
     * 5. EMA smoothing with subpixel accumulation
     */
    fun processMouseDelta(
        rawYaw: Float,
        rawPitch: Float,
        dt: Float = 0.010f,
        invertX: Boolean = false,
        invertY: Boolean = false,
        isHoldingAimButton: Boolean = false
    ): Pair<Float, Float> {
        isAimingModeActive = holdToAimEnabled && isHoldingAimButton

        val isAiming = isAimingModeActive
        val sensX = if (isAiming) aimingSensitivityX else sensitivityX
        val sensY = if (isAiming) aimingSensitivityY else sensitivityY
        val effectiveDeadzone = if (isAiming) deadzone * 0.5f else deadzone

        // Dynamic drift calibration
        if (autoDriftCalibration) {
            synchronized(driftLock) {
                val rawMag = hypot(rawYaw, rawPitch)
                if (rawMag < effectiveDeadzone) {
                    driftBiasX = 0.9f * driftBiasX + 0.1f * rawYaw
                    driftBiasY = 0.9f * driftBiasY + 0.1f * rawPitch
                    driftSampleCount++
                }
            }
        }

        val cleanYaw = applyDeadzone(rawYaw, effectiveDeadzone, driftBiasX)
        val cleanPitch = applyDeadzone(rawPitch, effectiveDeadzone, driftBiasY)

        val targetDx: Float
        val targetDy: Float

        if (cleanYaw == 0f && cleanPitch == 0f) {
            targetDx = 0f
            targetDy = 0f
        } else {
            val speed = hypot(cleanYaw, cleanPitch)

            // Apply non-linear power curve
            val curveMultiplier = if (nonLinearCurveExponent > 1.0f) {
                speed.pow(nonLinearCurveExponent - 1.0f)
            } else 1.0f

            val accelMultiplier = if (accelerationEnabled && speed > accelThreshold) {
                1.0f + accelGain * ((speed - accelThreshold) / accelThreshold).pow(accelExponent)
            } else {
                1.0f
            }

            val baseScale = 28.0f
            val dirX = if (invertX) 1.0f else -1.0f
            val dirY = if (invertY) -1.0f else 1.0f

            targetDx = cleanYaw * sensX * curveMultiplier * accelMultiplier * baseScale * dirX
            targetDy = cleanPitch * sensY * curveMultiplier * accelMultiplier * baseScale * dirY
        }

        // Continuous Exponential Moving Average (EMA)
        val smoothDx = smoothingFactor * prevSmoothX + (1f - smoothingFactor) * targetDx
        val smoothDy = smoothingFactor * prevSmoothY + (1f - smoothingFactor) * targetDy

        prevSmoothX = if (abs(smoothDx) < 0.0001f) 0f else smoothDx
        prevSmoothY = if (abs(smoothDy) < 0.0001f) 0f else smoothDy

        // Subpixel accumulator
        subpixelX += smoothDx
        subpixelY += smoothDy

        val intDx = subpixelX.toInt().toFloat()
        val intDy = subpixelY.toInt().toFloat()

        subpixelX -= intDx
        subpixelY -= intDy

        return Pair(intDx, intDy)
    }

    /**
     * Maps gyro angular rate to virtual gamepad thumbstick deflection [-32768, 32767]
     * with aiming sensitivity scaling and dynamic drift compensation.
     */
    fun processStickDeflection(
        rawYaw: Float,
        rawPitch: Float,
        invertX: Boolean = false,
        invertY: Boolean = false,
        isHoldingAimButton: Boolean = false
    ): Pair<Short, Short> {
        isAimingModeActive = holdToAimEnabled && isHoldingAimButton

        val isAiming = isAimingModeActive
        val sensX = if (isAiming) aimingSensitivityX else sensitivityX
        val sensY = if (isAiming) aimingSensitivityY else sensitivityY
        val effectiveDeadzone = if (isAiming) deadzone * 0.5f else deadzone

        // Dynamic drift calibration
        if (autoDriftCalibration) {
            synchronized(driftLock) {
                val rawMag = hypot(rawYaw, rawPitch)
                if (rawMag < effectiveDeadzone) {
                    driftBiasX = 0.9f * driftBiasX + 0.1f * rawYaw
                    driftBiasY = 0.9f * driftBiasY + 0.1f * rawPitch
                    driftSampleCount++
                }
            }
        }

        val cleanYaw = applyDeadzone(rawYaw, effectiveDeadzone, driftBiasX)
        val cleanPitch = applyDeadzone(rawPitch, effectiveDeadzone, driftBiasY)

        val targetX: Float
        val targetY: Float

        if (cleanYaw == 0f && cleanPitch == 0f) {
            targetX = 0f
            targetY = 0f
        } else {
            val dirX = if (invertX) -1.0f else 1.0f
            val dirY = if (invertY) -1.0f else 1.0f

            // Apply non-linear power curve
            val curveX = abs(cleanYaw).pow(nonLinearCurveExponent) * sign(cleanYaw)
            val curveY = abs(cleanPitch).pow(nonLinearCurveExponent) * sign(cleanPitch)

            val stickScale = 9000.0f
            targetX = (curveX * sensX * stickScale * dirX).coerceIn(-32768f, 32767f)
            targetY = (curveY * sensY * stickScale * dirY).coerceIn(-32768f, 32767f)
        }

        val smoothX = smoothingFactor * prevSmoothX + (1f - smoothingFactor) * targetX
        val smoothY = smoothingFactor * prevSmoothY + (1f - smoothingFactor) * targetY

        prevSmoothX = if (abs(smoothX) < 0.0001f) 0f else smoothX
        prevSmoothY = if (abs(smoothY) < 0.0001f) 0f else smoothY

        return Pair(smoothX.toInt().toShort(), smoothY.toInt().toShort())
    }

    /**
     * Standard 2-axis filter with deadzone and EMA smoothing.
     */
    fun applyFilter(x: Float, y: Float): Pair<Float, Float> {
        val deadX = applyDeadzone(x, deadzone, driftBiasX)
        val deadY = applyDeadzone(y, deadzone, driftBiasY)

        val smoothX = smoothingFactor * prevSmoothX + (1f - smoothingFactor) * deadX
        val smoothY = smoothingFactor * prevSmoothY + (1f - smoothingFactor) * deadY

        prevSmoothX = smoothX
        prevSmoothY = smoothY

        return Pair(smoothX, smoothY)
    }

    private fun applyDeadzone(value: Float, threshold: Float, bias: Float = 0f): Float {
        val absVal = abs(value - bias)
        return if (absVal < threshold) {
            0f
        } else {
            sign(value - bias) * absVal
        }
    }

    fun reset() {
        prevSmoothX = 0f
        prevSmoothY = 0f
        subpixelX = 0f
        subpixelY = 0f
    }

    fun updateFromConfig(config: com.airpad.app.data.models.GyroConfig) {
        this.deadzone = config.deadzone
        this.smoothingFactor = config.smoothingFactor
        this.sensitivityX = config.sensitivityX
        this.sensitivityY = config.sensitivityY
        this.aimingSensitivityX = config.aimingSensitivityX
        this.aimingSensitivityY = config.aimingSensitivityY
        this.holdToAimEnabled = config.holdToAimEnabled
        this.nonLinearCurveExponent = config.nonLinearCurveExponent
        this.autoDriftCalibration = config.autoDriftCalibration
    }
}
