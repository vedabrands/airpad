# AirPad Android App (Kotlin + Jetpack Compose)

AirPad is an Android application that turns your smartphone into an ultra-low-latency Air Mouse, Gamepad, and Gyroscope motion controller for your PC over local Wi-Fi.

---

## Architecture Overview

- **Jetpack Compose UI**: Modern, declarative UI with fluid gesture recognition and haptic feedback.
- **Binary UDP Network Layer**: Zero-JSON, compact binary frame serialization using Kotlin `ByteBuffer` (Network Big-Endian).
- **LAN Discovery & PIN Handshake**: Automatically discovers desktop hosts on the LAN and pairs via a 6-digit PIN code.
- **Sensor Fusion & Gyro Aiming**: High-frequency sensor sampling (`HIGH_SAMPLING_RATE_SENSORS`) with deadzone compensation, exponential curve scaling, and low-pass filtering.

---

## Project Structure

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/airpad/app/
│   │   │   ├── data/
│   │   │   │   ├── ProtocolConstants.kt    # Opcodes & binary frame specifications
│   │   │   │   └── models/                 # Input events & connection state
│   │   │   ├── network/
│   │   │   │   ├── PacketEncoder.kt        # Big-Endian binary frame serializer
│   │   │   │   ├── UdpClient.kt            # Coroutines-based non-blocking UDP socket
│   │   │   │   ├── DiscoveryClient.kt      # LAN UDP broadcast listener
│   │   │   │   └── SessionManager.kt       # Pairing handshake & heartbeat keepalive
│   │   │   ├── sensor/
│   │   │   │   ├── SensorFusionManager.kt  # IMU / Gyroscope hardware interface
│   │   │   │   └── GyroFilter.kt           # Smoothing & deadzone math
│   │   │   ├── ui/
│   │   │   │   ├── screens/                # Connection, AirMouse, Gamepad & Settings screens
│   │   │   │   └── theme/                  # Dark mode color palette & typography
│   │   │   ├── AirPadApplication.kt
│   │   │   └── MainActivity.kt
│   │   └── res/                            # Android resources, strings, themes
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```
