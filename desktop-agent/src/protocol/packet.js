import { PROTOCOL, OPCODES, OPCODE_NAMES } from './constants.js';

/**
 * Encodes a generic AirPad binary packet frame with the 16-byte fixed header.
 *
 * Header Layout (16 Bytes, Big-Endian):
 *  0..1  : Magic (0x4150)
 *  2     : ProtoVer (0x01)
 *  3     : Flags (8-bit)
 *  4..7  : Sequence Number (uint32)
 *  8..11 : Session Token (uint32)
 *  12    : Opcode (uint8)
 *  13    : Reserved (uint8 = 0x00)
 *  14..15: Payload Length (uint16)
 *
 * @param {object} options
 * @param {number} options.opcode
 * @param {number} [options.flags=0]
 * @param {number} [options.sequence=0]
 * @param {number} [options.sessionToken=0]
 * @param {Buffer} [options.payload=Buffer.alloc(0)]
 * @returns {Buffer}
 */
export function encodeFrame({
  opcode,
  flags = 0,
  sequence = 0,
  sessionToken = 0,
  payload = Buffer.alloc(0),
}) {
  const header = Buffer.alloc(PROTOCOL.HEADER_SIZE);
  header.writeUInt16BE(PROTOCOL.MAGIC_BYTES, 0);
  header.writeUInt8(PROTOCOL.VERSION, 2);
  header.writeUInt8(flags, 3);
  header.writeUInt32BE(sequence >>> 0, 4);
  header.writeUInt32BE(sessionToken >>> 0, 8);
  header.writeUInt8(opcode, 12);
  header.writeUInt8(0x00, 13);
  header.writeUInt16BE(payload.length, 14);
  return Buffer.concat([header, payload]);
}

/**
 * Helper to construct a packet from individual parameters.
 */
export function createPacket(opcode, sessionToken = 0, sequence = 0, flags = 0, payload = Buffer.alloc(0)) {
  return encodeFrame({ opcode, flags, sequence, sessionToken, payload });
}

/**
 * Decodes an incoming binary buffer into Header fields and Payload buffer.
 * Performs validation on magic bytes and protocol version.
 *
 * @param {Buffer} buffer
 * @returns {object|null} Decoded packet header and payload slice, or null if invalid
 */
export function decodePacket(buffer) {
  if (!buffer || buffer.length < PROTOCOL.HEADER_SIZE) {
    return null;
  }

  const magic = buffer.readUInt16BE(0);
  if (magic !== PROTOCOL.MAGIC_BYTES) {
    return null;
  }

  const version = buffer.readUInt8(2);
  if (version !== PROTOCOL.VERSION) {
    return null;
  }

  const flags = buffer.readUInt8(3);
  const sequence = buffer.readUInt32BE(4);
  const sessionToken = buffer.readUInt32BE(8);
  const opcode = buffer.readUInt8(12);
  const payloadLength = buffer.readUInt16BE(14);

  // Check if buffer contains the complete payload
  if (buffer.length < PROTOCOL.HEADER_SIZE + payloadLength) {
    return null;
  }

  const payload = buffer.subarray(PROTOCOL.HEADER_SIZE, PROTOCOL.HEADER_SIZE + payloadLength);
  const opcodeName = OPCODE_NAMES[opcode] || `UNKNOWN_0x${opcode.toString(16).padStart(2, '0')}`;

  return {
    magic,
    version,
    flags,
    sequence,
    sessionToken,
    opcode,
    opcodeName,
    payloadLength,
    payload,
  };
}

// ---------------------------------------------------------------------------
// Individual Opcode Decoders & Encoders
// ---------------------------------------------------------------------------

// Opcode 0x01: HEARTBEAT (Client -> Server)
export function parseHeartbeat(payload) {
  if (payload.length < 8) return { clientTimestamp: 0 };
  const clientTimestamp = Number(payload.readBigUInt64BE(0));
  return { clientTimestamp };
}

export function encodeHeartbeat(clientTimestamp = Date.now()) {
  const buf = Buffer.alloc(8);
  buf.writeBigUInt64BE(BigInt(clientTimestamp), 0);
  return buf;
}

