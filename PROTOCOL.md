# AirPad Protocol Specification (v1.0)
**Compact Binary UDP Protocol for Low-Latency Remote Air Mouse, Gamepad & Gyroscopic Input**

---

## 1. Overview & Design Principles

AirPad is a high-frequency, low-latency client-server protocol designed to operate over local 802.11 Wi-Fi networks using **UDP (User Datagram Protocol)**. It connects an Android client device (acting as an air mouse, game controller, and motion sensor) to a Desktop service (injecting native mouse, keyboard, and virtual gamepad events into the host operating system).

### Core Design Goals:
1. **Minimal Packet Overhead & Zero Allocation**: Uses fixed-size, binary-packed frames (no JSON/text serialization) to minimize parsing latency (<0.1ms per packet) and avoid GC pressure on mobile and desktop runtimes.
2. **Deterministic Big-Endian (Network Byte Order)**: All multi-byte integers and IEEE 754 floating-point values are encoded in Network Byte Order (Big-Endian).
3. **Session Authentication & Packet Filtering**: UDP traffic is secured through a numeric PIN-pairing handshake that establishes a 32-bit `SessionToken` to drop unauthenticated or rogue broadcast packets.
4. **Failsafe Disconnect & Held-Input Recovery**: A continuous heartbeat watchdog on the desktop detects connection drops and instantly releases all held mouse buttons, modifier keys, and virtual gamepad analog sticks.
5. **High Sampling Rate Support**: Supports 60 Hz to 240 Hz sensor/input streaming with sub-frame time stamping and sequence tracking.

---

## 2. Network Ports & Transport Layer

| Service | Protocol | Default Port | Direction | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Discovery Beacon** | UDP Broadcast | `54320` | Desktop $\rightarrow$ LAN Broadcast | Desktop announces availability, IP, port, and pairing requirement. |
| **Control & Input** | UDP Unicast | `54321` | Phone $\longleftrightarrow$ Desktop | Handshake, heartbeat, mode switching, and input streams. |

---

## 3. Binary Frame Architecture

Every AirPad packet consists of a **16-byte Fixed Header** (aligned to a 4-byte boundary) followed by a variable-length **Payload** (0 to 1024 bytes, typically 4 to 32 bytes).

### 3.1 Common Frame Header (16 Bytes)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Magic (0x4150)       | ProtoVer(0x01)|   Flags (8b)  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Sequence Number                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         Session Token                         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     Opcode    | Reserved(0x00)|         Payload Length        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|                        Payload Data ...                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

#### Field Specifications:

| Offset | Field Name | Data Type | Description |
| :--- | :--- | :--- | :--- |
| `0x00` | **Magic** | `uint16` | Fixed value `0x4150` (ASCII `"AP"` for AirPad). Packets without this magic are dropped immediately. |
| `0x02` | **ProtoVer** | `uint8` | Protocol version (`0x01`). |
| `0x03` | **Flags** | `uint8` | Bitmask for packet properties:<br>• Bit 0 (`0x01`): `ACK_REQUEST` (Sender requests acknowledgment)<br>• Bit 1 (`0x02`): `TIME_SYNC` (Packet contains calibrated timestamp)<br>• Bits 2–7: Reserved (must be `0`). |
| `0x04` | **Sequence Number** | `uint32` | Monotonically increasing counter per session to detect dropped, out-of-order, or duplicated UDP packets. |
| `0x08` | **Session Token** | `uint32` | Random 32-bit token returned by Desktop during `PAIR_ACK`. Unpaired packets must use `0x00000000`. |
| `0x0C` | **Opcode** | `uint8` | Action/packet type identifier (see Section 4). |
| `0x0D` | **Reserved** | `uint8` | Alignment byte, set to `0x00`. |
| `0x0E` | **Payload Length** | `uint16` | Byte length of the following payload ($0 \le N \le 1024$). |

---

## 4. Opcodes & Payload Definitions

### 4.1 Opcode Registry

