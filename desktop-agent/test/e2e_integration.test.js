import test from 'node:test';
import assert from 'node:assert/strict';
import dgram from 'node:dgram';
import { OPCODES, PAIR_STATUS, GYRO_MODES } from '../src/protocol/constants.js';
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
  encodeGamepadState,
  encodeDisconnect
} from '../src/protocol/packet.js';
import { UdpServer } from '../src/server/udp-server.js';
import { DiscoveryService } from '../src/server/discovery-service.js';
import { PairingManager } from '../src/server/pairing-manager.js';
import { HeartbeatWatchdog } from '../src/server/heartbeat-watchdog.js';
import { InputManager } from '../src/input/input-manager.js';

test('End-to-End Live Integration: Discovery -> Pairing -> Heartbeat -> Input -> Disconnect', async () => {
  const TEST_CONTROL_PORT = 54391;
  const TEST_DISCOVERY_PORT = 54390;
  const TEST_PIN = 842915;

  const pairingManager = new PairingManager({
    configuredPin: TEST_PIN,
    requirePin: true
  });

  const watchdog = new HeartbeatWatchdog({
    timeoutMs: 1500,
    tickMs: 250
  });

  const inputManager = new InputManager({
    enableGamepad: false // test without hardware dependency
  });

  const udpServer = new UdpServer({
    port: TEST_CONTROL_PORT,
    pairingManager,
    watchdog,
    inputManager,
    logLevel: 'error',
    statsIntervalMs: 0
  });

  const discoveryService = new DiscoveryService({
    broadcastPort: TEST_DISCOVERY_PORT,
    controlPort: TEST_CONTROL_PORT,
    serverName: 'Integration-Test-Server',
    osType: 1, // Windows
    requirePin: true,
    intervalMs: 100
  });

  udpServer.start();
  discoveryService.start();

  // Create simulated Android UDP client
  const clientSocket = dgram.createSocket('udp4');
  await new Promise((resolve) => clientSocket.bind(0, resolve));

  let sessionToken = 0;
  let sequence = 1;

  try {
    // 1. Test Pairing Handshake (Invalid PIN first)
    const badPairPayload = encodePairRequest(999999, '12345678123456781234567812345678', 'Android-Test-Phone');
    const badPairPacket = createPacket(OPCODES.PAIR_REQUEST, 0, sequence++, 0, badPairPayload);

    const badPairPromise = new Promise((resolve) => {
      const handler = (msg) => {
        const decoded = decodePacket(msg);
        if (decoded && decoded.opcode === OPCODES.PAIR_RESPONSE) {
          clientSocket.off('message', handler);
          resolve(parsePairResponse(decoded.payload));
        }
      };
      clientSocket.on('message', handler);
    });

    clientSocket.send(badPairPacket, TEST_CONTROL_PORT, '127.0.0.1');
    const badPairRes = await badPairPromise;
    assert.equal(badPairRes.statusCode, PAIR_STATUS.ERR_INVALID_PIN);
    assert.equal(badPairRes.sessionToken, 0);

    // 2. Test Pairing Handshake (Valid 6-Digit PIN)
    const goodPairPayload = encodePairRequest(TEST_PIN, '12345678123456781234567812345678', 'Android-Test-Phone');
    const goodPairPacket = createPacket(OPCODES.PAIR_REQUEST, 0, sequence++, 0, goodPairPayload);

    const goodPairPromise = new Promise((resolve) => {
      const handler = (msg) => {
        const decoded = decodePacket(msg);
        if (decoded && decoded.opcode === OPCODES.PAIR_RESPONSE) {
          clientSocket.off('message', handler);
          resolve(parsePairResponse(decoded.payload));
        }
      };
      clientSocket.on('message', handler);
    });

    clientSocket.send(goodPairPacket, TEST_CONTROL_PORT, '127.0.0.1');
    const goodPairRes = await goodPairPromise;
    assert.equal(goodPairRes.statusCode, PAIR_STATUS.SUCCESS);
    assert.notEqual(goodPairRes.sessionToken, 0);
    sessionToken = goodPairRes.sessionToken;

    // Verify server registered the session
    const activeSession = pairingManager.getActiveSession();
    assert.ok(activeSession);
    assert.equal(activeSession.deviceName, 'Android-Test-Phone');
    assert.equal(activeSession.sessionToken, sessionToken);

    // 3. Test Heartbeat Ping-Pong and RTT calculation
    const clientTs = Date.now();
    const hbPayload = encodeHeartbeat(clientTs);
    const hbPacket = createPacket(OPCODES.HEARTBEAT, sessionToken, sequence++, 0, hbPayload);

    const hbPromise = new Promise((resolve) => {
      const handler = (msg) => {
        const decoded = decodePacket(msg);
        if (decoded && decoded.opcode === OPCODES.HEARTBEAT_ACK) {
          clientSocket.off('message', handler);
          resolve(parseHeartbeatAck(decoded.payload));
        }
      };
      clientSocket.on('message', handler);
    });

    clientSocket.send(hbPacket, TEST_CONTROL_PORT, '127.0.0.1');
    const hbAck = await hbPromise;
    assert.equal(hbAck.clientTimestamp, clientTs);
    assert.ok(hbAck.serverTimestamp >= clientTs);

    // 4. Test Mode Switch Event (Tri-State Gyro config)
    const modePayload = encodeModeSwitch({
      gyroMode: GYRO_MODES.GYRO_TO_MOUSE,
      invertX: false,
      invertY: true,
      ratchetActive: true,
      resetBias: false,
      sensitivityX: 2.0,
      sensitivityY: 2.0,
      deadzone: 0.03,
      smoothingFactor: 0.3
    });
    const modePacket = createPacket(OPCODES.MODE_SWITCH, sessionToken, sequence++, 0, modePayload);
    clientSocket.send(modePacket, TEST_CONTROL_PORT, '127.0.0.1');

    // 5. Test Mouse Delta Event
    const mouseDeltaPayload = encodeMouseDelta(15, -10, 0.25, 0.75);
    const mouseDeltaPacket = createPacket(OPCODES.MOUSE_DELTA, sessionToken, sequence++, 0, mouseDeltaPayload);
    clientSocket.send(mouseDeltaPacket, TEST_CONTROL_PORT, '127.0.0.1');

    // 6. Test Mouse Button Click Event
    const mouseBtnPayload = encodeMouseButton(0x01, 0x01); // Left Press
    const mouseBtnPacket = createPacket(OPCODES.MOUSE_BUTTON, sessionToken, sequence++, 0, mouseBtnPayload);
    clientSocket.send(mouseBtnPacket, TEST_CONTROL_PORT, '127.0.0.1');

    // 7. Test Gamepad State Event (Xbox 360 virtual input frame)
    const gamepadPayload = encodeGamepadState({
      buttonMask: 0x1000, // A button
      leftX: 16000,
      leftY: 0,
      rightX: 0,
      rightY: -16000,
      leftTrigger: 255,
      rightTrigger: 0
    });
    const gamepadPacket = createPacket(OPCODES.GAMEPAD_STATE, sessionToken, sequence++, 0, gamepadPayload);
    clientSocket.send(gamepadPacket, TEST_CONTROL_PORT, '127.0.0.1');

    // 8. Test Graceful Disconnect
    const disconnectPayload = encodeDisconnect(0);
    const disconnectPacket = createPacket(OPCODES.DISCONNECT, sessionToken, sequence++, 0, disconnectPayload);
    clientSocket.send(disconnectPacket, TEST_CONTROL_PORT, '127.0.0.1');

    // Wait 100ms for disconnect processing
    await new Promise((resolve) => setTimeout(resolve, 100));
    assert.equal(pairingManager.getActiveSession(), null);

  } finally {
    clientSocket.close();
    discoveryService.stop();
    udpServer.stop();
    inputManager.destroy();
  }
});
