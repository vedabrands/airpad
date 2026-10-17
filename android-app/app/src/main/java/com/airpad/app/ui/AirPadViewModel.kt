package com.airpad.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airpad.app.data.ProfileManager
import com.airpad.app.data.ProtocolConstants
import com.airpad.app.data.models.AppMode
import com.airpad.app.data.models.ButtonMappingConfig
import com.airpad.app.data.models.ConnectionState
import com.airpad.app.data.models.ControlProfile
import com.airpad.app.data.models.DiscoveredServer
import com.airpad.app.data.models.GamepadStateEvent
import com.airpad.app.data.models.GyroConfig
import com.airpad.app.data.models.GyroHorizontalAxis
import com.airpad.app.data.models.GyroMode
import com.airpad.app.data.models.HapticProfile
import com.airpad.app.data.models.MacroSequence
import com.airpad.app.data.models.RawImuEvent
import com.airpad.app.data.models.StickConfig
import com.airpad.app.data.models.TouchConfig
import com.airpad.app.network.SessionManager
import com.airpad.app.sensor.GyroFilter
import com.airpad.app.sensor.SensorFusionManager
import com.airpad.app.sensor.StickFilter
import com.airpad.app.sensor.TouchFilter
import com.airpad.app.service.AirPadService
import com.airpad.app.util.HapticManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AirPadViewModel(application: Application) : AndroidViewModel(application) {

    private val profileManager = ProfileManager(application)
    private val sessionManager = SessionManager(viewModelScope)
    private val hapticManager = HapticManager(application)

    // 1. Connection State
    val connectionState: StateFlow<ConnectionState> = sessionManager.connectionState

    // 2. Discovered LAN Servers
    val discoveredServers: StateFlow<List<DiscoveredServer>> = sessionManager.discoveryClient.discoveredServers
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Current App Mode
    private val _currentMode = MutableStateFlow(AppMode.CONNECT)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    // 4. Profiles Management
    private val _allProfiles = MutableStateFlow<List<ControlProfile>>(emptyList())
    val allProfiles: StateFlow<List<ControlProfile>> = _allProfiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(ControlProfile.DEFAULT)
    val activeProfile: StateFlow<ControlProfile> = _activeProfile.asStateFlow()

    // Active game window auto-detection info
    private val _activeGameProcess = MutableStateFlow<String?>(null)
    val activeGameProcess: StateFlow<String?> = _activeGameProcess.asStateFlow()

    private val _activeWindowTitle = MutableStateFlow<String?>(null)
    val activeWindowTitle: StateFlow<String?> = _activeWindowTitle.asStateFlow()

    private val _autoDetectEnabled = MutableStateFlow(true)
    val autoDetectEnabled: StateFlow<Boolean> = _autoDetectEnabled.asStateFlow()

    // 5. Active Configurations
    private val _touchConfig = MutableStateFlow(TouchConfig())
    val touchConfig: StateFlow<TouchConfig> = _touchConfig.asStateFlow()

    private val _gyroConfig = MutableStateFlow(GyroConfig())
    val gyroConfig: StateFlow<GyroConfig> = _gyroConfig.asStateFlow()

    private val _stickConfig = MutableStateFlow(StickConfig())
    val stickConfig: StateFlow<StickConfig> = _stickConfig.asStateFlow()

    private val _buttonMapping = MutableStateFlow(ButtonMappingConfig())
    val buttonMapping: StateFlow<ButtonMappingConfig> = _buttonMapping.asStateFlow()

    private val _hapticProfile = MutableStateFlow(HapticProfile())
    val hapticProfile: StateFlow<HapticProfile> = _hapticProfile.asStateFlow()

    val gyroMode: StateFlow<GyroMode> = _gyroConfig
        .map { it.mode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GyroMode.OFF)

    // Hold-to-aim active state
    private val _isAimingHoldActive = MutableStateFlow(false)
    val isAimingHoldActive: StateFlow<Boolean> = _isAimingHoldActive.asStateFlow()

    // Turbo rapid-fire active state
    private var activeTurboJob: Job? = null
    private var heldTurboButtons = mutableSetOf<Int>()

    // 6. Visual Feedback
    private val _gestureFeedback = MutableStateFlow<String?>(null)
    val gestureFeedback: StateFlow<String?> = _gestureFeedback.asStateFlow()
    private var gestureFeedbackJob: Job? = null

    // 7. Filters & Fusion
    private val touchFilter = TouchFilter()
    private val gyroFilter = GyroFilter()
    private val leftStickFilter = StickFilter()
    private val rightStickFilter = StickFilter()

    private val sensorFusionManager = SensorFusionManager(application) { rawImu ->
        handleImuUpdate(rawImu)
    }

    private var isRatchetActive = false
    private var gyroAccumulatorX = 0f
    private var gyroAccumulatorY = 0f
    private var gyroSampleCount = 0
    private val gyroLock = Any()
    private var gyroTickerJob: Job? = null

    init {
        loadProfiles()
        observeConnectionLifecycle()
        startGyroTicker()
        _autoDetectEnabled.value = profileManager.isAutoDetectEnabled()
    }

    private fun observeConnectionLifecycle() {
        viewModelScope.launch {
            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        AirPadService.startService(getApplication(), state.hostName)
                    }
                    is ConnectionState.Reconnecting -> {
                        AirPadService.updateStatus(
                            getApplication(),
                            "Reconnecting to ${state.hostName} (${state.attempt}/${state.maxAttempts})..."
                        )
                    }
                    is ConnectionState.Disconnected, is ConnectionState.Error -> {
                        AirPadService.stopService(getApplication())
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadProfiles() {
        val loadedAll = profileManager.getAllProfiles()
        val active = profileManager.getActiveProfile()
        _allProfiles.value = loadedAll
        _activeProfile.value = active
        applyProfile(active, persist = false)
    }

    private fun applyProfile(profile: ControlProfile, persist: Boolean = true) {
        _activeProfile.value = profile
        _touchConfig.value = profile.touchConfig
        _gyroConfig.value = profile.gyroConfig
        _stickConfig.value = profile.stickConfig
        _buttonMapping.value = profile.buttonMapping
        _hapticProfile.value = profile.hapticProfile

        // Update filters & haptics live
        touchFilter.sensitivity = profile.touchConfig.sensitivity
        touchFilter.deadzone = profile.touchConfig.deadzone
        touchFilter.smoothingFactor = profile.touchConfig.smoothingFactor

        gyroFilter.updateFromConfig(profile.gyroConfig)
        leftStickFilter.updateConfig(profile.stickConfig)
        rightStickFilter.updateConfig(profile.stickConfig)
        hapticManager.setProfile(profile.hapticProfile)

        sensorFusionManager.updateConfig(profile.gyroConfig)

        if (persist) {
            profileManager.saveActiveProfile(profile)
        }
    }

    // --- Profile Management ---
    fun selectProfile(profileId: String) {
        val profile = profileManager.selectProfile(profileId)
        _activeProfile.value = profile
        _allProfiles.value = profileManager.getAllProfiles()
        applyProfile(profile, persist = true)
        sessionManager.sendModeSwitch(profile.gyroConfig)
    }

    fun createCustomProfile(name: String, genre: String = "General") {
        val base = _activeProfile.value.copy(genre = genre)
        val newProfile = profileManager.createCustomProfile(name, base)
        _activeProfile.value = newProfile
        _allProfiles.value = profileManager.getAllProfiles()
        applyProfile(newProfile, persist = true)
    }

    fun deleteCustomProfile(profileId: String) {
        val fallback = profileManager.deleteCustomProfile(profileId)
        _allProfiles.value = profileManager.getAllProfiles()
        _activeProfile.value = fallback
        applyProfile(fallback, persist = true)
    }

    fun setAutoDetectEnabled(enabled: Boolean) {
        _autoDetectEnabled.value = enabled
        profileManager.setAutoDetectEnabled(enabled)
    }

    fun handleActiveWindowDetected(processName: String, windowTitle: String) {
        _activeGameProcess.value = processName
        _activeWindowTitle.value = windowTitle

        if (!_autoDetectEnabled.value) return

        val matched = profileManager.detectAndSelectProfile(processName, windowTitle)
        if (matched != null && matched.id != _activeProfile.value.id) {
            selectProfile(matched.id)
            triggerGestureFeedback("Auto-Switched to: ${matched.name}")
        }
    }

    fun importProfileFromJson(jsonStr: String): Boolean {
        val imported = profileManager.importProfile(jsonStr) ?: return false
        _allProfiles.value = profileManager.getAllProfiles()
        selectProfile(imported.id)
        performHapticHeavyClick()
        return true
    }

    fun exportActiveProfileToJson(): String {
        return profileManager.exportProfileToJson(_activeProfile.value)
    }

    // --- Mode Navigation ---
    fun selectMode(mode: AppMode) {
        _currentMode.value = mode
    }

    // --- Gyro Mode ---
    fun setGyroMode(mode: GyroMode) {
        val updated = _gyroConfig.value.copy(mode = mode)
        updateGyroConfig(updated)
    }

    fun toggleGyroForGamepad() {
        val next = if (_gyroConfig.value.mode == GyroMode.GYRO_TO_STICK) GyroMode.OFF else GyroMode.GYRO_TO_STICK
        setGyroMode(next)
    }

    fun toggleGyroForMouse() {
        val next = if (_gyroConfig.value.mode == GyroMode.GYRO_TO_MOUSE) GyroMode.OFF else GyroMode.GYRO_TO_MOUSE
        setGyroMode(next)
    }

    fun setRatchetActive(active: Boolean) {
        isRatchetActive = active
        if (!active) {
            synchronized(gyroLock) {
                gyroAccumulatorX = 0f
                gyroAccumulatorY = 0f
                gyroSampleCount = 0
            }
            gyroFilter.reset()
        }
    }

    private fun startGyroTicker() {
        gyroTickerJob?.cancel()
        gyroTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(10) // ~100Hz loop
                if (connectionState.value is ConnectionState.Connected &&
                    _gyroConfig.value.mode == GyroMode.GYRO_TO_MOUSE &&
                    isRatchetActive
                ) {
                    var yaw = 0f
                    var pitch = 0f
                    synchronized(gyroLock) {
                        if (gyroSampleCount > 0) {
                            yaw = gyroAccumulatorX / gyroSampleCount
                            pitch = gyroAccumulatorY / gyroSampleCount
                            gyroAccumulatorX = 0f
                            gyroAccumulatorY = 0f
                            gyroSampleCount = 0
                        }
                    }
                    val config = _gyroConfig.value
                    val (dx, dy) = gyroFilter.processMouseDelta(
                        rawYaw = yaw,
                        rawPitch = pitch,
                        dt = 0.010f,
                        invertX = config.invertX,
                        invertY = config.invertY,
                        isHoldingAimButton = _isAimingHoldActive.value
                    )
                    if (dx != 0f || dy != 0f) {
                        sessionManager.sendMouseDelta(dx, dy)
                    }
                }
            }
        }
    }

    // --- Configuration Updates ---
    fun updateGyroConfig(newConfig: GyroConfig) {
        _gyroConfig.value = newConfig
        gyroFilter.updateFromConfig(newConfig)
        sensorFusionManager.updateConfig(newConfig)
        sessionManager.sendModeSwitch(newConfig)

        val updatedProfile = _activeProfile.value.copy(gyroConfig = newConfig)
        _activeProfile.value = updatedProfile
        profileManager.saveActiveProfile(updatedProfile)
    }

    fun updateStickConfig(newConfig: StickConfig) {
        _stickConfig.value = newConfig
        leftStickFilter.updateConfig(newConfig)
        rightStickFilter.updateConfig(newConfig)

        val updatedProfile = _activeProfile.value.copy(stickConfig = newConfig)
        _activeProfile.value = updatedProfile
        profileManager.saveActiveProfile(updatedProfile)
    }

    fun updateButtonMapping(newConfig: ButtonMappingConfig) {
        _buttonMapping.value = newConfig
        val updatedProfile = _activeProfile.value.copy(buttonMapping = newConfig)
        _activeProfile.value = updatedProfile
        profileManager.saveActiveProfile(updatedProfile)
    }

    fun updateHapticProfile(newProfile: HapticProfile) {
        _hapticProfile.value = newProfile
        hapticManager.setProfile(newProfile)
        val updatedProfile = _activeProfile.value.copy(hapticProfile = newProfile)
        _activeProfile.value = updatedProfile
        profileManager.saveActiveProfile(updatedProfile)
    }

    fun updateTouchConfig(newConfig: TouchConfig) {
        _touchConfig.value = newConfig
        touchFilter.sensitivity = newConfig.sensitivity
        touchFilter.deadzone = newConfig.deadzone
        touchFilter.smoothingFactor = newConfig.smoothingFactor
        touchFilter.accelerationEnabled = newConfig.accelerationEnabled
        touchFilter.accelThreshold = newConfig.accelThreshold
        touchFilter.accelGain = newConfig.accelGain
        touchFilter.accelExponent = newConfig.accelExponent
        touchFilter.subpixelPrecision = newConfig.subpixelPrecision
        touchFilter.predictionEnabled = newConfig.predictionEnabled
        touchFilter.predictionFrames = newConfig.predictionFrames

        val updatedProfile = _activeProfile.value.copy(touchConfig = newConfig)
        _activeProfile.value = updatedProfile
        profileManager.saveActiveProfile(updatedProfile)
    }

    private fun handleImuUpdate(rawImu: RawImuEvent) {
        if (connectionState.value !is ConnectionState.Connected) return
        val config = _gyroConfig.value
        if (config.mode == GyroMode.OFF) return

        val rawHorizontal = if (config.horizontalAxis == GyroHorizontalAxis.ROLL) rawImu.gyroY else rawImu.gyroZ
        val rawVertical = rawImu.gyroX

        if (config.mode == GyroMode.GYRO_TO_MOUSE) {
            if (isRatchetActive) {
                synchronized(gyroLock) {
                    gyroAccumulatorX += rawHorizontal
                    gyroAccumulatorY += rawVertical
                    gyroSampleCount++
                }
            }
        } else if (config.mode == GyroMode.GYRO_TO_STICK) {
            sessionManager.sendRawImu(rawImu)
        }
    }

    // --- Filtered Gamepad Dispatcher ---
    fun sendFilteredGamepadState(
        buttonMask: Int,
        rawLx: Float,
        rawLy: Float,
        rawRx: Float,
        rawRy: Float,
        rawLt: Float,
        rawRt: Float
    ) {
        // Apply Button Remappings
        var mappedMask = 0
        val mapping = _buttonMapping.value
        for (bit in 0 until 32) {
            val mask = 1 shl bit
            if ((buttonMask and mask) != 0) {
                val targetBit = mapping.remappings[mask] ?: mask
                mappedMask = mappedMask or targetBit
            }
        }

        // Apply Chording Rules (combos like LB+A -> Y)
        for (chord in mapping.chordingRules) {
            if ((mappedMask and chord.primaryBit) != 0 && (mappedMask and chord.secondaryBit) != 0) {
                mappedMask = (mappedMask and chord.primaryBit.inv()) and chord.secondaryBit.inv()
                mappedMask = mappedMask or chord.outputBit
            }
        }

        // Check Hold-To-Aim status
        val aimButton = _gyroConfig.value.holdToAimButton
        val isAiming = _gyroConfig.value.holdToAimEnabled && ((mappedMask and aimButton) != 0 || rawLt > 0.5f)
        if (_isAimingHoldActive.value != isAiming) {
            _isAimingHoldActive.value = isAiming
        }

        // Handle Turbo / Rapid-Fire
        handleTurboLoop(mappedMask, mapping.turboButtons, mapping.turboRateHz)

        // Process Left & Right Sticks through advanced filters
        val (filteredLx, filteredLy) = leftStickFilter.processStick(rawLx, -rawLy) {
            hapticManager.performEdgeTick()
        }
        val (filteredRx, filteredRy) = rightStickFilter.processStick(rawRx, -rawRy) {
            hapticManager.performEdgeTick()
        }

        val ltByte = (rawLt * 255).toInt().coerceIn(0, 255).toUByte()
        val rtByte = (rawRt * 255).toInt().coerceIn(0, 255).toUByte()

        sessionManager.sendGamepadState(
            GamepadStateEvent(
                buttonMask = mappedMask,
                leftStickX = filteredLx,
                leftStickY = filteredLy,
                rightStickX = filteredRx,
                rightStickY = filteredRy,
                leftTrigger = ltByte,
                rightTrigger = rtByte
            )
        )
    }

    private fun handleTurboLoop(currentMask: Int, turboButtons: Set<Int>, rateHz: Int) {
        if (turboButtons.isEmpty()) return

        val activeTurbos = turboButtons.filter { (currentMask and it) != 0 }
        if (activeTurbos.isEmpty()) {
            activeTurboJob?.cancel()
            activeTurboJob = null
            heldTurboButtons.clear()
            return
        }

        if (activeTurboJob == null || !activeTurboJob!!.isActive) {
            val intervalMs = (1000L / rateHz.coerceIn(5, 30)) / 2
            activeTurboJob = viewModelScope.launch {
                var toggleState = false
                while (isActive) {
                    toggleState = !toggleState
                    for (btnBit in activeTurbos) {
                        sessionManager.sendGamepadButton(btnBit, if (toggleState) ProtocolConstants.ACTION_PRESS else ProtocolConstants.ACTION_RELEASE)
                    }
                    if (toggleState) hapticManager.performTurboTick()
                    delay(intervalMs)
                }
            }
        }
    }

    fun executeMacro(macro: MacroSequence) {
        viewModelScope.launch {
            triggerGestureFeedback("Executing Macro: ${macro.name}")
            for (step in macro.steps) {
                sessionManager.sendGamepadButton(step.buttonBit, step.action)
                hapticManager.performTick()
                if (step.delayMs > 0) {
                    delay(step.delayMs)
                }
            }
        }
    }

    // --- Touch & Mouse Dispatchers ---
    fun sendProcessedTouchDelta(rawDx: Float, rawDy: Float) {
        val (filteredDx, filteredDy) = touchFilter.processDelta(rawDx, rawDy)
        val finalDx = if (_touchConfig.value.invertX) -filteredDx else filteredDx
        val finalDy = if (_touchConfig.value.invertY) -filteredDy else filteredDy
        if (finalDx != 0f || finalDy != 0f) {
            sessionManager.sendMouseDelta(finalDx, finalDy)
        }
    }

    fun resetTouchFilter() {
        touchFilter.reset()
    }

    fun triggerGestureFeedback(gestureName: String) {
        _gestureFeedback.value = gestureName
        gestureFeedbackJob?.cancel()
        gestureFeedbackJob = viewModelScope.launch {
            delay(800)
            if (_gestureFeedback.value == gestureName) {
                _gestureFeedback.value = null
            }
        }
    }

    // --- Haptics ---
    fun performHapticButtonPress() {
        hapticManager.performButtonPress()
    }

    fun performHapticTick() {
        hapticManager.performTick()
    }

    fun performHapticHeavyClick() {
        hapticManager.performHeavyClick()
    }

    // --- Connection ---
    fun connectToServer(server: DiscoveredServer, pinCode: Int) {
        sessionManager.initiatePairing(server, pinCode)
    }

    fun connectManual(ip: String, port: Int, pinCode: Int) {
        val server = DiscoveredServer(
            name = "Manual Desktop Host",
            address = ip,
            port = port,
            osType = 1,
            requirePin = true
        )
        sessionManager.initiatePairing(server, pinCode)
    }

    fun cancelReconnection() {
        sessionManager.cancelReconnection()
    }

    fun disconnect() {
        sessionManager.disconnect()
        sensorFusionManager.stop()
        setGyroMode(GyroMode.OFF)
    }

    // --- Direct Input Dispatchers ---
    fun sendMouseDelta(dx: Float, dy: Float) {
        sessionManager.sendMouseDelta(dx, dy)
    }

    fun sendMouseButton(buttonMask: Byte, action: Byte) {
        sessionManager.sendMouseButton(buttonMask, action)
    }

    fun sendMouseScroll(scrollX: Short, scrollY: Short) {
        val finalScrollX = if (_touchConfig.value.invertScroll) (-scrollX).toShort() else scrollX
        val finalScrollY = if (_touchConfig.value.invertScroll) (-scrollY).toShort() else scrollY
        sessionManager.sendMouseScroll(finalScrollX, finalScrollY)
    }

    fun sendGamepadState(state: GamepadStateEvent) {
        sessionManager.sendGamepadState(state)
    }

    fun sendGamepadStick(stickId: Byte, posX: Short, posY: Short) {
        sessionManager.sendGamepadStick(stickId, posX, posY)
    }

    fun sendGamepadButton(buttonBit: Int, action: Byte) {
        sessionManager.sendGamepadButton(buttonBit, action)
    }

    fun sendGamepadTrigger(triggerId: Byte, value: UByte) {
        sessionManager.sendGamepadTrigger(triggerId, value)
    }

    fun sendKeyboardKey(keyCode: Short, action: Byte) {
        sessionManager.sendKeyboardKey(keyCode, action)
    }

    fun completeOnboarding() {
        // Mark onboarding as complete in shared preferences
        val prefs = getApplication<Application>().getSharedPreferences("airpad_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
    }

    override fun onCleared() {
        super.onCleared()
        gyroTickerJob?.cancel()
        activeTurboJob?.cancel()
        sensorFusionManager.stop()
        sessionManager.disconnect()
        AirPadService.stopService(getApplication())
    }
}