| Opcode | Hex | Name | Direction | Frequency | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `1` | `0x01` | `HEARTBEAT` | Client $\rightarrow$ Server | 2 Hz (when idle) | Session keepalive and timestamp ping |
| `2` | `0x02` | `HEARTBEAT_ACK` | Server $\rightarrow$ Client | On demand | Pong response with server timestamp |
| `5` | `0x05` | `DISCOVERY_BEACON` | Server $\rightarrow$ Broadcast | 1 Hz | Desktop server presence broadcast |
| `6` | `0x06` | `PAIR_REQUEST` | Client $\rightarrow$ Server | On connect | Handshake request with PIN code |
| `7` | `0x07` | `PAIR_RESPONSE` | Server $\rightarrow$ Client | On handshake | Handshake outcome & session token |
| `8` | `0x08` | `DISCONNECT` | Either $\rightarrow$ Peer | On exit | Clean session teardown |
| `16` | `0x10` | `MODE_SWITCH` | Client $\rightarrow$ Server | On state change | Toggle Gyro mode (Off / Stick / Mouse) |
| `32` | `0x20` | `MOUSE_DELTA` | Client $\rightarrow$ Server | Event / 60–120 Hz | 2D Cursor movement $(dx, dy)$ |
| `33` | `0x21` | `MOUSE_BUTTON` | Client $\rightarrow$ Server | Event | Mouse button press/release bitmask |
| `34` | `0x22` | `MOUSE_SCROLL` | Client $\rightarrow$ Server | Event | Vertical & horizontal scroll deltas |
| `35` | `0x23` | `MOUSE_COMBO` | Client $\rightarrow$ Server | Stream 60–120 Hz | Atomic Delta + Button + Scroll packet |
| `48` | `0x30` | `GAMEPAD_STATE` | Client $\rightarrow$ Server | Stream 60–120 Hz | Full controller state (sticks + buttons + triggers) |
| `49` | `0x31` | `GAMEPAD_STICK` | Client $\rightarrow$ Server | Stream | Analog stick positions $(LX, LY, RX, RY)$ |
| `50` | `0x32` | `GAMEPAD_BUTTON` | Client $\rightarrow$ Server | Event | Gamepad button bitmask |
| `51` | `0x33` | `GAMEPAD_TRIGGER` | Client $\rightarrow$ Server | Stream | Analog trigger values $(LT, RT)$ |
| `64` | `0x40` | `RAW_IMU_DATA` | Client $\rightarrow$ Server | 60–200 Hz | Raw Gyroscope & Accelerometer vectors |
| `65` | `0x41` | `GYRO_CALIBRATE` | Client $\rightarrow$ Server | On calibration | Zero-bias calibration offsets |

---

### 4.2 Detailed Payload Layouts

#### Opcode `0x01`: `HEARTBEAT` (Payload Length: 8 bytes)
Used to keep the session alive and measure round-trip time (RTT).
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     Client Timestamp (ms)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Client Timestamp`: `uint64` (Epoch time in milliseconds).

#### Opcode `0x02`: `HEARTBEAT_ACK` (Payload Length: 16 bytes)
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   Echoed Client Timestamp                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     Server Timestamp (ms)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

---

#### Opcode `0x05`: `DISCOVERY_BEACON` (Payload Length: Variable, ~40 bytes)
Broadcasted by Desktop to `255.255.255.255:54320`.
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Server Port          |    OS Type    | RequirePIN(1b)|
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| ServerNameLen |           Server Name (UTF-8 bytes)           |
| ...                                                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Server Port`: `uint16` (e.g. `54321`).
- `OS Type`: `uint8` (`1` = Windows, `2` = Linux, `3` = macOS).
- `RequirePIN`: `uint8` (`0` = Open / Auto-accept, `1` = Requires 4-digit PIN).
- `ServerNameLen`: `uint8` (length $L$).
- `Server Name`: String of $L$ UTF-8 bytes (e.g., `"Gaming-Rig"`).

---

#### Opcode `0x06`: `PAIR_REQUEST` (Payload Length: Variable, ~36 bytes)
Sent by Phone to establish connection.
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Pairing PIN Code                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                         Client UID                            |
|                                                               |
|                                                               |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| DeviceNameLen |           Device Name (UTF-8 bytes)           |
| ...                                                           |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Pairing PIN Code`: `uint32` (e.g., `1234` or numeric representation).
- `Client UID`: `16 bytes` (UUIDv4 client identifier).
- `DeviceNameLen`: `uint8` (length $M$).
- `Device Name`: String of $M$ bytes (e.g., `"Pixel 8 Pro"`).

---

#### Opcode `0x07`: `PAIR_RESPONSE` (Payload Length: 10 bytes)
Sent by Desktop in response to `PAIR_REQUEST`.
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Status Code  |   Reserved    |       Assigned Session Token  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       Assigned Session Token (cont.)          | TargetRate(Hz)|
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Status Code`: `uint8`:
  - `0x00`: `SUCCESS` (Paired & Authenticated)
  - `0x01`: `ERR_INVALID_PIN`
  - `0x02`: `ERR_VERSION_MISMATCH`
  - `0x03`: `ERR_SERVER_BUSY` (Already paired to another device)
  - `0x04`: `ERR_REJECTED_BY_USER`
