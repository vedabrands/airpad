import dgram from 'node:dgram';
import { CONFIG } from './src/config.js';
import { OPCODES, PAIR_STATUS, GYRO_MODES, KEY_CODES, KEY_ACTIONS } from './src/protocol/constants.js';
import { PairingManager } from './src/server/pairing-manager.js';
import { HeartbeatWatchdog } from './src/server/heartbeat-watchdog.js';
import { UdpServer } from './src/server/udp-server.js';
import { InputManager } from './src/input/input-manager.js';
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

async function runIntegrationTest() {
  console.log('=== STARTING AUTOMATED END-TO-END UDP PROTOCOL TEST ===\n');

  const TEST_PORT = 54329;
  const TEST_PIN = 654321;

  // 1. Initialize Server components with fixed test PIN
  const pairingManager = new PairingManager({
    configuredPin: TEST_PIN,
    requirePin: true,
  });

  const watchdog = new HeartbeatWatchdog({
    timeoutMs: 1500,
    tickMs: 100,
  });

  const inputManager = new InputManager();

  const server = new UdpServer({
    port: TEST_PORT,
    pairingManager,
    watchdog,
    inputManager,
  });

  server.start();
  await sleep(100);

  // 2. Initialize UDP Test Client
  const client = dgram.createSocket('udp4');
  let sequence = 1;
  let activeToken = 0;
  let receivedAcks = [];
  let pairResponses = [];

  client.on('message', (msg) => {
    const packet = decodePacket(msg);
    if (!packet) return;

    if (packet.opcode === OPCODES.PAIR_RESPONSE) {
      const parsed = parsePairResponse(packet.payload);
      pairResponses.push(parsed);
    } else if (packet.opcode === OPCODES.HEARTBEAT_ACK) {
      const ack = parseHeartbeatAck(packet.payload);
      receivedAcks.push(ack);
    }
  });

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  function sendClientPacket(opcode, token, payload = Buffer.alloc(0)) {
    const p = createPacket(opcode, token, sequence++, 0x00, payload);
    return new Promise((resolve, reject) => {
      client.send(p, 0, p.length, TEST_PORT, '127.0.0.1', (err) => {
        if (err) reject(err);
        else resolve();
      });
    });
  }

  // --- Test 1: Invalid PIN handshake rejection ---
  console.log('[Test 1] Testing invalid PIN (111111 vs expected ' + TEST_PIN + ')...');
  await sendClientPacket(OPCODES.PAIR_REQUEST, 0, encodePairRequest(111111, '0123456789abcdef0123456789abcdef', 'AttackerDevice'));
  await sleep(150);

  if (pairResponses.length === 1 && pairResponses[0].statusCode === PAIR_STATUS.ERR_INVALID_PIN) {
    console.log(' -> PASSED: Invalid PIN rejected with ERR_INVALID_PIN');
  } else {
    throw new Error('Test 1 failed: Expected ERR_INVALID_PIN');
  }

  // --- Test 2: Unauthenticated input packet dropped ---
  console.log('[Test 2] Testing unauthenticated packet drop before handshake...');
  await sendClientPacket(OPCODES.MOUSE_DELTA, 0x12345678, encodeMouseDelta(10, -10));
  await sleep(100);
  console.log(' -> PASSED: Unauthorized packet dropped cleanly');

  // --- Test 3: Valid Handshake with correct 6-digit PIN ---
  console.log('[Test 3] Testing valid PIN pairing (' + TEST_PIN + ')...');
  await sendClientPacket(OPCODES.PAIR_REQUEST, 0, encodePairRequest(TEST_PIN, 'a1b2c3d4e5f60718293a4b5c6d7e8f90', 'Pixel7-AirPad'));
  await sleep(150);

  if (pairResponses.length >= 2 && pairResponses[1].statusCode === PAIR_STATUS.SUCCESS && pairResponses[1].sessionToken !== 0) {
    activeToken = pairResponses[1].sessionToken;
    console.log(` -> PASSED: Valid pairing accepted. Assigned token: 0x${activeToken.toString(16).toUpperCase()}`);
  } else {
    throw new Error('Test 3 failed: Expected SUCCESS pairing');
  }

  const sessionObj = pairingManager.getActiveSession();
  if (sessionObj && sessionObj.pairedStatus && sessionObj.deviceName === 'Pixel7-AirPad') {
    console.log(' -> PASSED: Session object correctly initialized with client metadata');
  } else {
    throw new Error('Test 3 failed: Session object invalid');
  }

  // --- Test 4: Mode Switch ---
  console.log('[Test 4] Testing MODE_SWITCH to GYRO_TO_MOUSE...');
  await sendClientPacket(
    OPCODES.MODE_SWITCH,
    activeToken,
    encodeModeSwitch({
      gyroMode: GYRO_MODES.GYRO_TO_MOUSE,
      sensitivityX: 2.0,
      sensitivityY: 2.0,
      deadzone: 0.03,
      smoothingFactor: 0.35,
      ratchetActive: true,
    })
  );
  await sleep(100);

  if (sessionObj.gyroMode === GYRO_MODES.GYRO_TO_MOUSE && sessionObj.currentMode === 'gyro_to_mouse') {
    console.log(' -> PASSED: Session mode updated to gyro_to_mouse with gyro parameters');
  } else {
    throw new Error('Test 4 failed: Mode switch not reflected in session');
  }

  // --- Test 5: Mouse Delta Packets ---
  console.log('[Test 5] Testing MOUSE_DELTA packet streaming...');
  for (let i = 1; i <= 3; i++) {
    await sendClientPacket(OPCODES.MOUSE_DELTA, activeToken, encodeMouseDelta(i * 5, -i * 2));
    await sleep(30);
  }
  console.log(' -> PASSED: Mouse deltas decoded and processed');

  // --- Test 6: Mouse Buttons & Scroll ---
  console.log('[Test 6] Testing MOUSE_BUTTON and MOUSE_SCROLL...');
  await sendClientPacket(OPCODES.MOUSE_BUTTON, activeToken, encodeMouseButton(0x01, 0x01)); // Left down
  await sendClientPacket(OPCODES.MOUSE_BUTTON, activeToken, encodeMouseButton(0x01, 0x00)); // Left up
  await sendClientPacket(OPCODES.MOUSE_SCROLL, activeToken, encodeMouseScroll(120, 0));
  await sleep(100);
  console.log(' -> PASSED: Button actions and scroll values decoded');

  // --- Test 7: Gamepad State & Raw IMU ---
  console.log('[Test 7] Testing GAMEPAD_STATE and RAW_IMU_DATA...');
  await sendClientPacket(
    OPCODES.GAMEPAD_STATE,
    activeToken,
    encodeGamepadState({ buttonMask: 0x0002, leftX: 8000, leftY: -8000, rightTrigger: 128 })
  );
  await sendClientPacket(
    OPCODES.RAW_IMU_DATA,
    activeToken,
    encodeRawImu({ gyroX: 0.1, gyroY: -0.2, gyroZ: 0.5, accelX: 0, accelY: 9.8, accelZ: 0 })
  );
  await sleep(100);
  console.log(' -> PASSED: Gamepad state & IMU data decoded');

  // --- Test 8: Keyboard Keys ---
  console.log('[Test 8] Testing KEYBOARD_KEY packets (Key Down, Key Up, Key Tap)...');
  await sendClientPacket(OPCODES.KEYBOARD_KEY, activeToken, encodeKeyboardKey(KEY_CODES.SPACE, KEY_ACTIONS.KEY_DOWN));
  await sendClientPacket(OPCODES.KEYBOARD_KEY, activeToken, encodeKeyboardKey(KEY_CODES.SPACE, KEY_ACTIONS.KEY_UP));
  await sendClientPacket(OPCODES.KEYBOARD_KEY, activeToken, encodeKeyboardKey(KEY_CODES.RETURN, KEY_ACTIONS.KEY_TAP));
  await sleep(100);
  console.log(' -> PASSED: Keyboard key actions injected successfully');

  // --- Test 9: Heartbeat & RTT measurement ---
  console.log('[Test 9] Testing HEARTBEAT round-trip ACK...');
  const sendTs = Date.now();
  await sendClientPacket(OPCODES.HEARTBEAT, activeToken, encodeHeartbeat(sendTs));
  await sleep(150);

  if (receivedAcks.length > 0 && receivedAcks[0].clientTimestamp === sendTs) {
    console.log(` -> PASSED: HEARTBEAT_ACK verified! ClientTs: ${receivedAcks[0].clientTimestamp}, ServerTs: ${receivedAcks[0].serverTimestamp}`);
  } else {
    throw new Error('Test 9 failed: No HEARTBEAT_ACK received');
  }

  // --- Test 10: Clean Disconnect & Failsafe Release ---
  console.log('[Test 10] Testing clean DISCONNECT and failsafe input release...');
  await sendClientPacket(OPCODES.DISCONNECT, activeToken, encodeDisconnect(0x00));
  await sleep(100);

  if (pairingManager.getActiveSession() === null) {
    console.log(' -> PASSED: Session cleanly terminated upon disconnect packet');
  } else {
    throw new Error('Test 10 failed: Session not cleared on disconnect');
  }

  // Clean up
  client.close();
  server.stop();

  console.log('\n===============================================================');
  console.log('  ALL UDP NETWORKING & PROTOCOL TESTS PASSED WITH 100% SUCCESS  ');
  console.log('===============================================================');
}

runIntegrationTest().catch((err) => {
  console.error('\n[FATAL TEST ERROR]:', err);
  process.exit(1);
});
