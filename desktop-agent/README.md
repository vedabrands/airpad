# AirPad Desktop Agent (Windows Background Service)

The AirPad Desktop Agent is a lightweight, low-latency Node.js UDP background service that receives compact binary input frames from the AirPad Android client and injects real mouse movements, mouse button events, mouse scroll wheel events, keyboard keys, and virtual Xbox 360 controller inputs on Windows.

---

## Features

- **High-Performance UDP Listener**: Processes 60–240 Hz binary input frames with sub-millisecond parsing overhead on port `54321`.
- **Bi-Directional LAN Discovery**:
  - Broadcasts presence beacons on UDP port `54320` across all local subnet broadcast addresses (`192.168.x.255`, `255.255.255.255`).
  - Actively listens for discovery query pings from Android clients and immediately replies with direct unicast beacons.
- **PIN-Based Pairing & Security**: Requires a 6-digit pairing PIN shown in the console and system tray to prevent unauthorized control. Supports instant PIN regeneration.
- **Windows System Tray Integration**:
  - Live connection status ("Waiting for phone..." / "Connected: [Device Name]").
  - One-click copy PIN to clipboard.
  - "Regenerate PIN" action.
  - "Virtual Gamepad" toggle.
  - "Disconnect Phone" action.
  - Balloon notification alerts on device connection, disconnection, and PIN changes.
- **Virtual Xbox 360 Controller (ViGEmBus)**:
  - Full dual analog sticks, triggers (LT/RT), D-pad, bumpers, face buttons (A/B/X/Y), and Start/Back.
  - Sub-millisecond Python `vgamepad` bridge.
  - Coexistence: Gyro-to-mouse cursor aiming works simultaneously while analog sticks control character movement.
  - Non-Windows Fallback: Maps gamepad sticks to WASD / Arrow cluster and buttons to keycodes.
- **Settings Persistence**: Auto-manages runtime configuration via `config.json` (`controlPort`, `discoveryPort`, `enableVirtualGamepad`, `pairingPin`, `requirePin`, `gyro` parameters).
- **Failsafe Watchdog & Emergency Input Reset**: If heartbeat or packets cease for >1500ms, all held mouse buttons, keyboard keys, and virtual controller axes/buttons are immediately released.
- **Traffic Rate Logger**: Emits clean summary reports (`[Traffic] 120 pkts/s (15.4 kbps)`) without flooding console scrollback.

---

## Getting Started

### Prerequisites
- Node.js v18.0.0 or higher
- Windows 10 / Windows 11 (with ViGEmBus driver installed for virtual Xbox 360 controller support)
- Python 3.x (with `vgamepad` package)

### Installation & Run

```bash
# Navigate to desktop agent folder
cd desktop-agent

# Install dependencies
npm install

# Start the desktop service
npm start
```

### Configuration (`config.json`)

The desktop service creates and maintains a `config.json` file in its root directory:

```json
{
  "controlPort": 54321,
  "discoveryPort": 54320,
  "serverName": "MyPC (AirPad Host)",
  "enableVirtualGamepad": true,
  "enableSystemTray": true,
  "pairingPin": "auto",
  "requirePin": true,
  "broadcastIntervalMs": 1000,
  "disconnectTimeoutMs": 1500,
  "watchdogTickMs": 100,
  "statsIntervalMs": 2000,
  "logLevel": "info",
  "gyro": {
    "sensitivityX": 1.2,
    "sensitivityY": 1.2,
    "deadzone": 0.02,
    "smoothingFactor": 0.25
  }
}
```

---

## Running Tests

```bash
npm test
```
