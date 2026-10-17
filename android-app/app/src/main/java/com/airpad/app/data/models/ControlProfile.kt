package com.airpad.app.data.models

import com.airpad.app.data.ProtocolConstants
import org.json.JSONArray
import org.json.JSONObject

data class ControlProfile(
    val id: String,
    val name: String,
    val genre: String = "General",
    val targetProcessNames: List<String> = emptyList(), // e.g. ["cod.exe", "Valorant-Win64-Shipping.exe"]
    val targetWindowTitles: List<String> = emptyList(), // e.g. ["Call of Duty", "VALORANT"]
    val iconName: String = "🎮",
    val isCustom: Boolean = false,
    val touchConfig: TouchConfig = TouchConfig(),
    val gyroConfig: GyroConfig = GyroConfig(),
    val stickConfig: StickConfig = StickConfig(),
    val buttonMapping: ButtonMappingConfig = ButtonMappingConfig(),
    val hapticProfile: HapticProfile = HapticProfile()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("genre", genre)

            val procArr = JSONArray()
            targetProcessNames.forEach { procArr.put(it) }
            put("targetProcessNames", procArr)

            val winArr = JSONArray()
            targetWindowTitles.forEach { winArr.put(it) }
            put("targetWindowTitles", winArr)

            put("iconName", iconName)
            put("isCustom", isCustom)

            put("touch", JSONObject().apply {
                put("sensitivity", touchConfig.sensitivity.toDouble())
                put("deadzone", touchConfig.deadzone.toDouble())
                put("smoothingFactor", touchConfig.smoothingFactor.toDouble())
                put("invertScroll", touchConfig.invertScroll)
                put("invertX", touchConfig.invertX)
                put("invertY", touchConfig.invertY)
                put("accelerationEnabled", touchConfig.accelerationEnabled)
                put("accelThreshold", touchConfig.accelThreshold.toDouble())
                put("accelGain", touchConfig.accelGain.toDouble())
                put("accelExponent", touchConfig.accelExponent.toDouble())
                put("subpixelPrecision", touchConfig.subpixelPrecision)
                put("predictionEnabled", touchConfig.predictionEnabled)
                put("predictionFrames", touchConfig.predictionFrames)
            })

            put("gyro", gyroConfig.toJson())
            put("stick", stickConfig.toJson())
            put("buttonMapping", buttonMapping.toJson())
            put("haptics", hapticProfile.toJson())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ControlProfile {
            val id = json.optString("id", "custom_${System.currentTimeMillis()}")
            val name = json.optString("name", "Custom Profile")
            val genre = json.optString("genre", "General")

            val procList = mutableListOf<String>()
            val procArr = json.optJSONArray("targetProcessNames")
            if (procArr != null) {
                for (i in 0 until procArr.length()) {
                    procList.add(procArr.getString(i))
                }
            }

            val winList = mutableListOf<String>()
            val winArr = json.optJSONArray("targetWindowTitles")
            if (winArr != null) {
                for (i in 0 until winArr.length()) {
                    winList.add(winArr.getString(i))
                }
            }

            val iconName = json.optString("iconName", "🎮")
            val isCustom = json.optBoolean("isCustom", true)

            val touchObj = json.optJSONObject("touch")
            val touch = if (touchObj != null) {
                TouchConfig(
                    sensitivity = touchObj.optDouble("sensitivity", 1.5).toFloat(),
                    deadzone = touchObj.optDouble("deadzone", 0.5).toFloat(),
                    smoothingFactor = touchObj.optDouble("smoothingFactor", 0.15).toFloat(),
                    invertScroll = touchObj.optBoolean("invertScroll", false),
                    invertX = touchObj.optBoolean("invertX", false),
                    invertY = touchObj.optBoolean("invertY", false),
                    accelerationEnabled = touchObj.optBoolean("accelerationEnabled", true),
                    accelThreshold = touchObj.optDouble("accelThreshold", 30.0).toFloat(),
                    accelGain = touchObj.optDouble("accelGain", 1.8).toFloat(),
                    accelExponent = touchObj.optDouble("accelExponent", 1.4).toFloat(),
                    subpixelPrecision = touchObj.optBoolean("subpixelPrecision", true),
                    predictionEnabled = touchObj.optBoolean("predictionEnabled", true),
                    predictionFrames = touchObj.optInt("predictionFrames", 2)
                )
            } else TouchConfig()

            val gyroConfig = GyroConfig.fromJson(json.optJSONObject("gyro"))
            val stickConfig = StickConfig.fromJson(json.optJSONObject("stick"))
            val buttonMapping = ButtonMappingConfig.fromJson(json.optJSONObject("buttonMapping"))
            val hapticProfile = HapticProfile.fromJson(json.optJSONObject("haptics"))

            return ControlProfile(
                id = id,
                name = name,
                genre = genre,
                targetProcessNames = procList,
                targetWindowTitles = winList,
                iconName = iconName,
                isCustom = isCustom,
                touchConfig = touch,
                gyroConfig = gyroConfig,
                stickConfig = stickConfig,
                buttonMapping = buttonMapping,
                hapticProfile = hapticProfile
            )
        }

        // ==========================================
        // PRE-CONFIGURED GAME PROFILES (OPTIMIZED)
        // ==========================================

        val DEFAULT = ControlProfile(
            id = "preset_default",
            name = "Default Controller",
            genre = "General",
            iconName = "🎮",
            isCustom = false,
            touchConfig = TouchConfig(
                sensitivity = 1.3f,
                deadzone = 0.5f,
                smoothingFactor = 0.2f
            ),
            gyroConfig = GyroConfig(
                mode = GyroMode.OFF,
                sensitivityX = 1.4f,
                sensitivityY = 1.4f,
                deadzone = 0.010f,
                smoothingFactor = 0.35f,
                horizontalAxis = GyroHorizontalAxis.YAW
            ),
            stickConfig = StickConfig(
                sensitivityX = 1.0f,
                sensitivityY = 1.0f,
                deadzone = 0.08f,
                outerDeadzone = 0.98f,
                curveType = StickCurveType.LINEAR,
                smoothingFactor = 0.15f
            ),
            buttonMapping = ButtonMappingConfig(),
            hapticProfile = HapticProfile(intensity = 1.0f)
        )

        val FPS_COMPETITIVE = ControlProfile(
            id = "preset_fps_competitive",
            name = "FPS Competitive Aim",
            genre = "FPS",
            targetProcessNames = listOf("cod.exe", "Valorant-Win64-Shipping.exe", "r5apex.exe", "cs2.exe", "Overwatch.exe"),
            targetWindowTitles = listOf("Call of Duty", "VALORANT", "Apex Legends", "Counter-Strike 2", "Overwatch"),
            iconName = "🎯",
            isCustom = false,
            touchConfig = TouchConfig(
                sensitivity = 1.0f,
                deadzone = 0.3f,
                smoothingFactor = 0.15f
            ),
            gyroConfig = GyroConfig(
                mode = GyroMode.GYRO_TO_STICK,
                sensitivityX = 2.2f,
                sensitivityY = 2.0f,
                aimingSensitivityX = 1.1f,
                aimingSensitivityY = 1.0f,
                holdToAimEnabled = true,
                holdToAimButton = ProtocolConstants.GP_BTN_LB, // LT/LB hold to aim
                deadzone = 0.008f,
                smoothingFactor = 0.12f,
                nonLinearCurveExponent = 1.5f,
                horizontalAxis = GyroHorizontalAxis.YAW
            ),
            stickConfig = StickConfig(
                sensitivityX = 1.3f,
                sensitivityY = 1.2f,
                deadzone = 0.04f,
                outerDeadzone = 0.96f,
                curveType = StickCurveType.EXPONENTIAL,
                curveExponent = 2.0f, // Smooth micro-adjustments in center, snappy turns at outer ring
                smoothingFactor = 0.05f,
                antiDriftCompensation = true,
                stickEdgeHaptics = true
            ),
            buttonMapping = ButtonMappingConfig(
                turboButtons = setOf(ProtocolConstants.GP_BTN_X), // Quick reload/use repeat
                turboRateHz = 12
            ),
            hapticProfile = HapticProfile(
                intensity = 1.0f,
                stickEdgeFeedback = true,
                buttonPressPattern = "LIGHT_CLICK"
            )
        )

        val RACING_FORZA = ControlProfile(
            id = "preset_racing",
            name = "Racing Steering Wheel",
            genre = "Racing",
            targetProcessNames = listOf("ForzaHorizon5.exe", "NFS22.exe", "GTA5.exe", "AssettoCorsa.exe"),
            targetWindowTitles = listOf("Forza Horizon 5", "Need for Speed", "Grand Theft Auto V", "Assetto Corsa"),
            iconName = "🏎",
            isCustom = false,
            touchConfig = TouchConfig(sensitivity = 1.2f),
            gyroConfig = GyroConfig(
                mode = GyroMode.GYRO_TO_STICK,
                sensitivityX = 2.5f,
                sensitivityY = 1.0f,
                deadzone = 0.02f,
                smoothingFactor = 0.25f,
                horizontalAxis = GyroHorizontalAxis.ROLL // Steering wheel roll motion
            ),
            stickConfig = StickConfig(
                sensitivityX = 1.0f,
                sensitivityY = 1.0f,
                deadzone = 0.12f,
                outerDeadzone = 0.98f,
                curveType = StickCurveType.WIDE_CENTER, // Progressive wheel rotation
                curveExponent = 1.6f,
                smoothingFactor = 0.20f
            ),
            buttonMapping = ButtonMappingConfig(),
            hapticProfile = HapticProfile(
                intensity = 0.8f,
                triggerThresholdFeedback = true
            )
        )

        val RPG_EXPLORATION = ControlProfile(
            id = "preset_rpg",
            name = "RPG / Adventure",
            genre = "RPG",
            targetProcessNames = listOf("TESV.exe", "Fallout4.exe", "Cyberpunk2077.exe", "EldenRing.exe", "Witcher3.exe"),
            targetWindowTitles = listOf("The Elder Scrolls V: Skyrim", "Fallout 4", "Cyberpunk 2077", "ELDEN RING"),
            iconName = "⚔",
            isCustom = false,
            touchConfig = TouchConfig(sensitivity = 1.4f),
            gyroConfig = GyroConfig(
                mode = GyroMode.OFF,
                sensitivityX = 1.2f,
                sensitivityY = 1.2f,
                deadzone = 0.012f,
                smoothingFactor = 0.30f,
                horizontalAxis = GyroHorizontalAxis.YAW
            ),
            stickConfig = StickConfig(
                sensitivityX = 1.0f,
                sensitivityY = 1.0f,
                deadzone = 0.08f,
                outerDeadzone = 0.98f,
                curveType = StickCurveType.LOGARITHMIC, // Smooth camera panning
                curveExponent = 1.7f,
                smoothingFactor = 0.15f
            ),
            buttonMapping = ButtonMappingConfig(
                chordingRules = listOf(
                    ChordingRule(
                        primaryBit = ProtocolConstants.GP_BTN_LB,
                        secondaryBit = ProtocolConstants.GP_BTN_A,
                        outputBit = ProtocolConstants.GP_BTN_Y // LB+A -> Y Special Ability
                    )
                )
            ),
            hapticProfile = HapticProfile(intensity = 1.0f)
        )

        val FIGHTING_ARCADE = ControlProfile(
            id = "preset_fighting",
            name = "Fighting Arcade Pro",
            genre = "Fighting",
            targetProcessNames = listOf("StreetFighter6.exe", "Tekken8.exe", "MK12.exe"),
            targetWindowTitles = listOf("Street Fighter 6", "TEKKEN 8", "Mortal Kombat 1"),
            iconName = "🥊",
            isCustom = false,
            touchConfig = TouchConfig(sensitivity = 1.0f),
            gyroConfig = GyroConfig(mode = GyroMode.OFF),
            stickConfig = StickConfig(
                sensitivityX = 1.0f,
                sensitivityY = 1.0f,
                deadzone = 0.02f, // Ultra low deadzone for instant actuation
                outerDeadzone = 0.95f,
                curveType = StickCurveType.DIGITAL_STEP, // Instant 0% or 100% digital directional response
                curveExponent = 1.0f,
                smoothingFactor = 0.0f // Zero input lag smoothing
            ),
            buttonMapping = ButtonMappingConfig(
                turboButtons = setOf(ProtocolConstants.GP_BTN_A, ProtocolConstants.GP_BTN_X),
                turboRateHz = 20
            ),
            hapticProfile = HapticProfile(
                intensity = 1.0f,
                buttonPressPattern = "HEAVY_RUMBLE"
            )
        )

        val PLATFORMER_PRECISION = ControlProfile(
            id = "preset_platformer",
            name = "Platformer & Precision",
            genre = "Platformer",
            targetProcessNames = listOf("HollowKnight.exe", "Celeste.exe", "OriAndTheWillOfTheWisps.exe"),
            targetWindowTitles = listOf("Hollow Knight", "Celeste", "Ori"),
            iconName = "🍄",
            isCustom = false,
            touchConfig = TouchConfig(sensitivity = 1.2f),
            gyroConfig = GyroConfig(mode = GyroMode.OFF),
            stickConfig = StickConfig(
                sensitivityX = 1.1f,
                sensitivityY = 1.1f,
                deadzone = 0.03f,
                outerDeadzone = 0.96f,
                curveType = StickCurveType.LINEAR,
                smoothingFactor = 0.02f,
                springTension = 0.2f
            ),
            buttonMapping = ButtonMappingConfig(),
            hapticProfile = HapticProfile(
                intensity = 0.9f,
                buttonPressPattern = "LIGHT_CLICK"
            )
        )

        val RTS_MOBA = ControlProfile(
            id = "preset_rts_moba",
            name = "RTS / MOBA Fast Cursor",
            genre = "RTS/MOBA",
            targetProcessNames = listOf("League of Legends.exe", "dota2.exe", "SC2_x64.exe"),
            targetWindowTitles = listOf("League of Legends", "Dota 2", "StarCraft II"),
            iconName = "⚡",
            isCustom = false,
            touchConfig = TouchConfig(sensitivity = 1.8f),
            gyroConfig = GyroConfig(
                mode = GyroMode.GYRO_TO_MOUSE,
                sensitivityX = 2.5f,
                sensitivityY = 2.5f,
                deadzone = 0.01f,
                smoothingFactor = 0.10f
            ),
            stickConfig = StickConfig(
                sensitivityX = 1.5f,
                sensitivityY = 1.5f,
                deadzone = 0.05f,
                curveType = StickCurveType.EXPONENTIAL,
                curveExponent = 2.2f
            ),
            buttonMapping = ButtonMappingConfig(
                turboButtons = setOf(ProtocolConstants.GP_BTN_RIGHT_SHOULDER),
                turboRateHz = 15
            ),
            hapticProfile = HapticProfile(intensity = 0.7f)
        )

        val BUILTIN_PRESETS = listOf(
            DEFAULT,
            FPS_COMPETITIVE,
            RACING_FORZA,
            RPG_EXPLORATION,
            FIGHTING_ARCADE,
            PLATFORMER_PRECISION,
            RTS_MOBA
        )
    }
}
