package com.airpad.app.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroFilterTest {

    @Test
    fun testDeadzoneFiltering() {
        val filter = GyroFilter(deadzone = 0.05f, smoothingFactor = 0.0f)

        // Values below threshold should be 0
        val (smoothX1, smoothY1) = filter.applyFilter(0.02f, -0.03f)
        assertEquals(0f, smoothX1, 0.0001f)
        assertEquals(0f, smoothY1, 0.0001f)

        // Values above threshold should subtract deadzone
        val (smoothX2, smoothY2) = filter.applyFilter(0.10f, -0.10f)
        assertEquals(0.05f, smoothX2, 0.0001f)
        assertEquals(-0.05f, smoothY2, 0.0001f)
    }

    @Test
    fun testSmoothing() {
        val filter = GyroFilter(deadzone = 0.0f, smoothingFactor = 0.5f)

        val (x1, y1) = filter.applyFilter(1.0f, 2.0f)
        assertEquals(0.5f, x1, 0.0001f)
        assertEquals(1.0f, y1, 0.0001f)

        val (x2, y2) = filter.applyFilter(1.0f, 2.0f)
        assertEquals(0.75f, x2, 0.0001f)
        assertEquals(1.5f, y2, 0.0001f)
    }

    @Test
    fun testMouseDeltaLaserPointerAcceleration() {
        val filter = GyroFilter(
            deadzone = 0.01f,
            smoothingFactor = 0.0f,
            sensitivityX = 1.0f,
            sensitivityY = 1.0f,
            accelerationEnabled = true,
            accelThreshold = 0.5f,
            accelGain = 1.5f,
            accelExponent = 1.2f
        )

        // Micro-movement below acceleration threshold
        val (dxSlow, dySlow) = filter.processMouseDelta(rawYaw = 0.2f, rawPitch = 0.0f)
        // cleanYaw = 0.2 - 0.01 = 0.19, speed = 0.19 <= 0.5 -> accelMultiplier = 1.0
        // targetDx = 0.19 * 1.0 * 1.0 * 28.0 * -1.0 = -5.32 -> subpixel int = -5
        assertEquals(-5f, dxSlow, 0.001f)

        filter.reset()

        // Fast flick exceeding acceleration threshold
        val (dxFast, dyFast) = filter.processMouseDelta(rawYaw = 2.0f, rawPitch = 0.0f)
        // cleanYaw = 1.99, speed > 0.5 -> acceleration multiplier > 1.0 -> magnified delta
        assertTrue(dxFast < -55.72f) // More than linear 1.99 * 28 = 55.72
    }

    @Test
    fun testStickDeflectionMapping() {
        val filter = GyroFilter(
            deadzone = 0.01f,
            smoothingFactor = 0.0f,
            sensitivityX = 1.0f,
            sensitivityY = 1.0f
        )

        // Sub-deadzone yields zero
        val (zeroX, zeroY) = filter.processStickDeflection(0.005f, -0.005f)
        assertEquals(0.toShort(), zeroX)
        assertEquals(0.toShort(), zeroY)

        // Moderate rate maps to proportional virtual stick short
        val (stickX, stickY) = filter.processStickDeflection(1.01f, -1.01f)
        // cleanYaw = 1.0 * 9000 = 9000
        assertEquals(9000.toShort(), stickX)
        assertEquals((-9000).toShort(), stickY)

        // Extreme rate clamps to max Short boundaries
        val (clampX, clampY) = filter.processStickDeflection(10.0f, -10.0f)
        assertEquals(32767.toShort(), clampX)
        assertEquals((-32768).toShort(), clampY)
    }

    @Test
    fun testReset() {
        val filter = GyroFilter(deadzone = 0.0f, smoothingFactor = 0.5f)
        filter.applyFilter(2.0f, 2.0f)
        filter.reset()

        val (x, y) = filter.applyFilter(2.0f, 2.0f)
        assertEquals(1.0f, x, 0.0001f)
        assertEquals(1.0f, y, 0.0001f)
    }
}