- `Assigned Session Token`: `uint32` (Unique session identifier required for all future packets).
- `TargetRate(Hz)`: `uint8` (Preferred streaming rate, e.g. `60`, `100`, `120`).

---

#### Opcode `0x10`: `MODE_SWITCH` (Payload Length: 20 bytes)
Configures active operational mode and motion sensor processing.
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   Gyro Mode   |  Config Flags |           Reserved            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     Sensitivity X (float32)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     Sensitivity Y (float32)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Deadzone (float32)                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   Smoothing Factor (float32)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Gyro Mode`: `uint8`:
  - `0x00`: `OFF` (Gyro disabled)
  - `0x01`: `GYRO_TO_STICK` (Motion mapped to virtual Right Thumbstick)
  - `0x02`: `GYRO_TO_MOUSE` (Motion mapped to Desktop Cursor Delta)
- `Config Flags`: `uint8` bitmask:
  - Bit 0 (`0x01`): Invert X Axis
  - Bit 1 (`0x02`): Invert Y Axis
  - Bit 2 (`0x04`): Gyro Ratchet Active (only apply gyro when touch modifier is held)
  - Bit 3 (`0x08`): Reset Calibration Bias
- `Sensitivity X / Y`: `float32` multiplier (Default `1.0f`).
- `Deadzone`: `float32` minimum angular velocity threshold (rad/s) below which motion is ignored (Default `0.02f`).
- `Smoothing Factor`: `float32` Exponential Moving Average $\alpha \in [0.0, 0.95]$ (Default `0.25f`).

---

#### Opcode `0x20`: `MOUSE_DELTA` (Payload Length: 8 bytes)
High-frequency relative mouse cursor motion.
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            Delta X (int16)    |        Delta Y (int16)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|      Subpixel X (uint8)       |      Subpixel Y (uint8)       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Delta X / Delta Y`: `int16` signed pixel movement $(-32768 \dots +32767)$.
- `Subpixel X / Y`: `uint8` fractional offset $(0 \dots 255 \rightarrow 0.0 \dots 0.996)$.

---

#### Opcode `0x21`: `MOUSE_BUTTON` (Payload Length: 2 bytes)
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Button Bitmask| Action Type   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Button Bitmask` (`uint8`):
  - Bit 0 (`0x01`): Left Button
  - Bit 1 (`0x02`): Right Button
  - Bit 2 (`0x04`): Middle / Wheel Click
  - Bit 3 (`0x08`): Extra / Back (XBUTTON1)
  - Bit 4 (`0x10`): Extra / Forward (XBUTTON2)
- `Action Type` (`uint8`):
  - `0x00`: `BUTTON_UP` (Released)
  - `0x01`: `BUTTON_DOWN` (Pressed)
  - `0x02`: `BUTTON_CLICK` (Instant down + up)

---

#### Opcode `0x22`: `MOUSE_SCROLL` (Payload Length: 4 bytes)
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        Scroll Y (int16)       |        Scroll X (int16)       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Scroll Y`: `int16` vertical wheel delta (positive = scroll up, negative = scroll down; standard notch = $\pm 120$).
- `Scroll X`: `int16` horizontal tilt delta (positive = right, negative = left).

---

