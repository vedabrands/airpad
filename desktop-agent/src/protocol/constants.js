/**
 * Protocol constants for the AirPad binary wire format.
 */

export const PROTOCOL = {
  MAGIC_BYTES: 0x4150, // 'AP' — AirPad
  VERSION: 0x01,
  HEADER_SIZE: 16,
};

export const OPCODES = {
  HEARTBEAT: 0x01,
  HEARTBEAT_ACK: 0x02,
  DISCOVERY_BEACON: 0x05,
  PAIR_REQUEST: 0x06,
  PAIR_RESPONSE: 0x07,
  DISCONNECT: 0x08,
  MODE_SWITCH: 0x10,
  MOUSE_DELTA: 0x20,
  MOUSE_BUTTON: 0x21,
  MOUSE_SCROLL: 0x22,
  MOUSE_COMBO: 0x23,
  KEYBOARD_KEY: 0x24,
  GAMEPAD_STATE: 0x30,
  GAMEPAD_STICK: 0x31,
  GAMEPAD_BUTTON: 0x32,
  GAMEPAD_TRIGGER: 0x33,
  RAW_IMU_DATA: 0x40,
  GYRO_CALIBRATE: 0x41,
  ACTIVE_WINDOW_INFO: 0x50,
  GAME_PROFILE_SYNC: 0x51,
  PROFILE_CHANGE: 0x52,
  MACRO_TRIGGER: 0x53,
};

/**
 * Reverse lookup table mapping opcode byte values to their human-readable names.
 * Generated from OPCODES so it stays in sync with the canonical definitions.
 */
export const OPCODE_NAMES = Object.fromEntries(
  Object.entries(OPCODES).map(([name, value]) => [value, name])
);

/**
 * Pairing status codes returned by the server in PAIR_RESPONSE.
 */
export const PAIR_STATUS = {
  SUCCESS: 0x00,
  ERR_INVALID_PIN: 0x01,
  ERR_DEVICE_BLACKLISTED: 0x02,
  ERR_SERVER_BUSY: 0x03,
  ERR_INTERNAL: 0xFF,
};

export const PAIR_STATUS_NAMES = Object.fromEntries(
  Object.entries(PAIR_STATUS).map(([name, value]) => [value, name])
);

/**
 * Gyro processing modes selected by MODE_SWITCH.
 */
export const GYRO_MODES = {
  OFF: 0x00,
  GYRO_TO_STICK: 0x01,
  GYRO_TO_MOUSE: 0x02,
};

export const GYRO_MODE_NAMES = Object.fromEntries(
  Object.entries(GYRO_MODES).map(([name, value]) => [value, name])
);

/**
 * Mouse button bitmask bits used in MOUSE_BUTTON and MOUSE_COMBO payloads.
 */
export const MOUSE_BUTTON_BITS = {
  LEFT: 0x01,
  RIGHT: 0x02,
  MIDDLE: 0x04,
  BACK: 0x08,
  FORWARD: 0x10,
};

/**
 * Mouse button action codes.
 */
export const MOUSE_ACTIONS = {
  BUTTON_UP: 0x00,
  BUTTON_DOWN: 0x01,
  BUTTON_TAP: 0x02,
};

/**
 * Standard USB HID-like key codes used by KEYBOARD_KEY opcode.
 */
export const KEY_CODES = {
  RETURN: 0x0D,
  ENTER: 0x9C,
  ESCAPE: 0x1B,
  BACKSPACE: 0x08,
  TAB: 0x09,
  SPACE: 0x20,
  UP: 0x48,
  DOWN: 0x50,
  LEFT: 0x4B,
  RIGHT: 0x4D,
  KEY_W: 0x57,
  KEY_A: 0x41,
  KEY_S: 0x53,
  KEY_D: 0x44,
  KEY_E: 0x45,
  KEY_Q: 0x51,
  KEY_R: 0x52,
  KEY_F: 0x46,
  LEFT_CTRL: 0x01,
  LEFT_SHIFT: 0x02,
  LEFT_ALT: 0x04,
  LEFT_SUPER: 0x08,
  VOLUME_UP: 0x90,
  VOLUME_DOWN: 0x91,
  MEDIA_PLAY_PAUSE: 0xB3,
  MEDIA_NEXT: 0xB5,
  MEDIA_PREV: 0xB6,
};

/**
 * Keyboard key action codes.
 */
export const KEY_ACTIONS = {
  KEY_UP: 0x00,
  KEY_DOWN: 0x01,
  KEY_TAP: 0x02,
};

export const KEY_ACTION_NAMES = Object.fromEntries(
  Object.entries(KEY_ACTIONS).map(([name, value]) => [value, name])
);

export const KEY_NAMES = Object.fromEntries(
  Object.entries(KEY_CODES).map(([name, value]) => [value, name])
);

/**
 * Gamepad button bitmask bits used in GAMEPAD_STATE.
 */
export const GAMEPAD_BUTTON_BITS = {
  A: 0x0001,
  B: 0x0002,
  X: 0x0004,
  Y: 0x0008,
  LB: 0x0010,
  RB: 0x0020,
  LS: 0x0040,
  RS: 0x0080,
  START: 0x0100,
  BACK: 0x0200,
  DPAD_UP: 0x0400,
  DPAD_DOWN: 0x0800,
  DPAD_LEFT: 0x1000,
  DPAD_RIGHT: 0x2000,
};