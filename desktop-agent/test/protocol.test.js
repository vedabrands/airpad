import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  PROTOCOL,
  OPCODES,
  PAIR_STATUS,
  GYRO_MODES,
  MOUSE_BUTTON_BITS,
  MOUSE_ACTIONS,
  KEY_ACTIONS,
  KEY_CODES,
  GAMEPAD_BUTTON_BITS,
} from '../src/protocol/constants.js';
import {
  encodeFrame,
  decodePacket,
  createPacket,
  encodeHeartbeat,
  parseHeartbeat,
  encodeHeartbeatAck,
  parseHeartbeatAck,
  encodeDiscoveryBeacon,
  parseDiscoveryBeacon,
  encodePairRequest,
  parsePairRequest,
  encodePairResponse,
  parsePairResponse,
  encodeDisconnect,
  parseDisconnect,
  encodeModeSwitch,
  parseModeSwitch,
  encodeMouseDelta,
  parseMouseDelta,
  encodeMouseButton,
  parseMouseButton,
  encodeMouseScroll,
  parseMouseScroll,
  encodeMouseCombo,
  parseMouseCombo,
  encodeKeyboardKey,
  parseKeyboardKey,
  encodeGamepadState,
  parseGamepadState,
  encodeGamepadStick,
  parseGamepadStick,
  encodeGamepadButton,
  parseGamepadButton,
  encodeGamepadTrigger,
  parseGamepadTrigger,
  encodeRawImu,
  parseRawImu,
  encodeGyroCalibrate,
  parseGyroCalibrate,
  decodePayloadByOpcode,
} from '../src/protocol/packet.js';

describe('Binary Protocol Frame Encoding & Decoding', () => {
  it('should encode and decode a standard frame header', () => {
    const payload = Buffer.from([0x01, 0x02, 0x03, 0x04]);
    const frame = encodeFrame({
      opcode: OPCODES.MOUSE_DELTA,
      flags: 0x03,
      sequence: 12345,
      sessionToken: 0xDEADBEEF,
      payload,
    });

    assert.equal(frame.length, PROTOCOL.HEADER_SIZE + payload.length);

    const decoded = decodePacket(frame);
    assert.ok(decoded);
    assert.equal(decoded.magic, PROTOCOL.MAGIC_BYTES);
    assert.equal(decoded.version, PROTOCOL.VERSION);
    assert.equal(decoded.flags, 0x03);
    assert.equal(decoded.sequence, 12345);
    assert.equal(decoded.sessionToken, 0xDEADBEEF);
    assert.equal(decoded.opcode, OPCODES.MOUSE_DELTA);
    assert.equal(decoded.opcodeName, 'MOUSE_DELTA');
    assert.equal(decoded.payloadLength, 4);
    assert.deepEqual(decoded.payload, payload);
  });

  it('should reject invalid magic bytes and truncated packets', () => {
    assert.equal(decodePacket(null), null);
    assert.equal(decodePacket(Buffer.alloc(10)), null); // Less than 16 bytes

    const badMagic = Buffer.alloc(16);
    badMagic.writeUInt16BE(0x1234, 0); // Invalid magic
    assert.equal(decodePacket(badMagic), null);

    const badVersion = Buffer.alloc(16);
    badVersion.writeUInt16BE(PROTOCOL.MAGIC_BYTES, 0);
    badVersion.writeUInt8(0x99, 2); // Invalid version
    assert.equal(decodePacket(badVersion), null);

    const truncated = Buffer.alloc(16);
    truncated.writeUInt16BE(PROTOCOL.MAGIC_BYTES, 0);
    truncated.writeUInt8(PROTOCOL.VERSION, 2);
    truncated.writeUInt16BE(100, 14); // Payload says 100 bytes, but buffer has 0 payload
    assert.equal(decodePacket(truncated), null);
  });
});

