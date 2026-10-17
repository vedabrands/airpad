package com.airpad.app.data.models

/**
 * Pre-configured gamepad profiles for different game genres.
 * Each profile tunes stick sensitivity, deadzone, and gyro settings.
 */
data class GamepadProfile(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val stickSensitivity: Float,
    val stickDeadzone: Float,
    val gyroSensitivity: Float,
    val gyroMode: GyroMode,
    val triggerThreshold: Float
) {
    companion object {
        val DEFAULT = GamepadProfile(
            id = "gp_default",
            name = "Balanced",
            description = "All-round balance for any game",
            emoji = "⚖️",
            stickSensitivity = 1.0f,
            stickDeadzone = 0.10f,
            gyroSensitivity = 1.4f,
            gyroMode = GyroMode.OFF,
            triggerThreshold = 0.5f
        )

        val FPS = GamepadProfile(
            id = "gp_fps",
            name = "FPS / TPS",
            description = "Tight sticks, low deadzone, gyro aim",
            emoji = "🎯",
            stickSensitivity = 1.3f,
            stickDeadzone = 0.05f,
            gyroSensitivity = 1.8f,
            gyroMode = GyroMode.GYRO_TO_MOUSE,
            triggerThreshold = 0.3f
        )

        val RACING = GamepadProfile(
            id = "gp_racing",
            name = "Racing",
            description = "Smooth analog steering, roll-axis gyro",
            emoji = "🏎️",
            stickSensitivity = 0.8f,
            stickDeadzone = 0.15f,
            gyroSensitivity = 2.0f,
            gyroMode = GyroMode.GYRO_TO_STICK,
            triggerThreshold = 0.6f
        )

        val RPG = GamepadProfile(
            id = "gp_rpg",
            name = "RPG / Adventure",
            description = "Comfortable sticks for exploration",
            emoji = "⚔️",
            stickSensitivity = 1.0f,
            stickDeadzone = 0.12f,
            gyroSensitivity = 1.0f,
            gyroMode = GyroMode.OFF,
            triggerThreshold = 0.5f
        )

        val FIGHTING = GamepadProfile(
            id = "gp_fighting",
            name = "Fighting",
            description = "Responsive D-pad, sharp inputs",
            emoji = "👊",
            stickSensitivity = 1.2f,
            stickDeadzone = 0.08f,
            gyroSensitivity = 0.8f,
            gyroMode = GyroMode.OFF,
            triggerThreshold = 0.4f
        )

        val SIMULATION = GamepadProfile(
            id = "gp_sim",
            name = "Simulation",
            description = "Fine-grained controls for flight/space sims",
            emoji = "✈️",
            stickSensitivity = 0.6f,
            stickDeadzone = 0.20f,
            gyroSensitivity = 2.5f,
            gyroMode = GyroMode.GYRO_TO_STICK,
            triggerThreshold = 0.7f
        )

        val BUILTIN_PROFILES = listOf(DEFAULT, FPS, RACING, RPG, FIGHTING, SIMULATION)
    }
}