// Opcode 0x02: HEARTBEAT_ACK (Server -> Client)
export function parseHeartbeatAck(payload) {
  if (payload.length < 16) {
    return { clientTimestamp: 0, serverTimestamp: 0, rttMs: 0 };
  }
  const clientTimestamp = Number(payload.readBigUInt64BE(0));
  const serverTimestamp = Number(payload.readBigUInt64BE(8));
  const rttMs = Date.now() - clientTimestamp;
  return { clientTimestamp, serverTimestamp, rttMs };
}

export function encodeHeartbeatAck(clientTimestamp, serverTimestamp = Date.now()) {
  const buf = Buffer.alloc(16);
  buf.writeBigUInt64BE(BigInt(clientTimestamp), 0);
  buf.writeBigUInt64BE(BigInt(serverTimestamp), 8);
  return buf;
}

// Opcode 0x05: DISCOVERY_BEACON (Server -> Broadcast)
export function parseDiscoveryBeacon(payload) {
  if (payload.length < 5) return null;
  const controlPort = payload.readUInt16BE(0);
  const osType = payload.readUInt8(2);
  const requirePin = payload.readUInt8(3) === 1;
  const nameLen = payload.readUInt8(4);
  const serverName = payload.subarray(5, 5 + nameLen).toString('utf8');
  return { controlPort, osType, requirePin, serverName };
}

export function encodeDiscoveryBeacon(port, osType, requirePin, serverName) {
  const nameBuf = Buffer.from(serverName, 'utf8');
  const buf = Buffer.alloc(4 + 1 + nameBuf.length);
  buf.writeUInt16BE(port, 0);
  buf.writeUInt8(osType, 2);
  buf.writeUInt8(requirePin ? 1 : 0, 3);
  buf.writeUInt8(nameBuf.length, 4);
  nameBuf.copy(buf, 5);
  return buf;
}

// Opcode 0x06: PAIR_REQUEST (Client -> Server)
export function parsePairRequest(payload) {
  if (payload.length < 21) return null;
  const pinCode = payload.readUInt32BE(0);
  const clientUid = payload.subarray(4, 20).toString('hex');
  const nameLen = payload.readUInt8(20);
  const deviceName = payload.subarray(21, 21 + nameLen).toString('utf8');
  return { pinCode, clientUid, deviceName };
}

export function encodePairRequest(pinCode, clientUidHex = '00'.repeat(16), deviceName = 'AirPad Client') {
  const nameBuf = Buffer.from(deviceName, 'utf8');
  const buf = Buffer.alloc(4 + 16 + 1 + nameBuf.length);
  buf.writeUInt32BE(pinCode >>> 0, 0);
  const uidBuf = Buffer.from(clientUidHex.replace(/-/g, ''), 'hex');
  if (uidBuf.length === 16) {
    uidBuf.copy(buf, 4);
  }
  buf.writeUInt8(nameBuf.length, 20);
  nameBuf.copy(buf, 21);
  return buf;
}

// Opcode 0x07: PAIR_RESPONSE (Server -> Client)
export function parsePairResponse(payload) {
  if (payload.length < 10) return null;
  const statusCode = payload.readUInt8(0);
  const sessionToken = payload.readUInt32BE(2);
  const targetRateHz = payload.readUInt8(9);
  return { statusCode, sessionToken, targetRateHz };
}

export function encodePairResponse(statusCode, sessionToken, targetRateHz = 60) {
  const buf = Buffer.alloc(10);
  buf.writeUInt8(statusCode, 0);
  buf.writeUInt8(0x00, 1);
  buf.writeUInt32BE(sessionToken >>> 0, 2);
  buf.writeUInt32BE(0, 6);
  buf.writeUInt8(targetRateHz, 9);
  return buf;
}

// Opcode 0x08: DISCONNECT (Client <-> Server)
export function parseDisconnect(payload) {
  const reasonCode = payload.length >= 1 ? payload.readUInt8(0) : 0;
  return { reasonCode };
}

export function encodeDisconnect(reasonCode = 0) {
  const buf = Buffer.alloc(1);
  buf.writeUInt8(reasonCode, 0);
  return buf;
}

