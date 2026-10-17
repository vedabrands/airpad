package com.airpad.app.data.models

import org.json.JSONArray
import org.json.JSONObject

data class MacroStep(
    val buttonBit: Int,
    val action: Byte, // 1 = Press, 0 = Release
    val delayMs: Long = 50L
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("buttonBit", buttonBit)
            put("action", action.toInt())
            put("delayMs", delayMs)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): MacroStep {
            return MacroStep(
                buttonBit = json.optInt("buttonBit", 0),
                action = json.optInt("action", 1).toByte(),
                delayMs = json.optLong("delayMs", 50L)
            )
        }
    }
}

data class MacroSequence(
    val id: String,
    val name: String,
    val triggerButtonBit: Int, // The button that fires this macro (e.g. LS, RS, or Touchpad)
    val steps: List<MacroStep>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("triggerButtonBit", triggerButtonBit)
            val stepsArr = JSONArray()
            steps.forEach { stepsArr.put(it.toJson()) }
            put("steps", stepsArr)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): MacroSequence {
            val id = json.optString("id", "macro_${System.currentTimeMillis()}")
            val name = json.optString("name", "Custom Macro")
            val trigger = json.optInt("triggerButtonBit", 0)
            val stepsList = mutableListOf<MacroStep>()
            val stepsArr = json.optJSONArray("steps")
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    stepsList.add(MacroStep.fromJson(stepsArr.getJSONObject(i)))
                }
            }
            return MacroSequence(id, name, trigger, stepsList)
        }
    }
}

data class ChordingRule(
    val primaryBit: Int,
    val secondaryBit: Int,
    val outputBit: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("primaryBit", primaryBit)
            put("secondaryBit", secondaryBit)
            put("outputBit", outputBit)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ChordingRule {
            return ChordingRule(
                primaryBit = json.optInt("primaryBit", 0),
                secondaryBit = json.optInt("secondaryBit", 0),
                outputBit = json.optInt("outputBit", 0)
            )
        }
    }
}

data class ButtonMappingConfig(
    val remappings: Map<Int, Int> = emptyMap(), // Original Button Bit -> Remapped Button Bit
    val turboButtons: Set<Int> = emptySet(),     // Buttons that auto-repeat when held
    val turboRateHz: Int = 10,                   // 5 to 30 Hz rapid-fire rate
    val macros: List<MacroSequence> = emptyList(),
    val chordingRules: List<ChordingRule> = emptyList()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            val remapObj = JSONObject()
            remappings.forEach { (src, dst) -> remapObj.put(src.toString(), dst) }
            put("remappings", remapObj)

            val turboArr = JSONArray()
            turboButtons.forEach { turboArr.put(it) }
            put("turboButtons", turboArr)
            put("turboRateHz", turboRateHz)

            val macroArr = JSONArray()
            macros.forEach { macroArr.put(it.toJson()) }
            put("macros", macroArr)

            val chordArr = JSONArray()
            chordingRules.forEach { chordArr.put(it.toJson()) }
            put("chordingRules", chordArr)
        }
    }

    companion object {
        fun fromJson(json: JSONObject?): ButtonMappingConfig {
            if (json == null) return ButtonMappingConfig()

            val remapMap = mutableMapOf<Int, Int>()
            val remapObj = json.optJSONObject("remappings")
            remapObj?.keys()?.forEach { key ->
                val src = key.toIntOrNull()
                val dst = remapObj.optInt(key)
                if (src != null) {
                    remapMap[src] = dst
                }
            }

            val turboSet = mutableSetOf<Int>()
            val turboArr = json.optJSONArray("turboButtons")
            if (turboArr != null) {
                for (i in 0 until turboArr.length()) {
                    turboSet.add(turboArr.getInt(i))
                }
            }

            val macroList = mutableListOf<MacroSequence>()
            val macroArr = json.optJSONArray("macros")
            if (macroArr != null) {
                for (i in 0 until macroArr.length()) {
                    macroList.add(MacroSequence.fromJson(macroArr.getJSONObject(i)))
                }
            }

            val chordList = mutableListOf<ChordingRule>()
            val chordArr = json.optJSONArray("chordingRules")
            if (chordArr != null) {
                for (i in 0 until chordArr.length()) {
                    chordList.add(ChordingRule.fromJson(chordArr.getJSONObject(i)))
                }
            }

            return ButtonMappingConfig(
                remappings = remapMap,
                turboButtons = turboSet,
                turboRateHz = json.optInt("turboRateHz", 10),
                macros = macroList,
                chordingRules = chordList
            )
        }
    }
}