describe('Opcode Payloads', () => {
  it('Heartbeat & HeartbeatAck', () => {
    const now = 1718000000000;
    const hbPayload = encodeHeartbeat(now);
    const parsedHb = parseHeartbeat(hbPayload);
    assert.equal(parsedHb.clientTimestamp, now);

    const ackPayload = encodeHeartbeatAck(now, now + 15);
    const parsedAck = parseHeartbeatAck(ackPayload);
    assert.equal(parsedAck.clientTimestamp, now);
    assert.equal(parsedAck.serverTimestamp, now + 15);
  });

  it('Discovery Beacon', () => {
    const beacon = encodeDiscoveryBeacon(54321, 1, true, 'Gaming-PC');
    const parsed = parseDiscoveryBeacon(beacon);
    assert.equal(parsed.controlPort, 54321);
    assert.equal(parsed.osType, 1);
    assert.equal(parsed.requirePin, true);
    assert.equal(parsed.serverName, 'Gaming-PC');
  });

  it('Pair Request & Pair Response', () => {
    const pairReq = encodePairRequest(842915, '12345678123456781234567812345678', 'Pixel-8');
    const parsedReq = parsePairRequest(pairReq);
    assert.equal(parsedReq.pinCode, 842915);
    assert.equal(parsedReq.clientUid, '12345678123456781234567812345678');
    assert.equal(parsedReq.deviceName, 'Pixel-8');

    const pairResp = encodePairResponse(PAIR_STATUS.SUCCESS, 0x12345678, 120);
    const parsedResp = parsePairResponse(pairResp);
    assert.equal(parsedResp.statusCode, PAIR_STATUS.SUCCESS);
    assert.equal(parsedResp.sessionToken, 0x12345678);
    assert.equal(parsedResp.targetRateHz, 120);
  });

  it('Disconnect', () => {
    const disc = encodeDisconnect(2);
    const parsed = parseDisconnect(disc);
    assert.equal(parsed.reasonCode, 2);
  });

  it('Mode Switch', () => {
    const modePayload = encodeModeSwitch({
      gyroMode: GYRO_MODES.GYRO_TO_MOUSE,
      invertX: false,
      invertY: true,
      ratchetActive: true,
      resetBias: false,
      sensitivityX: 2.5,
      sensitivityY: 1.8,
      deadzone: 0.03,
      smoothingFactor: 0.35,
    });
    const parsed = parseModeSwitch(modePayload);
    assert.equal(parsed.gyroMode, GYRO_MODES.GYRO_TO_MOUSE);
    assert.equal(parsed.invertX, false);
    assert.equal(parsed.invertY, true);
    assert.equal(parsed.ratchetActive, true);
    assert.equal(parsed.resetBias, false);
    assert.ok(Math.abs(parsed.sensitivityX - 2.5) < 0.001);
    assert.ok(Math.abs(parsed.sensitivityY - 1.8) < 0.001);
    assert.ok(Math.abs(parsed.deadzone - 0.03) < 0.001);
    assert.ok(Math.abs(parsed.smoothingFactor - 0.35) < 0.001);
  });

  it('Mouse Delta, Button, Scroll, Combo', () => {
    const delta = encodeMouseDelta(15, -20);
    const parsedDelta = parseMouseDelta(delta);
    assert.equal(parsedDelta.rawDx, 15);
    assert.equal(parsedDelta.rawDy, -20);

    const btn = encodeMouseButton(MOUSE_BUTTON_BITS.LEFT, MOUSE_ACTIONS.BUTTON_DOWN);
    const parsedBtn = parseMouseButton(btn);
    assert.equal(parsedBtn.buttonMask, MOUSE_BUTTON_BITS.LEFT);
    assert.equal(parsedBtn.action, MOUSE_ACTIONS.BUTTON_DOWN);

    const scroll = encodeMouseScroll(120, -50);
    const parsedScroll = parseMouseScroll(scroll);
    assert.equal(parsedScroll.scrollY, 120);
    assert.equal(parsedScroll.scrollX, -50);

    const combo = encodeMouseCombo({
      dx: 5,
      dy: -10,
      buttonState: 0x01,
      scrollX: 0,
      scrollY: 1,
    });
    const parsedCombo = parseMouseCombo(combo);
    assert.equal(parsedCombo.dx, 5);
    assert.equal(parsedCombo.dy, -10);
    assert.equal(parsedCombo.buttonState, 0x01);
    assert.equal(parsedCombo.scrollY, 1);
    assert.equal(parsedCombo.scrollX, 0);
  });

  it('Keyboard Key', () => {
    const key = encodeKeyboardKey(KEY_CODES.SPACE, KEY_ACTIONS.KEY_DOWN, 0x01);
    const parsedKey = parseKeyboardKey(key);
    assert.equal(parsedKey.keyCode, KEY_CODES.SPACE);
    assert.equal(parsedKey.action, KEY_ACTIONS.KEY_DOWN);
    assert.equal(parsedKey.modifiers, 0x01);
  });

  it('Gamepad State, Stick, Button, Trigger', () => {
    const state = encodeGamepadState({
      buttonMask: GAMEPAD_BUTTON_BITS.A | GAMEPAD_BUTTON_BITS.X,
      leftX: 16000,
      leftY: -16000,
      rightX: 32000,
      rightY: 0,
      leftTrigger: 200,
      rightTrigger: 50,
    });
    const parsedState = parseGamepadState(state);
    assert.equal(parsedState.buttonMask, GAMEPAD_BUTTON_BITS.A | GAMEPAD_BUTTON_BITS.X);
    assert.equal(parsedState.leftX, 16000);
    assert.equal(parsedState.leftY, -16000);
    assert.equal(parsedState.rightX, 32000);
    assert.equal(parsedState.rightY, 0);
    assert.equal(parsedState.leftTrigger, 200);
    assert.equal(parsedState.rightTrigger, 50);

    const stick = encodeGamepadStick(0, 1000, -2000);
    const parsedStick = parseGamepadStick(stick);
    assert.equal(parsedStick.stickId, 0);
    assert.equal(parsedStick.posX, 1000);
    assert.equal(parsedStick.posY, -2000);

    const btn = encodeGamepadButton(GAMEPAD_BUTTON_BITS.Y, 1);
    const parsedBtn = parseGamepadButton(btn);
    assert.equal(parsedBtn.buttonBit, GAMEPAD_BUTTON_BITS.Y);
    assert.equal(parsedBtn.action, 1);

    const trigger = encodeGamepadTrigger(1, 255);
    const parsedTrigger = parseGamepadTrigger(trigger);
    assert.equal(parsedTrigger.triggerId, 1);
    assert.equal(parsedTrigger.value, 255);
  });

  it('Raw IMU Data & Gyro Calibrate', () => {
    const imu = encodeRawImu({
      gyroX: 0.15,
      gyroY: -0.25,
      gyroZ: 1.5,
      accelX: 0.0,
      accelY: 9.8,
      accelZ: 0.1,
    });
    const parsedImu = parseRawImu(imu);
    assert.ok(Math.abs(parsedImu.gyroX - 0.15) < 0.0001);
    assert.ok(Math.abs(parsedImu.gyroY - (-0.25)) < 0.0001);
    assert.ok(Math.abs(parsedImu.gyroZ - 1.5) < 0.0001);
    assert.ok(Math.abs(parsedImu.accelY - 9.8) < 0.0001);

    const cal = encodeGyroCalibrate(1);
    const parsedCal = parseGyroCalibrate(cal);
    assert.equal(parsedCal.command, 1);
  });

  it('Universal Dispatcher (decodePayloadByOpcode)', () => {
    const deltaPayload = encodeMouseDelta(10, 20);
    const decoded = decodePayloadByOpcode(OPCODES.MOUSE_DELTA, deltaPayload);
    assert.equal(decoded.rawDx, 10);
    assert.equal(decoded.rawDy, 20);
  });
});