// Opcode 0x10: MODE_SWITCH (Client -> Server)
export function parseModeSwitch(payload) {
  if (payload.length < 20) return null;
  const gyroMode = payload.readUInt8(0);
  const configFlags = payload.readUInt8(1);
  const sensitivityX = payload.readFloatBE(4);
  const sensitivityY = payload.readFloatBE(8);
  const deadzone = payload.readFloatBE(12);
  const smoothingFactor = payload.readFloatBE(16);

  return {
    gyroMode,
    invertX: (configFlags & 0x01) !== 0,
    invertY: (configFlags & 0x02) !== 0,
    ratchetActive: (configFlags & 0x04) !== 0,
    resetBias: (configFlags & 0x08) !== 0,
    sensitivityX,
    sensitivityY,
    deadzone,
    smoothingFactor,
  };
}

export function encodeModeSwitch({
  gyroMode = 0,
  invertX = false,
  invertY = false,
  ratchetActive = false,
  resetBias = false,
  sensitivityX = 1.0,
  sensitivityY = 1.0,
  deadzone = 0.02,
  smoothingFactor = 0.25,
}) {
  const buf = Buffer.alloc(20);
  buf.writeUInt8(gyroMode, 0);
  let flags = 0;
  if (invertX) flags |= 0x01;
  if (invertY) flags |= 0x02;
  if (ratchetActive) flags |= 0x04;
  if (resetBias) flags |= 0x08;
  buf.writeUInt8(flags, 1);
  buf.writeUInt16BE(0, 2); // Reserved
  buf.writeFloatBE(sensitivityX, 4);
  buf.writeFloatBE(sensitivityY, 8);
  buf.writeFloatBE(deadzone, 12);
  buf.writeFloatBE(smoothingFactor, 16);
  return buf;
}

// Opcode 0x20: MOUSE_DELTA (Client -> Server)
export function parseMouseDelta(payload) {
  if (payload.length < 4) return null;
  const dx = payload.readInt16BE(0);
  const dy = payload.readInt16BE(2);
  const subX = payload.length >= 6 ? payload.readUInt8(4) / 255.0 : 0;
  const subY = payload.length >= 6 ? payload.readUInt8(5) / 255.0 : 0;
  return {
    dx: dx >= 0 ? dx + subX : dx - subX,
    dy: dy >= 0 ? dy + subY : dy - subY,
    rawDx: dx,
    rawDy: dy,
  };
}

export function encodeMouseDelta(dx, dy) {
  const buf = Buffer.alloc(4);
  buf.writeInt16BE(Math.trunc(dx), 0);
  buf.writeInt16BE(Math.trunc(dy), 2);
  return buf;
}

// Opcode 0x21: MOUSE_BUTTON (Client -> Server)
export function parseMouseButton(payload) {
  if (payload.length < 2) return null;
  const buttonMask = payload.readUInt8(0);
  const action = payload.readUInt8(1);
  return { buttonMask, action };
}

export function encodeMouseButton(buttonMask, action) {
  const buf = Buffer.alloc(2);
  buf.writeUInt8(buttonMask, 0);
  buf.writeUInt8(action, 1);
  return buf;
}

// Opcode 0x22: MOUSE_SCROLL (Client -> Server)
export function parseMouseScroll(payload) {
  if (payload.length < 4) return null;
  const scrollY = payload.readInt16BE(0);
  const scrollX = payload.readInt16BE(2);
  return { scrollX, scrollY };
}

export function encodeMouseScroll(scrollY = 0, scrollX = 0) {
  const buf = Buffer.alloc(4);
  buf.writeInt16BE(scrollY, 0);
  buf.writeInt16BE(scrollX, 2);
  return buf;
}

// Opcode 0x23: MOUSE_COMBO (Client -> Server)
// Standard 7-byte format: [dx:int16][dy:int16][buttonState:u8][scrollY:int8][scrollX:int8]
// Extended batched format (>7 bytes): adds count byte at offset 7 followed by delta entries
export function parseMouseCombo(payload) {
  if (payload.length < 7) return null;

  const dx = payload.readInt16BE(0);
  const dy = payload.readInt16BE(2);
  const buttonState = payload.readUInt8(4);
  const scrollY = payload.readInt8(5);
  const scrollX = payload.readInt8(6);

  let deltas = null;
  if (payload.length >= 8) {
    const count = payload.readUInt8(7);
    if (count > 0 && count <= 31 && payload.length >= 8 + count * 4) {
      deltas = [];
      let offset = 8;
      for (let i = 0; i < count; i++) {
        const deltaDx = payload.readInt16BE(offset);
        const deltaDy = payload.readInt16BE(offset + 2);
        deltas.push({ dx: deltaDx, dy: deltaDy });
        offset += 4;
      }
    }
  }

  return {
    dx,
    dy,
    buttonState,
    scrollX,
    scrollY,
    deltas // Array of batched deltas if present
  };
}

