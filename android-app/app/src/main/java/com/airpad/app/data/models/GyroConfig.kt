package com.airpad.app.data.models

import com.airpad.app.data.ProtocolConstants
import org.json.JSONObject

data class GyroConfig(
    val mode: GyroMode = GyroMode.OFF,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    val ratchetActive: Boolean = false,
    val sensitivityX: Float = 1.4f,           // General motion sensitivity
    val sensitivityY: Float = 1.4f,
    val aimingSensitivityX: Float = 0.8f,     // Dedicated fine aiming sensitivity
    val aimingSensitivityY: Float = 0.8f,
    val holdToAimEnabled: Boolean = false,    // Automatically engage aiming sensitivity when holdToAimButton pressed
    val holdToAimButton: Int = ProtocolConstants.GP_BTN_LB, // Default LT or LB
    val deadzone: Float = 0.010f,
    val smoothingFactor: Float = 0.35f,
    val nonLinearCurveExponent: Float = 1.4f, // Non-linear motion curve power
    val autoDriftCalibration: Boolean = true,  // Dynamic zero-bias gyro drift compensation
    val horizontalAxis: GyroHorizontalAxis = GyroHorizontalAxis.YAW
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("mode", mode.name)
            put("invertX", invertX)
            put("invertY", invertY)
            put("ratchetActive", ratchetActive)
            put("sensitivityX", sensitivityX.toDouble())
            put("sensitivityY", sensitivityY.toDouble())
            put("aimingSensitivityX", aimingSensitivityX.toDouble())
            put("aimingSensitivityY", aimingSensitivityY.toDouble())
            put("holdToAimEnabled", holdToAimEnabled)
            put("holdToAimButton", holdToAimButton)
            put("deadzone", deadzone.toDouble())
            put("smoothingFactor", smoothingFactor.toDouble())
            put("nonLinearCurveExponent", nonLinearCurveExponent.toDouble())
            put("autoDriftCalibration", autoDriftCalibration)
            put("horizontalAxis", horizontalAxis.name)
        }
    }

    companion object {
        fun fromJson(json: JSONObject?): GyroConfig {
            if (json == null) return GyroConfig()
            val modeStr = json.optString("mode", GyroMode.OFF.name)
            val mode = try { GyroMode.valueOf(modeStr) } catch (_: Exception) { GyroMode.OFF }
            val axisStr = json.optString("horizontalAxis", GyroHorizontalAxis.YAW.name)
            val axis = try { GyroHorizontalAxis.valueOf(axisStr) } catch (_: Exception) { GyroHorizontalAxis.YAW }

            return GyroConfig(
                mode = mode,
                invertX = json.optBoolean("invertX", false),
                invertY = json.optBoolean("invertY", false),
                ratchetActive = json.optBoolean("ratchetActive", false),
                sensitivityX = json.optDouble("sensitivityX", 1.4).toFloat(),
                sensitivityY = json.optDouble("sensitivityY", 1.4).toFloat(),
                aimingSensitivityX = json.optDouble("aimingSensitivityX", 0.8).toFloat(),
                aimingSensitivityY = json.optDouble("aimingSensitivityY", 0.8).toFloat(),
                holdToAimEnabled = json.optBoolean("holdToAimEnabled", false),
                holdToAimButton = json.optInt("holdToAimButton", ProtocolConstants.GP_BTN_LB),
                deadzone = json.optDouble("deadzone", 0.010).toFloat(),
                smoothingFactor = json.optDouble("smoothingFactor", 0.35).toFloat(),
                nonLinearCurveExponent = json.optDouble("nonLinearCurveExponent", 1.4).toFloat(),
                autoDriftCalibration = json.optBoolean("autoDriftCalibration", true),
                horizontalAxis = axis
            )
        }
    }
}
