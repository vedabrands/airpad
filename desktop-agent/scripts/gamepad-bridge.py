"""
AirPad Windows Virtual Gamepad Bridge (ViGEmBus / vgamepad)
-----------------------------------------------------------
Emulates a virtual Xbox 360 controller on Windows via ViGEmBus driver.
Receives high-throughput command streams via stdin from the Node.js desktop service.

Supported Commands:
  S <mask> <lx> <ly> <rx> <ry> <lt> <rt>   - Full Gamepad State
  J <stick_id> <x> <y>                     - Analog Joystick (0: Left, 1: Right)
  B <button_bit> <action>                  - Button Action (1: Press, 0: Release)
  T <trigger_id> <value>                   - Trigger Value (0: Left, 1: Right, 0-255)
  R                                        - Reset to neutral
  Q                                        - Quit
"""

import sys
import signal

try:
    import vgamepad as vg
except ImportError as e:
    sys.stderr.write(f"[GamepadBridge] Error importing vgamepad: {e}\n")
    sys.stderr.flush()
    sys.exit(1)

# Protocol Bitmask to vgamepad XUSB_BUTTON mapping
BUTTON_MAP = {
    0x0001: vg.XUSB_BUTTON.XUSB_GAMEPAD_A,
    0x0002: vg.XUSB_BUTTON.XUSB_GAMEPAD_B,
    0x0004: vg.XUSB_BUTTON.XUSB_GAMEPAD_X,
    0x0008: vg.XUSB_BUTTON.XUSB_GAMEPAD_Y,
    0x0010: vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,
    0x0020: vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,
    0x0040: vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,
    0x0080: vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT,
    0x0100: vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,
    0x0200: vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,
    0x0400: vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,
    0x0800: vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB,
    0x1000: vg.XUSB_BUTTON.XUSB_GAMEPAD_START,
    0x2000: vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK,
    0x4000: vg.XUSB_BUTTON.XUSB_GAMEPAD_GUIDE,
}

def clamp(val, min_val, max_val):
    return max(min_val, min(max_val, val))

def main():
    try:
        pad = vg.VX360Gamepad()
    except Exception as e:
        sys.stderr.write(f"[GamepadBridge] Failed to initialize VX360Gamepad: {e}\n")
        sys.stderr.flush()
        sys.exit(2)

    current_button_mask = 0

    def cleanup_and_exit(signum=None, frame=None):
        try:
            pad.reset()
            pad.update()
        except Exception:
            pass
        sys.exit(0)

    signal.signal(signal.SIGINT, cleanup_and_exit)
    signal.signal(signal.SIGTERM, cleanup_and_exit)

    # Signal Node.js parent process that the virtual gamepad is ready
    sys.stdout.write("READY\n")
    sys.stdout.flush()

    # Main command ingestion loop
    for raw_line in sys.stdin:
        line = raw_line.strip()
        if not line:
            continue

        parts = line.split()
        cmd = parts[0]

        try:
            if cmd == "S":
                # S <mask> <lx> <ly> <rx> <ry> <lt> <rt>
                mask = int(parts[1])
                lx = clamp(int(parts[2]), -32768, 32767)
                ly = clamp(int(parts[3]), -32768, 32767)
                rx = clamp(int(parts[4]), -32768, 32767)
                ry = clamp(int(parts[5]), -32768, 32767)
                lt = clamp(int(parts[6]), 0, 255)
                rt = clamp(int(parts[7]), 0, 255)

                pad.left_joystick(x_value=lx, y_value=ly)
                pad.right_joystick(x_value=rx, y_value=ry)
                pad.left_trigger(value=lt)
                pad.right_trigger(value=rt)

                # Update buttons based on diff against current mask
                diff = current_button_mask ^ mask
                if diff:
                    for bit, btn_enum in BUTTON_MAP.items():
                        if diff & bit:
                            if mask & bit:
                                pad.press_button(button=btn_enum)
                            else:
                                pad.release_button(button=btn_enum)
                    current_button_mask = mask

                pad.update()

            elif cmd == "J":
                # J <stick_id> <x> <y>
                stick_id = int(parts[1])
                x = clamp(int(parts[2]), -32768, 32767)
                y = clamp(int(parts[3]), -32768, 32767)
                if stick_id == 0:
                    pad.left_joystick(x_value=x, y_value=y)
                elif stick_id == 1:
                    pad.right_joystick(x_value=x, y_value=y)
                pad.update()

            elif cmd == "B":
                # B <button_bit> <action>
                bit = int(parts[1])
                action = int(parts[2])
                btn_enum = BUTTON_MAP.get(bit)
                if btn_enum:
                    if action == 1:
                        pad.press_button(button=btn_enum)
                        current_button_mask |= bit
                    else:
                        pad.release_button(button=btn_enum)
                        current_button_mask &= ~bit
                    pad.update()

            elif cmd == "T":
                # T <trigger_id> <val>
                trigger_id = int(parts[1])
                val = clamp(int(parts[2]), 0, 255)
                if trigger_id == 0:
                    pad.left_trigger(value=val)
                elif trigger_id == 1:
                    pad.right_trigger(value=val)
                pad.update()

            elif cmd == "R":
                # Reset all to neutral
                pad.reset()
                pad.update()
                current_button_mask = 0

            elif cmd == "Q":
                cleanup_and_exit()

        except Exception as e:
            sys.stderr.write(f"[GamepadBridge] Error executing '{line}': {e}\n")
            sys.stderr.flush()

if __name__ == "__main__":
    main()
