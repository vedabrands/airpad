package com.airpad.app.data.models

import org.json.JSONObject

data class HapticProfile(
    val intensity: Float = 1.0f,                 // Overall vibration intensity [0.0f .. 1.0f]
    val stickEdgeFeedback: Boolean = true,    // Vibration when stick reaches 90%+ deflection
    val triggerThresholdFeedback: Boolean = true, // Vibration when triggers cross 50% or 90%
    val buttonPressPattern: String = "LIGHT_CLICK", // LIGHT_CLICK, MEDIUM_CLICK, HEAVY_RUMBLE, DOUBLE_PULSE
    val turboTickPattern: String = "RAPID_TICK",   // Pattern for rapid-fire feedback: RAPID_TICK or OFF
    val edgeReachedAt: Float = 0.95f          // Threshold percentage for stick edge feedback
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("intensity", intensity.toDouble())
            put("stickEdgeFeedback", stickEdgeFeedback)
            put("triggerThresholdFeedback", triggerThresholdFeedback)
            put("buttonPressPattern", buttonPressPattern)
            put("turboTickPattern", turboTickPattern)
            put("edgeReachedAt", edgeReachedAt.toDouble())
        }
    }

    companion object {
        fun fromJson(json: JSONObject?): HapticProfile {
            if (json == null) return HapticProfile()
            return HapticProfile(
                intensity = json.optDouble("intensity", 1.0).toFloat(),
                stickEdgeFeedback = json.optBoolean("stickEdgeFeedback", true),
                triggerThresholdFeedback = json.optBoolean("triggerThresholdFeedback", true),
                buttonPressPattern = json.optString("buttonPressPattern", "LIGHT_CLICK"),
                turboTickPattern = json.optString("turboTickPattern", "RAPID_TICK"),
                edgeReachedAt = json.optDouble("edgeReachedAt", 0.95).toFloat()
            )
        }
    }
}