export function encodeMouseCombo(dxOrObj, dy = 0, buttonState = 0, scrollX = 0, scrollY = 0) {
  let dx = dxOrObj;
  if (typeof dxOrObj === 'object' && dxOrObj !== null) {
    dx = dxOrObj.dx || 0;
    dy = dxOrObj.dy || 0;
    buttonState = dxOrObj.buttonState || 0;
    scrollX = dxOrObj.scrollX || 0;
    scrollY = dxOrObj.scrollY || 0;
  }
  const buf = Buffer.alloc(7);
  buf.writeInt16BE(dx, 0);
  buf.writeInt16BE(dy, 2);
  buf.writeUInt8(buttonState, 4);
  buf.writeInt8(scrollY, 5);
  buf.writeInt8(scrollX, 6);
  return buf;
}

/**
 * Parses batched mouse deltas from MOUSE_COMBO packet.
 * Returns array of {dx, dy} deltas or null if not batched.
 */
export function parseBatchedMouseDeltas(payload) {
  if (payload.length < 9) return null;
  const count = payload.readUInt8(9);
  if (count <= 0 || count > 31 || payload.length < 10 + count * 4) return null;

  const deltas = [];
  let offset = 10;
  for (let i = 0; i < count; i++) {
    const dx = payload.readInt16BE(offset);
    const dy = payload.readInt16BE(offset + 2);
    deltas.push({ dx, dy });
    offset += 4;
  }
  return deltas;
}

// Opcode 0x24: KEYBOARD_KEY (Client -> Server)
export function parseKeyboardKey(payload) {
  if (payload.length < 3) return null;
  const keyCode = payload.readUInt16BE(0);
  const action = payload.readUInt8(2);
  const modifiers = payload.length >= 4 ? payload.readUInt8(3) : 0;
  return { keyCode, action, modifiers };
}

export function encodeKeyboardKey(keyCode, action = 1, modifiers = 0) {
  const buf = Buffer.alloc(4);
  buf.writeUInt16BE(keyCode, 0);
  buf.writeUInt8(action, 2);
  buf.writeUInt8(modifiers, 3);
  return buf;
}

// Opcode 0x30: GAMEPAD_STATE (Client -> Server)
export function parseGamepadState(payload) {
  if (payload.length < 14) return null;
  const buttonMask = payload.readUInt32BE(0);
  const leftX = payload.readInt16BE(4);
  const leftY = payload.readInt16BE(6);
  const rightX = payload.readInt16BE(8);
  const rightY = payload.readInt16BE(10);
  const leftTrigger = payload.readUInt8(12);
  const rightTrigger = payload.readUInt8(13);
  return { buttonMask, leftX, leftY, rightX, rightY, leftTrigger, rightTrigger };
}

export function encodeGamepadState({
  buttonMask = 0,
  leftX = 0,
  leftY = 0,
  rightX = 0,
  rightY = 0,
  leftTrigger = 0,
  rightTrigger = 0,
}) {
  const buf = Buffer.alloc(14);
  buf.writeUInt32BE(buttonMask >>> 0, 0);
  buf.writeInt16BE(leftX, 4);
  buf.writeInt16BE(leftY, 6);
  buf.writeInt16BE(rightX, 8);
  buf.writeInt16BE(rightY, 10);
  buf.writeUInt8(leftTrigger, 12);
  buf.writeUInt8(rightTrigger, 13);
  return buf;
}

// Opcode 0x31: GAMEPAD_STICK (Client -> Server)
export function parseGamepadStick(payload) {
  if (payload.length < 6) return null;
  const stickId = payload.readUInt8(0);
  const posX = payload.readInt16BE(2);
  const posY = payload.readInt16BE(4);
  return { stickId, posX, posY };
}

