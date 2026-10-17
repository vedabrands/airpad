package com.airpad.app.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class TouchFilterTest {

    @Test
    fun testDeadzoneFiltering() {
        val filter = TouchFilter(deadzone = 1.0f, smoothingFactor = 0.0f, sensitivity = 1.0f)

        // Input within deadzone should return (0, 0)
        val (dx, dy) = filter.processDelta(0.4f, 0.4f)
        assertEquals(0f, dx, 0.0001f)
        assertEquals(0f, dy, 0.0001f)
    }

    @Test
    fun testSensitivityScaling() {
        val filter = TouchFilter(deadzone = 0.0f, smoothingFactor = 0.0f, sensitivity = 2.0f)

        val (dx, dy) = filter.processDelta(5.0f, -3.0f)
        assertEquals(10.0f, dx, 0.0001f)
        assertEquals(-6.0f, dy, 0.0001f)
    }

    @Test
    fun testEmaSmoothing() {
        val filter = TouchFilter(deadzone = 0.0f, smoothingFactor = 0.5f, sensitivity = 1.0f)

        // First sample: initial smoothed values are 0, so smoothed = 0.5*0 + 0.5*10 = 5.0
        val (dx1, dy1) = filter.processDelta(10.0f, 0.0f)
        assertEquals(5.0f, dx1, 0.0001f)
        assertEquals(0.0f, dy1, 0.0001f)

        // Second sample with same input: smoothed = 0.5*5.0 + 0.5*10.0 = 7.5
        val (dx2, dy2) = filter.processDelta(10.0f, 0.0f)
        assertEquals(7.5f, dx2, 0.0001f)
        assertEquals(0.0f, dy2, 0.0001f)
    }

    @Test
    fun testReset() {
        val filter = TouchFilter(deadzone = 0.0f, smoothingFactor = 0.5f, sensitivity = 1.0f)
        filter.processDelta(10.0f, 10.0f)
        filter.reset()

        // After reset, previous state is cleared
        val (dx, dy) = filter.processDelta(10.0f, 0.0f)
        assertEquals(5.0f, dx, 0.0001f)
    }
}
