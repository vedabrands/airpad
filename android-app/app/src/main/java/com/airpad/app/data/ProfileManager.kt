package com.airpad.app.data

import android.content.Context
import android.content.SharedPreferences
import com.airpad.app.data.models.ControlProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.IOException

class ProfileManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val profileDir = context.getExternalFilesDir("game_profiles")

    companion object {
        private const val PREFS_NAME = "airpad_control_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_CUSTOM_PROFILES_JSON = "custom_profiles_json"
        private const val KEY_ACTIVE_OVERRIDE_JSON = "active_override_json"
        private const val KEY_AUTO_DETECT_ENABLED = "auto_detect_enabled"
    }

    /**
     * Retrieves all available profiles (Built-in Presets + User Custom Profiles).
     */
    fun getAllProfiles(): List<ControlProfile> {
        val list = mutableListOf<ControlProfile>()
        list.addAll(ControlProfile.BUILTIN_PRESETS)
        list.addAll(getCustomProfiles())
        return list
    }

    /**
     * Gets user-created custom profiles from persistent storage.
     */
    fun getCustomProfiles(): List<ControlProfile> {
        val jsonStr = prefs.getString(KEY_CUSTOM_PROFILES_JSON, null) ?: return emptyList()
        val result = mutableListOf<ControlProfile>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(ControlProfile.fromJson(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * Gets the current active profile with any live tweaks saved.
     */
    fun getActiveProfile(): ControlProfile {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, ControlProfile.DEFAULT.id) ?: ControlProfile.DEFAULT.id

        // Check if there is an active override stored for this profile ID
        val overrideJson = prefs.getString(KEY_ACTIVE_OVERRIDE_JSON, null)
        if (overrideJson != null) {
            try {
                val obj = JSONObject(overrideJson)
                if (obj.optString("id") == activeId) {
                    return ControlProfile.fromJson(obj)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Search custom profiles first, then builtin presets
        val all = getAllProfiles()
        return all.firstOrNull { it.id == activeId } ?: ControlProfile.DEFAULT
    }

    /**
     * Selects an active profile by ID and persists selection.
     */
    fun selectProfile(profileId: String): ControlProfile {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
        // Reset override to match the selected profile
        val all = getAllProfiles()
        val target = all.firstOrNull { it.id == profileId } ?: ControlProfile.DEFAULT
        saveActiveProfile(target)
        return target
    }

    /**
     * Saves the current active configuration tweaks to disk.
     */
    fun saveActiveProfile(profile: ControlProfile) {
        prefs.edit()
            .putString(KEY_ACTIVE_PROFILE_ID, profile.id)
            .putString(KEY_ACTIVE_OVERRIDE_JSON, profile.toJson().toString())
            .apply()

        // If it's a custom profile, also update it in the custom profiles list
        if (profile.isCustom) {
            updateCustomProfile(profile)
        }
    }

    /**
     * Creates and saves a new custom profile based on the given configuration.
     */
    fun createCustomProfile(name: String, baseProfile: ControlProfile): ControlProfile {
        val newId = "custom_${System.currentTimeMillis()}"
        val newProfile = baseProfile.copy(
            id = newId,
            name = name,
            isCustom = true
        )
        val customList = getCustomProfiles().toMutableList()
        customList.add(newProfile)
        saveCustomProfilesList(customList)
        selectProfile(newId)
        return newProfile
    }

    /**
     * Updates an existing custom profile in the saved list.
     */
    fun updateCustomProfile(profile: ControlProfile) {
        if (!profile.isCustom) return
        val customList = getCustomProfiles().toMutableList()
        val index = customList.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            customList[index] = profile
        } else {
            customList.add(profile)
        }
        saveCustomProfilesList(customList)
    }

    /**
     * Deletes a custom profile by ID. If it was active, reverts to Default.
     */
    fun deleteCustomProfile(profileId: String): ControlProfile {
        val customList = getCustomProfiles().filterNot { it.id == profileId }
        saveCustomProfilesList(customList)

        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        if (activeId == profileId) {
            return selectProfile(ControlProfile.DEFAULT.id)
        }
        return getActiveProfile()
    }

    /**
     * Auto-detects matching profile based on process name and window title.
     */
    fun detectAndSelectProfile(processName: String, windowTitle: String): ControlProfile? {
        val allProfiles = getAllProfiles()

        // Skip if auto-detect is disabled
        if (!isAutoDetectEnabled()) return null

        // Try exact process name match first
        val processLower = processName.lowercase().removeSuffix(".exe")
        val matchedByProcess = allProfiles.find { profile ->
            profile.targetProcessNames.any { target ->
                target.lowercase().removeSuffix(".exe") == processLower
            }
        }
        if (matchedByProcess != null) {
            return matchedByProcess
        }

        // Try window title partial match
        val titleLower = windowTitle.lowercase()
        val matchedByTitle = allProfiles.find { profile ->
            profile.targetWindowTitles.any { target ->
                target.lowercase() in titleLower
            }
        }
        return matchedByTitle
    }

    /**
     * Enables or disables auto-detection.
     */
    fun setAutoDetectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DETECT_ENABLED, enabled).apply()
    }

    fun isAutoDetectEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_DETECT_ENABLED, true)
    }

    /**
     * Imports a JSON profile from an input stream.
     */
    fun importProfile(jsonStr: String): ControlProfile? {
        return try {
            val json = JSONObject(jsonStr)
            val imported = ControlProfile.fromJson(json)
            // Assign a new ID to avoid conflicts
            val newId = "imported_${System.currentTimeMillis()}"
            val updated = imported.copy(id = newId, isCustom = true, name = imported.name + " (Imported)")
            saveCustomProfile(updated)
            updated
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a single custom profile to the list.
     */
    fun saveCustomProfile(profile: ControlProfile) {
        val customList = getCustomProfiles().toMutableList()
        val index = customList.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            customList[index] = profile
        } else {
            customList.add(profile)
        }
        saveCustomProfilesList(customList)
    }

    private fun saveCustomProfilesList(profiles: List<ControlProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_CUSTOM_PROFILES_JSON, array.toString()).apply()
    }

    /**
     * Exports a profile as JSON string.
     */
    fun exportProfileToJson(profile: ControlProfile): String {
        return profile.toJson().toString(2)
    }

    /**
     * Writes profile JSON to external files directory for sharing.
     */
    fun writeProfileToFile(profile: ControlProfile): String? {
        val fileName = "${profile.name.replace(" ", "_")}_${System.currentTimeMillis()}.json"
        val file = File(profileDir, fileName)
        return try {
            file.writeText(profile.toJson().toString(2))
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