export function encodeGamepadStick(stickId, posX, posY) {
  const buf = Buffer.alloc(6);
  buf.writeUInt8(stickId, 0);
  buf.writeUInt8(0x00, 1);
  buf.writeInt16BE(posX, 2);
  buf.writeInt16BE(posY, 4);
  return buf;
}

// Opcode 0x32: GAMEPAD_BUTTON (Client -> Server)
export function parseGamepadButton(payload) {
  if (payload.length < 5) return null;
  const buttonBit = payload.readUInt32BE(0);
  const action = payload.readUInt8(4);
  return { buttonBit, action };
}

export function encodeGamepadButton(buttonBit, action) {
  const buf = Buffer.alloc(5);
  buf.writeUInt32BE(buttonBit >>> 0, 0);
  buf.writeUInt8(action, 4);
  return buf;
}

// Opcode 0x33: GAMEPAD_TRIGGER (Client -> Server)
export function parseGamepadTrigger(payload) {
  if (payload.length < 2) return null;
  const triggerId = payload.readUInt8(0);
  const value = payload.readUInt8(1);
  return { triggerId, value };
}

export function encodeGamepadTrigger(triggerId, value) {
  const buf = Buffer.alloc(2);
  buf.writeUInt8(triggerId, 0);
  buf.writeUInt8(value, 1);
  return buf;
}

// Opcode 0x40: RAW_IMU_DATA (Client -> Server)
export function parseRawImu(payload) {
  if (payload.length < 24) return null;
  const gyroX = payload.readFloatBE(0);
  const gyroY = payload.readFloatBE(4);
  const gyroZ = payload.readFloatBE(8);
  const accelX = payload.readFloatBE(12);
  const accelY = payload.readFloatBE(16);
  const accelZ = payload.readFloatBE(20);
  return { gyroX, gyroY, gyroZ, accelX, accelY, accelZ };
}

export function encodeRawImu({
  gyroX = 0,
  gyroY = 0,
  gyroZ = 0,
  accelX = 0,
  accelY = 0,
  accelZ = 0,
}) {
  const buf = Buffer.alloc(24);
  buf.writeFloatBE(gyroX, 0);
  buf.writeFloatBE(gyroY, 4);
  buf.writeFloatBE(gyroZ, 8);
  buf.writeFloatBE(accelX, 12);
  buf.writeFloatBE(accelY, 16);
  buf.writeFloatBE(accelZ, 20);
  return buf;
}

// Opcode 0x41: GYRO_CALIBRATE (Client -> Server)
export function parseGyroCalibrate(payload) {
  if (payload.length < 1) return { command: 0 };
  const command = payload.readUInt8(0);
  return { command };
}

export function encodeGyroCalibrate(command = 0) {
  const buf = Buffer.alloc(1);
  buf.writeUInt8(command, 0);
  return buf;
}

// Opcode 0x50: ACTIVE_WINDOW_INFO (Server -> Client)
export function parseActiveWindowInfo(payload) {
  if (payload.length < 2) return null;
  const processNameLen = payload.readUInt8(0);
  if (payload.length < 2 + processNameLen) return null;
  const processName = payload.subarray(1, 1 + processNameLen).toString('utf8');

  let offset = 1 + processNameLen;
  if (payload.length < offset + 1) return null;
  const windowTitleLen = payload.readUInt8(offset);
  if (payload.length < offset + 1 + windowTitleLen) return null;
  offset += 1;
  const windowTitle = payload.subarray(offset, offset + windowTitleLen).toString('utf8');

  return { processName, windowTitle };
}

export function encodeActiveWindowInfo(processName, windowTitle) {
  const processBuf = Buffer.from(processName, 'utf8');
  const windowBuf = Buffer.from(windowTitle, 'utf8');
  const buf = Buffer.alloc(1 + processBuf.length + 1 + windowBuf.length);
  buf.writeUInt8(processBuf.length, 0);
  processBuf.copy(buf, 1);
  buf.writeUInt8(windowBuf.length, 1 + processBuf.length);
  windowBuf.copy(buf, 1 + processBuf.length + 1);
  return buf;
}

// Opcode 0x51: GAME_PROFILE_SYNC (Server -> Client)
export function parseGameProfileSync(payload) {
  if (payload.length < 1) return null;
  const profileIdLen = payload.readUInt8(0);
  if (payload.length < 1 + profileIdLen) return null;
  const profileId = payload.subarray(1, 1 + profileIdLen).toString('utf8');
  return { profileId };
}

