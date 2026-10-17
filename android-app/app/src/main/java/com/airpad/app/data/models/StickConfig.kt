package com.airpad.app.data.models

import org.json.JSONObject

enum class StickCurveType {
    LINEAR,          // Standard linear output
    EXPONENTIAL,     // Smooth micro-aim near center, responsive near edges
    LOGARITHMIC,     // Snappy near center, levels off near edges
    WIDE_CENTER,     // Flattened center zone for ultra-stable fine aim
    DIGITAL_STEP;    // Instant 0% or 100% binary response (Fighting games)

    companion object {
        fun fromString(str: String?): StickCurveType {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: LINEAR
        }
    }
}

data class StickConfig(
    val sensitivityX: Float = 1.0f,
    val sensitivityY: Float = 1.0f,
    val deadzone: Float = 0.08f,          // Inner radial deadzone [0.0f .. 0.4f]
    val outerDeadzone: Float = 0.98f,     // Outer deadzone threshold [0.6f .. 1.0f]
    val curveType: StickCurveType = StickCurveType.LINEAR,
    val curveExponent: Float = 1.8f,      // Exponent for Exponential/Logarithmic curves [1.0f .. 3.5f]
    val smoothingFactor: Float = 0.15f,
    val antiDriftCompensation: Boolean = true,
    val springTension: Float = 0.0f,      // Return-to-center spring effect simulation [0.0f .. 0.5f]
    val stickEdgeHaptics: Boolean = true, // Haptic tick when reaching maximum stick radius
    val invertX: Boolean = false,
    val invertY: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("sensitivityX", sensitivityX.toDouble())
            put("sensitivityY", sensitivityY.toDouble())
            put("deadzone", deadzone.toDouble())
            put("outerDeadzone", outerDeadzone.toDouble())
            put("curveType", curveType.name)
            put("curveExponent", curveExponent.toDouble())
            put("smoothingFactor", smoothingFactor.toDouble())
            put("antiDriftCompensation", antiDriftCompensation)
            put("springTension", springTension.toDouble())
            put("stickEdgeHaptics", stickEdgeHaptics)
            put("invertX", invertX)
            put("invertY", invertY)
        }
    }

    companion object {
        fun fromJson(json: JSONObject?): StickConfig {
            if (json == null) return StickConfig()
            return StickConfig(
                sensitivityX = json.optDouble("sensitivityX", 1.0).toFloat(),
                sensitivityY = json.optDouble("sensitivityY", 1.0).toFloat(),
                deadzone = json.optDouble("deadzone", 0.08).toFloat(),
                outerDeadzone = json.optDouble("outerDeadzone", 0.98).toFloat(),
                curveType = StickCurveType.fromString(json.optString("curveType", "LINEAR")),
                curveExponent = json.optDouble("curveExponent", 1.8).toFloat(),
                smoothingFactor = json.optDouble("smoothingFactor", 0.15).toFloat(),
                antiDriftCompensation = json.optBoolean("antiDriftCompensation", true),
                springTension = json.optDouble("springTension", 0.0).toFloat(),
                stickEdgeHaptics = json.optBoolean("stickEdgeHaptics", true),
                invertX = json.optBoolean("invertX", false),
                invertY = json.optBoolean("invertY", false)
            )
        }
    }
}