#### Opcode `0x23`: `MOUSE_COMBO` (Payload Length: 8 bytes)
Atomic frame combining delta, buttons state, and scroll to reduce packet count.
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            Delta X (int16)    |        Delta Y (int16)        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Button State  | ScrollY(int8) | ScrollX(int8) |   Reserved    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```
- `Button State`: `uint8` bitmask of currently pressed buttons.

---

#### Opcode `0x30`: `GAMEPAD_STATE` (Payload Length: 16 bytes)
Complete Virtual Xbox 360 / DualShock 4 Controller State.
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     Button Bitmask (32-bit)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         Left Stick X (int16)  |      Left Stick Y (int16)     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        Right Stick X (int16)  |     Right Stick Y (int16)     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Left Trigger  | Right Trigger |           Reserved            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

##### Gamepad Button Bitmask Definition (32-bit):
| Bit | Hex | Xbox Equivalent | PlayStation Equivalent |
| :--- | :--- | :--- | :--- |
| `0` | `0x00000001` | **A** | **Cross ($\times$)** |
| `1` | `0x00000002` | **B** | **Circle ($\bigcirc$)** |
| `2` | `0x00000004` | **X** | **Square ($\square$)** |
| `3` | `0x00000008` | **Y** | **Triangle ($\triangle$)** |
| `4` | `0x00000010` | **DPad Up** | **DPad Up** |
| `5` | `0x00000020` | **DPad Down** | **DPad Down** |
| `6` | `0x00000040` | **DPad Left** | **DPad Left** |
| `7` | `0x00000080` | **DPad Right** | **DPad Right** |
| `8` | `0x00000100` | **Left Bumper (LB)** | **L1** |
| `9` | `0x00000200` | **Right Bumper (RB)** | **R1** |
| `10` | `0x00000400` | **Left Thumb Click (LS)** | **L3** |
| `11` | `0x00000800` | **Right Thumb Click (RS)** | **R3** |
| `12` | `0x00001000` | **Start / Menu** | **Options** |
| `13` | `0x00002000` | **Back / View** | **Share / Create** |
| `14` | `0x00004000` | **Guide / Xbox Logo** | **PS Home** |
| `15` | `0x00008000` | **Touchpad Click** | **Touchpad Click** |

- `Sticks`: `int16` range $[-32768 \dots +32767]$, centered at $0$.
- `Triggers`: `uint8` range $[0 \dots 255]$ ($0$ = fully released, $255$ = fully depressed).

---

#### Opcode `0x40`: `RAW_IMU_DATA` (Payload Length: 24 bytes)
Streams raw fused sensor data for desktop-side math / gyro processing.
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Gyro X (float32 rad/s)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Gyro Y (float32 rad/s)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Gyro Z (float32 rad/s)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Accel X (float32 m/s²)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Accel Y (float32 m/s²)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Accel Z (float32 m/s²)                   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

---

## 5. Gyroscopic Modes & Mathematical Mapping

The client and server coordinate three distinct Gyro processing modes via opcode `0x10` (`MODE_SWITCH`).

```
                    +-----------------------+
                    |   Phone IMU Sensor    |
                    | Gyroscope (rad/s)     |
                    +-----------+-----------+
                                |
                   [Mode Switch Opcode 0x10]
                                |
        +-----------------------+-----------------------+
        |                       |                       |
   Mode 0: OFF             Mode 1: STICK           Mode 2: MOUSE
   (Sensor idle)      (Gyro to Right Stick)      (Gyro to Desktop Cursor)
        |                       |                       |
     No Delta         +---------+---------+   +---------+---------+
                      | Deadzone Filter   |   | Deadzone Filter   |
                      | Sensitivity & Expo|   | Angular Rate Int. |
                      | Output: RX, RY    |   | Output: dx, dy    |
                      +-------------------+   +-------------------+