export function encodeGameProfileSync(profileId) {
  const idBuf = Buffer.from(profileId, 'utf8');
  const buf = Buffer.alloc(1 + idBuf.length);
  buf.writeUInt8(idBuf.length, 0);
  idBuf.copy(buf, 1);
  return buf;
}

// Opcode 0x52: PROFILE_CHANGE (Client -> Server)
export function parseProfileChange(payload) {
  if (payload.length < 1) return null;
  const profileIdLen = payload.readUInt8(0);
  if (payload.length < 1 + profileIdLen) return null;
  const profileId = payload.subarray(1, 1 + profileIdLen).toString('utf8');
  return { profileId };
}

export function encodeProfileChange(profileId) {
  const idBuf = Buffer.from(profileId, 'utf8');
  const buf = Buffer.alloc(1 + idBuf.length);
  buf.writeUInt8(idBuf.length, 0);
  idBuf.copy(buf, 1);
  return buf;
}

// Opcode 0x53: MACRO_TRIGGER (Client -> Server)
export function parseMacroTrigger(payload) {
  if (payload.length < 4) return null;
  const macroIdLen = payload.readUInt8(0);
  if (payload.length < 1 + macroIdLen) return null;
  const macroId = payload.subarray(1, 1 + macroIdLen).toString('utf8');

  let offset = 1 + macroIdLen;
  if (payload.length < offset + 4) return null;
  const timestamp = payload.readUInt32BE(offset);

  return { macroId, timestamp };
}

export function encodeMacroTrigger(macroId, timestamp = Date.now()) {
  const idBuf = Buffer.from(macroId, 'utf8');
  const buf = Buffer.alloc(1 + idBuf.length + 4);
  buf.writeUInt8(idBuf.length, 0);
  idBuf.copy(buf, 1);
  buf.writeUInt32BE(timestamp >>> 0, 1 + idBuf.length);
  return buf;
}

/**
 * Universal dispatcher that decodes any packet payload by its opcode.
 * @param {number} opcode
 * @param {Buffer} payload
 * @returns {object|null}
 */
export function decodePayloadByOpcode(opcode, payload) {
  switch (opcode) {
    case OPCODES.HEARTBEAT:
      return parseHeartbeat(payload);
    case OPCODES.HEARTBEAT_ACK:
      return parseHeartbeatAck(payload);
    case OPCODES.DISCOVERY_BEACON:
      return parseDiscoveryBeacon(payload);
    case OPCODES.PAIR_REQUEST:
      return parsePairRequest(payload);
    case OPCODES.PAIR_RESPONSE:
      return parsePairResponse(payload);
    case OPCODES.DISCONNECT:
      return parseDisconnect(payload);
    case OPCODES.MODE_SWITCH:
      return parseModeSwitch(payload);
    case OPCODES.MOUSE_DELTA:
      return parseMouseDelta(payload);
    case OPCODES.MOUSE_BUTTON:
      return parseMouseButton(payload);
    case OPCODES.MOUSE_SCROLL:
      return parseMouseScroll(payload);
    case OPCODES.MOUSE_COMBO:
      return parseMouseCombo(payload);
    case OPCODES.KEYBOARD_KEY:
      return parseKeyboardKey(payload);
    case OPCODES.GAMEPAD_STATE:
      return parseGamepadState(payload);
    case OPCODES.GAMEPAD_STICK:
      return parseGamepadStick(payload);
    case OPCODES.GAMEPAD_BUTTON:
      return parseGamepadButton(payload);
    case OPCODES.GAMEPAD_TRIGGER:
      return parseGamepadTrigger(payload);
    case OPCODES.RAW_IMU_DATA:
      return parseRawImu(payload);
    case OPCODES.GYRO_CALIBRATE:
      return parseGyroCalibrate(payload);
    case OPCODES.ACTIVE_WINDOW_INFO:
      return parseActiveWindowInfo(payload);
    case OPCODES.GAME_PROFILE_SYNC:
      return parseGameProfileSync(payload);
    case OPCODES.PROFILE_CHANGE:
      return parseProfileChange(payload);
    case OPCODES.MACRO_TRIGGER:
      return parseMacroTrigger(payload);
    default:
      return null;
  }
}
