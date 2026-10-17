import dgram from 'node:dgram';
import { OPCODES, PAIR_STATUS, PAIR_STATUS_NAMES, GYRO_MODES, KEY_CODES, KEY_ACTIONS } from './src/protocol/constants.js';
import {
  createPacket,
  decodePacket,
  encodePairRequest,
  parsePairResponse,
  encodeHeartbeat,
  parseHeartbeatAck,
  encodeModeSwitch,
  encodeMouseDelta,
  encodeMouseButton,
  encodeMouseScroll,
  encodeKeyboardKey,
  encodeGamepadState,
  encodeRawImu,
  encodeDisconnect,
} from './src/protocol/packet.js';

// Configuration
const TARGET_HOST = process.env.AIRPAD_HOST || '127.0.0.1';
const TARGET_PORT = parseInt(process.env.AIRPAD_PORT || '54321', 10);
const PIN_CODE = parseInt(process.argv[2] || process.env.AIRPAD_PIN || '123456', 10);

console.log('===============================================================');
console.log('            AIRPAD UDP PROTOCOL TEST CLIENT                   ');
console.log('===============================================================');
console.log(` Target Host     : ${TARGET_HOST}`);
console.log(` Target Port     : ${TARGET_PORT}`);
console.log(` Test PIN Code   : ${PIN_CODE}`);
console.log('---------------------------------------------------------------');

const client = dgram.createSocket('udp4');
let sequence = 1;
let sessionToken = 0;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function sendPacket(opcode, payload = Buffer.alloc(0)) {
  const packet = createPacket(opcode, sessionToken, sequence++, 0x00, payload);
  return new Promise((resolve, reject) => {
    client.send(packet, 0, packet.length, TARGET_PORT, TARGET_HOST, (err) => {
      if (err) reject(err);
      else resolve();
    });
  });
}

client.on('message', (msg, rinfo) => {
  const packet = decodePacket(msg);
  if (!packet) {
    console.warn(`[Client] Received unknown or corrupted packet from ${rinfo.address}:${rinfo.port}`);
    return;
  }

  const { opcode, opcodeName, payload } = packet;

  if (opcode === OPCODES.PAIR_RESPONSE) {
    const resp = parsePairResponse(payload);
    const statusName = PAIR_STATUS_NAMES[resp.statusCode] || `STATUS_${resp.statusCode}`;
    console.log(`[Client] <== RECV [${opcodeName}] | Status: ${statusName} | Token: 0x${resp.sessionToken.toString(16).toUpperCase()}`);

    if (resp.statusCode === PAIR_STATUS.SUCCESS) {
      sessionToken = resp.sessionToken;
      console.log(`[Client] Handshake SUCCESS! Assigned Session Token: 0x${sessionToken.toString(16).toUpperCase()}`);
    } else {
      console.error(`[Client] Handshake REJECTED with code: ${statusName}`);
    }
  } else if (opcode === OPCODES.HEARTBEAT_ACK) {
    const ack = parseHeartbeatAck(payload);
    console.log(`[Client] <== RECV [${opcodeName}] | ClientTs: ${ack.clientTimestamp} | ServerTs: ${ack.serverTimestamp} | Roundtrip RTT: ~${ack.rttMs}ms`);
  } else {
    console.log(`[Client] <== RECV [${opcodeName}] (${payload.length} bytes payload)`);
  }
});