```

### 5.1 Mode 0: `OFF` (`0x00`)
- Motion sensor sampling is paused on the Android client to conserve battery.
- Any residual gyro bias on the desktop is cleared to 0.

### 5.2 Mode 1: `GYRO_TO_STICK` (`0x01`)
Designed for console-style games where camera control is bound to the Right Analog Stick (e.g., FPS aim assist).

#### Transformation Pipeline:
1. **Coordinate Mapping (Device in Landscape Orientation)**:
   $$\omega_{\text{yaw}} = \text{Gyro}_Z, \quad \omega_{\text{pitch}} = \text{Gyro}_X, \quad \omega_{\text{roll}} = \text{Gyro}_Y$$
2. **Deadzone Thresholding**:
   $$\omega' = \begin{cases} 0 & \text{if } |\omega| < \text{Deadzone} \\ \text{sgn}(\omega)(|\omega| - \text{Deadzone}) & \text{otherwise} \end{cases}$$
3. **Exponential Sensitivity & Axis Inversion**:
   $$V_X = \text{clamp}\left( (\omega'_{\text{yaw}} \cdot S_X)^{\gamma} \cdot 32767, -32768, 32767 \right) \cdot \text{Inv}_X$$
   $$V_Y = \text{clamp}\left( (\omega'_{\text{pitch}} \cdot S_Y)^{\gamma} \cdot 32767, -32768, 32767 \right) \cdot \text{Inv}_Y$$
4. **Smoothing Filter (Exponential Moving Average)**:
   $$R_t = \alpha R_{t-1} + (1 - \alpha) V_t \quad (\alpha \in [0.0, 0.95])$$
5. **Output**: Directly written to Virtual Gamepad `RightStickX` and `RightStickY`.

### 5.3 Mode 2: `GYRO_TO_MOUSE` (`0x02`)
Turns the smartphone into a precision 2D Air Mouse for PC desktop navigation or raw mouse-look in PC games.

#### Transformation Pipeline:
1. **Angular Velocity to Pixel Velocity**:
   $$v_x(t) = -\omega_{\text{yaw}}(t) \cdot S_x \cdot \Delta t \cdot \text{DPI}_{\text{scale}}$$
   $$v_y(t) = -\omega_{\text{pitch}}(t) \cdot S_y \cdot \Delta t \cdot \text{DPI}_{\text{scale}}$$
2. **Gyro Ratchet (Clutching) Mechanism**:
   When `Config Flags` has bit 2 (`0x04`) set, gyro deltas are only accumulated while the user touches a dedicated sensor trackpad on the phone screen (preventing cursor wander when repositioning the hand).
3. **Subpixel Accumulator**:
   Accumulates fractional pixel remainders to ensure smooth micro-movements without rounding stutter:
   $$\text{acc}_x \leftarrow \text{acc}_x + v_x$$
   $$\Delta X_{\text{int}} = \lfloor \text{acc}_x \rfloor, \quad \text{acc}_x \leftarrow \text{acc}_x - \Delta X_{\text{int}}$$

---

## 6. Discovery & Pairing State Machine

```
   [ Desktop Service ]                                  [ Android App ]
            |                                                  |
     (Start UDP 54320)                                  (Start Discovery)
            |                                                  |
            | === DISCOVERY_BEACON (0x05) [Broadcast] =======> |
            |     (Server IP, Port 54321, Name, PIN Required)  |
            |                                                  |
            |                                            [User enters PIN]
            |                                                  |
            | <== PAIR_REQUEST (0x06) ======================== |
            |     (PIN, Device Name, Client UUID)              |
            |                                                  |
    [Verify PIN Code]                                          |
            |                                                  |
    [Success: Token=0x9A4F12C0]                                |
            |                                                  |
            | === PAIR_RESPONSE (0x07) ======================> |
            |     (Status: SUCCESS, SessionToken: 0x9A4F12C0)  |
            |                                                  |
    [Session ACTIVE]                                    [Session ACTIVE]
            |                                                  |
            | <== INPUT / MOUSE / GAMEPAD (with Token) ======= |
            |                                                  |
```

### 6.1 Discovery Phase:
1. **Desktop Broadcast**: Desktop listens on `0.0.0.0:54321` and sends a `DISCOVERY_BEACON` (`0x05`) every 1.0 second to UDP broadcast `255.255.255.255:54320`.
2. **Direct Connect Fallback**: If UDP broadcast is blocked by Wi-Fi AP isolation / router configuration, the Android app provides manual IP + Port + PIN input fields.

### 6.2 Pairing & Authentication:
1. When launched, Desktop generates a 4-digit PIN (e.g. `4829`) and displays it in its UI / CLI.
2. The Android client sends `PAIR_REQUEST` (`0x06`) containing the entered PIN.
3. If PIN matches, Desktop generates a cryptographically secure 32-bit `SessionToken` and responds with `PAIR_RESPONSE` (`0x07`, `Status = 0x00`).
4. Both client and server transition to the **CONNECTED** state. All subsequent frames must include the matching `SessionToken`. Any frame received with an invalid or zero token is dropped.

---

## 7. Heartbeat Watchdog & Failsafe Input Release

UDP is connectionless; if the mobile device leaves Wi-Fi range, crashes, or runs out of battery while a button is held down (e.g., Left Mouse Down or Gamepad Trigger at 100%), the desktop must not leave inputs permanently stuck.

```
       Last Valid Packet Received
                   |
                   v
       +-----------------------+
       |   Watchdog Ticker     | (Runs every 100ms on Desktop)
       +-----------+-----------+
                   |
          Elapsed > 500ms ?
             /           \
           YES            NO
           /                \
   [Mark SOFT_LAG]     [Normal Flow]
          |
   Elapsed > 1500ms ?
          |
         YES
          |
          v
