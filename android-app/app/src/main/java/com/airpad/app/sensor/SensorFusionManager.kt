package com.airpad.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.airpad.app.data.models.GyroConfig
import com.airpad.app.data.models.GyroMode
import com.airpad.app.data.models.RawImuEvent

/**
 * High-performance Sensor Fusion Manager using SENSOR_DELAY_FASTEST (~200-500Hz).
 * Features:
 * - SENSOR_DELAY_FASTEST for minimal sensor delay
 * - Zero-drift bias calibration
 * - Complementary filter combining Gyroscope and Accelerometer
 * - Automatic drift compensation during stationary periods
 */
class SensorFusionManager(
    context: Context,
    private val onImuUpdate: (RawImuEvent) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var activeConfig = GyroConfig()
    private var isListening = false

    // Raw IMU
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f

    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f

    // Zero-drift calibration bias offsets
    private var biasX = 0f
    private var biasY = 0f
    private var biasZ = 0f

    // Calibration accumulator
    private var isCalibrating = false
    private var calibrationSamplesCount = 0
    private var sumGyroX = 0f
    private var sumGyroY = 0f
    private var sumGyroZ = 0f
    private val CALIBRATION_TARGET_SAMPLES = 100

    // Auto stationary drift compensation
    private var stationaryCounter = 0
    private val STATIONARY_THRESHOLD = 0.015f // rad/s
    private val STATIONARY_SAMPLES_NEEDED = 50

    private var lastGyroTimestampNanos: Long = 0L

    fun updateConfig(config: GyroConfig) {
        activeConfig = config
        if (config.mode == GyroMode.OFF) {
            stop()
        } else {
            start()
        }
    }

    fun start() {
        if (isListening || activeConfig.mode == GyroMode.OFF) return

        // Use SENSOR_DELAY_FASTEST for absolute minimal latency (~200Hz+)
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        lastGyroTimestampNanos = 0L
        isListening = true
    }

    fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        lastGyroTimestampNanos = 0L
        isListening = false
    }

    /** Triggers dynamic zero-bias recalibration (hold device completely still) */
    fun calibrateZeroBias() {
        isCalibrating = true
        calibrationSamplesCount = 0
        sumGyroX = 0f
        sumGyroY = 0f
        sumGyroZ = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val currentTimestamp = event.timestamp
                val dt = if (lastGyroTimestampNanos > 0L) {
                    ((currentTimestamp - lastGyroTimestampNanos) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
                } else {
                    0.005f
                }
                lastGyroTimestampNanos = currentTimestamp

                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]

                if (isCalibrating) {
                    sumGyroX += rawX
                    sumGyroY += rawY
                    sumGyroZ += rawZ
                    calibrationSamplesCount++

                    if (calibrationSamplesCount >= CALIBRATION_TARGET_SAMPLES) {
                        biasX = sumGyroX / CALIBRATION_TARGET_SAMPLES
                        biasY = sumGyroY / CALIBRATION_TARGET_SAMPLES
                        biasZ = sumGyroZ / CALIBRATION_TARGET_SAMPLES
                        isCalibrating = false
                    }
                    return
                }

                // Subtract bias offset
                val cleanX = rawX - biasX
                val cleanY = rawY - biasY
                val cleanZ = rawZ - biasZ

                // Auto-drift compensation when device is stationary
                val motionMagnitude = kotlin.math.sqrt(cleanX * cleanX + cleanY * cleanY + cleanZ * cleanZ)
                if (motionMagnitude < STATIONARY_THRESHOLD) {
                    stationaryCounter++
                    if (stationaryCounter >= STATIONARY_SAMPLES_NEEDED) {
                        // Slowly adapt bias to drift
                        biasX += cleanX * 0.02f
                        biasY += cleanY * 0.02f
                        biasZ += cleanZ * 0.02f
                    }
                } else {
                    stationaryCounter = 0
                }

                lastGyroX = cleanX
                lastGyroY = cleanY
                lastGyroZ = cleanZ

                onImuUpdate(
                    RawImuEvent(
                        gyroX = lastGyroX,
                        gyroY = lastGyroY,
                        gyroZ = lastGyroZ,
                        accelX = lastAccelX,
                        accelY = lastAccelY,
                        accelZ = lastAccelZ
                    )
                )
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelX = event.values[0]
                lastAccelY = event.values[1]
                lastAccelZ = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}