async function runTestSuite() {
  console.log('\n[Step 1] Sending PAIR_REQUEST with PIN: ' + PIN_CODE);
  const pairPayload = encodePairRequest(PIN_CODE, 'a1b2c3d4e5f60718293a4b5c6d7e8f90', 'AirPad-TestClient-Pixel7');
  await sendPacket(OPCODES.PAIR_REQUEST, pairPayload);

  // Wait for PAIR_RESPONSE
  await sleep(300);

  if (!sessionToken) {
    console.warn('\n[Warning] No active session token received. (Check if PIN matches the server console output).');
    console.log('Usage: node test-client.js <6-digit-pin-from-server>');
    process.exit(1);
  }

  console.log('\n[Step 2] Sending MODE_SWITCH (GYRO_TO_MOUSE mode)');
  const modePayload = encodeModeSwitch({
    gyroMode: GYRO_MODES.GYRO_TO_MOUSE,
    sensitivityX: 1.5,
    sensitivityY: 1.5,
    deadzone: 0.02,
    smoothingFactor: 0.2,
    ratchetActive: true,
  });
  await sendPacket(OPCODES.MODE_SWITCH, modePayload);
  await sleep(100);

  console.log('\n[Step 3] Sending MOUSE_DELTA packets (Simulating diagonal movement)');
  for (let i = 1; i <= 5; i++) {
    const dx = i * 4;
    const dy = -i * 3;
    console.log(`[Client] ==> Sending MOUSE_DELTA dx: ${dx}, dy: ${dy}`);
    await sendPacket(OPCODES.MOUSE_DELTA, encodeMouseDelta(dx, dy));
    await sleep(50);
  }

  console.log('\n[Step 4] Sending MOUSE_BUTTON Click (Press & Release)');
  console.log('[Client] ==> Sending MOUSE_BUTTON (Left Click Press)');
  await sendPacket(OPCODES.MOUSE_BUTTON, encodeMouseButton(0x01, 0x01)); // Left button press
  await sleep(80);
  console.log('[Client] ==> Sending MOUSE_BUTTON (Left Click Release)');
  await sendPacket(OPCODES.MOUSE_BUTTON, encodeMouseButton(0x01, 0x00)); // Left button release
  await sleep(100);

  console.log('\n[Step 5] Sending MOUSE_SCROLL (Scroll Up +120)');
  await sendPacket(OPCODES.MOUSE_SCROLL, encodeMouseScroll(120, 0));
  await sleep(100);

  console.log('\n[Step 6] Sending KEYBOARD_KEY (Space down, Space up, Enter tap)');
  console.log('[Client] ==> Sending KEYBOARD_KEY (Space Down)');
  await sendPacket(OPCODES.KEYBOARD_KEY, encodeKeyboardKey(KEY_CODES.SPACE, KEY_ACTIONS.KEY_DOWN));
  await sleep(50);
  console.log('[Client] ==> Sending KEYBOARD_KEY (Space Up)');
  await sendPacket(OPCODES.KEYBOARD_KEY, encodeKeyboardKey(KEY_CODES.SPACE, KEY_ACTIONS.KEY_UP));
  await sleep(50);
  console.log('[Client] ==> Sending KEYBOARD_KEY (Enter Tap)');
  await sendPacket(OPCODES.KEYBOARD_KEY, encodeKeyboardKey(KEY_CODES.ENTER, KEY_ACTIONS.KEY_TAP));
  await sleep(100);

  console.log('\n[Step 7] Sending GAMEPAD_STATE (Pressing A button + Left Stick deflection)');
  const gpPayload = encodeGamepadState({
    buttonMask: 0x0001, // A button
    leftX: 16384,
    leftY: -16384,
    rightX: 0,
    rightY: 0,
    leftTrigger: 0,
    rightTrigger: 255, // Full RT pull
  });
  await sendPacket(OPCODES.GAMEPAD_STATE, gpPayload);
  await sleep(100);

  console.log('\n[Step 8] Sending RAW_IMU_DATA (Gyro Angular Velocity)');
  const imuPayload = encodeRawImu({
    gyroX: 0.05,
    gyroY: -0.12,
    gyroZ: 0.85,
    accelX: 0.02,
    accelY: 9.81,
    accelZ: 0.15,
  });
  await sendPacket(OPCODES.RAW_IMU_DATA, imuPayload);
  await sleep(100);

  console.log('\n[Step 9] Sending HEARTBEAT and measuring roundtrip keepalive');
  await sendPacket(OPCODES.HEARTBEAT, encodeHeartbeat(Date.now()));
  await sleep(200);

  console.log('\n[Step 10] Sending clean DISCONNECT');
  await sendPacket(OPCODES.DISCONNECT, encodeDisconnect(0x00));
  await sleep(200);

  console.log('\n===============================================================');
  console.log('       TEST SUITE COMPLETED SUCCESSFULLY                      ');
  console.log('===============================================================');
  client.close();
  process.exit(0);
}

runTestSuite().catch((err) => {
  console.error('[Client Error]:', err);
  client.close();
  process.exit(1);
});
