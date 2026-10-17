import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { PairingManager } from '../src/server/pairing-manager.js';
import { PAIR_STATUS, GYRO_MODES } from '../src/protocol/constants.js';

describe('PairingManager', () => {
  it('should initialize with static or auto-generated PIN', () => {
    const pmAuto = new PairingManager({ configuredPin: 'auto', requirePin: true });
    const pin = pmAuto.getPin();
    assert.ok(pin >= 100000 && pin <= 999999);

    const pmStatic = new PairingManager({ configuredPin: '123456', requirePin: true });
    assert.equal(pmStatic.getPin(), 123456);
  });

  it('should regenerate PIN and emit pin_changed', () => {
    const pm = new PairingManager({ configuredPin: '123456' });
    let emittedPin = null;
    pm.on('pin_changed', (newPin) => {
      emittedPin = newPin;
    });

    const newPin = pm.regeneratePin();
    assert.equal(emittedPin, newPin);
    assert.ok(newPin >= 100000 && newPin <= 999999);
  });

  it('should authenticate pairing request with correct PIN and reject invalid PIN', () => {
    const pm = new PairingManager({ configuredPin: '654321', requirePin: true });

    // Invalid PIN
    const failResult = pm.handlePairRequest({
      pinCode: 111111,
      clientUid: 'uid-12345678-abcd',
      deviceName: 'Pixel 8',
      remoteAddress: '192.168.1.50',
      remotePort: 54321,
    });
    assert.equal(failResult.status, PAIR_STATUS.ERR_INVALID_PIN);
    assert.equal(failResult.sessionToken, 0);

    // Valid PIN
    let authenticatedSession = null;
    pm.on('session_authenticated', (session) => {
      authenticatedSession = session;
    });

    const successResult = pm.handlePairRequest({
      pinCode: 654321,
      clientUid: 'uid-12345678-abcd',
      deviceName: 'Pixel 8',
      remoteAddress: '192.168.1.50',
      remotePort: 54321,
    });

    assert.equal(successResult.status, PAIR_STATUS.SUCCESS);
    assert.ok(successResult.sessionToken > 0);
    assert.ok(authenticatedSession);
    assert.equal(authenticatedSession.sessionToken, successResult.sessionToken);
    assert.equal(authenticatedSession.deviceName, 'Pixel 8');

    // Validation
    const validSession = pm.validateSession(successResult.sessionToken, '192.168.1.50', 54321, 1);
    assert.ok(validSession);
    assert.equal(validSession.packetsReceived, 1);

    // IP mismatch
    const badIpSession = pm.validateSession(successResult.sessionToken, '192.168.1.99', 54321, 2);
    assert.equal(badIpSession, null);
  });

  it('should update session mode config and clear session', () => {
    const pm = new PairingManager({ configuredPin: '654321', requirePin: false });
    const { sessionToken } = pm.handlePairRequest({
      pinCode: 0,
      clientUid: 'uid-1',
      deviceName: 'Galaxy S24',
      remoteAddress: '10.0.0.5',
      remotePort: 54321,
    });

    pm.updateSessionMode(sessionToken, {
      gyroMode: GYRO_MODES.GYRO_TO_MOUSE,
      invertX: true,
      invertY: false,
      ratchetActive: true,
      sensitivityX: 2.0,
      sensitivityY: 1.5,
      deadzone: 0.05,
      smoothingFactor: 0.1,
    });

    const activeSession = pm.getActiveSession();
    assert.ok(activeSession);
    assert.equal(activeSession.gyroMode, GYRO_MODES.GYRO_TO_MOUSE);
    assert.equal(activeSession.currentMode, 'gyro_to_mouse');
    assert.equal(activeSession.gyroConfig.invertX, true);

    // Clear session
    let clearedSession = null;
    pm.on('session_cleared', (s) => {
      clearedSession = s;
    });

    const cleared = pm.clearSession(sessionToken);
    assert.equal(cleared.sessionToken, sessionToken);
    assert.equal(clearedSession.sessionToken, sessionToken);
    assert.equal(pm.getActiveSession(), null);
    assert.equal(pm.validateSession(sessionToken, '10.0.0.5', 54321), null);
  });
});