+------------------------------------+
|     TRIGGER EMERGENCY RESET        |
|------------------------------------|
| 1. Release all Mouse Buttons (L/R) |
| 2. Release all Gamepad Buttons     |
| 3. Center Gamepad Analog Sticks    |
| 4. Reset Gamepad Triggers to 0     |
| 5. Clear Gyro Drift / Bias         |
| 6. Invalidate Session Token        |
| 7. Set State to DISCONNECTED       |
+------------------------------------+
```

### 7.1 Timeout Thresholds
| Parameter | Value | Behavior |
| :--- | :--- | :--- |
| **Client Heartbeat Interval** | `500 ms` | Client transmits `HEARTBEAT` (`0x01`) when no input packets are generated. |
| **Input As Keepalive** | `Implicit` | Any valid input packet (`MOUSE_DELTA`, `GAMEPAD_STATE`, etc.) updates the `lastPacketReceived` timestamp. |
| **Soft Lag Warning** | `500 ms` | Desktop flags network jitter / packet loss. |
| **Hard Disconnect Timeout** | `1500 ms` | Desktop executes `failsafeReleaseAll()` and closes the session. |

### 7.2 Windows OS `failsafeReleaseAll()` Execution Specification
When a timeout is reached, the desktop agent must execute:
1. **Mouse Reset**:
   ```c
   INPUT inputs[4] = {0};
   inputs[0].type = INPUT_MOUSE; inputs[0].mi.dwFlags = MOUSEEVENTF_LEFTUP;
   inputs[1].type = INPUT_MOUSE; inputs[1].mi.dwFlags = MOUSEEVENTF_RIGHTUP;
   inputs[2].type = INPUT_MOUSE; inputs[2].mi.dwFlags = MOUSEEVENTF_MIDDLEUP;
   inputs[3].type = INPUT_MOUSE; inputs[3].mi.dwFlags = MOUSEEVENTF_XUP;
   SendInput(4, inputs, sizeof(INPUT));
   ```
2. **Virtual Gamepad Reset (ViGEmBus / XInput / vJoy)**:
   - `wButtons = 0x0000`
   - `sThumbLX = 0`, `sThumbLY = 0`
   - `sThumbRX = 0`, `sThumbRY = 0`
   - `bLeftTrigger = 0`, `bRightTrigger = 0`
   - Push empty report to virtual gamepad device.

---

## 8. Summary of Protocol Constants (C / Kotlin / JS Mapping)

```
MAGIC_BYTES           = 0x4150  // "AP"
PROTOCOL_VERSION      = 0x01

OP_HEARTBEAT          = 0x01
OP_HEARTBEAT_ACK      = 0x02
OP_DISCOVERY_BEACON   = 0x05
OP_PAIR_REQUEST       = 0x06
OP_PAIR_RESPONSE      = 0x07
OP_DISCONNECT         = 0x08
OP_MODE_SWITCH        = 0x10
OP_MOUSE_DELTA        = 0x20
OP_MOUSE_BUTTON       = 0x21
OP_MOUSE_SCROLL       = 0x22
OP_MOUSE_COMBO        = 0x23
OP_GAMEPAD_STATE      = 0x30
OP_GAMEPAD_STICK      = 0x31
OP_GAMEPAD_BUTTON     = 0x32
OP_GAMEPAD_TRIGGER    = 0x33
OP_RAW_IMU_DATA       = 0x40
OP_GYRO_CALIBRATE     = 0x41

GYRO_MODE_OFF         = 0x00
GYRO_MODE_TO_STICK    = 0x01
GYRO_MODE_TO_MOUSE    = 0x02

DISCONNECT_TIMEOUT_MS = 1500
HEARTBEAT_INTERVAL_MS = 500
DISCOVERY_PORT        = 54320
CONTROL_PORT          = 54321
```
