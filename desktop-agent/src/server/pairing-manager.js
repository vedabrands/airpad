import crypto from 'node:crypto';
import { EventEmitter } from 'node:events';
import { PAIR_STATUS, GYRO_MODES } from '../protocol/constants.js';

export class PairingManager extends EventEmitter {
  /**
   * @param {object} options
   * @param {string|number} [options.configuredPin='auto']
   * @param {boolean} [options.requirePin=true]
   */
  constructor({ configuredPin = 'auto', requirePin = true } = {}) {
    super();
    this.requirePin = requirePin;
    this.activePin = this._resolvePin(configuredPin);
    /** @type {Map<number, object>} sessionToken -> SessionObject */
    this.sessions = new Map();
    /** @type {object|null} Active primary session */
    this.currentSession = null;
  }

  /**
   * Generates or parses a 6-digit PIN (100000 - 999999).
   * @param {string|number} pinSetting
   * @returns {number}
   */
  _resolvePin(pinSetting) {
    if (pinSetting && pinSetting !== 'auto') {
      const parsed = parseInt(String(pinSetting), 10);
      if (!isNaN(parsed) && parsed >= 100000 && parsed <= 999999) {
        return parsed;
      }
    }
    // Generate cryptographically secure random 6-digit PIN (100000 to 999999)
    return crypto.randomInt(100000, 1000000);
  }

  /**
   * Returns current 6-digit pairing PIN code.
   * @returns {number}
   */
  getPin() {
    return this.activePin;
  }

  /**
   * Regenerates a new 6-digit random PIN.
   * @returns {number}
   */
  regeneratePin() {
    this.activePin = crypto.randomInt(100000, 1000000);
    this.emit('pin_changed', this.activePin);
    return this.activePin;
  }

  /**
   * Validates a pair request from a mobile client.
   * @param {object} params
   * @param {number} params.pinCode
   * @param {string} params.clientUid
   * @param {string} params.deviceName
   * @param {string} params.remoteAddress
   * @param {number} params.remotePort
   * @returns {{ status: number, sessionToken: number }}
   */
  handlePairRequest({ pinCode, clientUid, deviceName, remoteAddress, remotePort }) {
    if (this.requirePin && pinCode !== this.activePin) {
      console.warn(
        `[PairingManager] REJECTED pairing attempt from ${remoteAddress}:${remotePort} ` +
        `("${deviceName}", UID: ${clientUid.substring(0, 8)}...): ` +
        `Invalid PIN provided (${pinCode}), expected (${this.activePin})`
      );
      return { status: PAIR_STATUS.ERR_INVALID_PIN, sessionToken: 0 };
    }

    // Generate random 32-bit uint session token (non-zero: 1 to 0xFFFFFFFF)
    const sessionToken = crypto.randomInt(1, 0xFFFFFFFF);

    const sessionObject = {
      sessionToken,
      clientUid,
      deviceName,
      remoteAddress,
      remotePort,
      pairedStatus: true,
      currentMode: 'off',
      gyroMode: GYRO_MODES.OFF,
      gyroConfig: {
        invertX: false,
        invertY: false,
        ratchetActive: false,
        sensitivityX: 1.0,
        sensitivityY: 1.0,
        deadzone: 0.02,
        smoothingFactor: 0.25,
      },
      pairedAt: Date.now(),
      lastSeen: Date.now(),
      packetsReceived: 0,
      lastSequence: 0,
    };

    this.sessions.set(sessionToken, sessionObject);
    this.currentSession = sessionObject;

    console.log(
      `[PairingManager] AUTHENTICATED client "${deviceName}" (${remoteAddress}:${remotePort}) | ` +
      `PIN: ${pinCode} | Session Token: 0x${sessionToken.toString(16).toUpperCase().padStart(8, '0')}`
    );

    this.emit('session_authenticated', sessionObject);

    return { status: PAIR_STATUS.SUCCESS, sessionToken };
  }

  /**
   * Verifies if a packet has a valid session token and updates lastSeen timestamp.
   * @param {number} sessionToken
   * @param {string} remoteAddress
   * @param {number} remotePort
   * @param {number} [sequence=0]
   * @returns {object|null} The session object if valid, else null
   */
  validateSession(sessionToken, remoteAddress, remotePort, sequence = 0) {
    if (!sessionToken || sessionToken === 0) return null;

    const session = this.sessions.get(sessionToken);
    if (!session || !session.pairedStatus) return null;

    // Verify remote IP matches (allow client port rebound if OS changes it)
    if (session.remoteAddress !== remoteAddress) {
      console.warn(
        `[PairingManager] Session token 0x${sessionToken.toString(16)} IP mismatch: ` +
        `Expected ${session.remoteAddress}, got ${remoteAddress}`
      );
      return null;
    }

    // Update session state
    session.lastSeen = Date.now();
    session.remotePort = remotePort;
    session.packetsReceived++;
    session.lastSequence = sequence;

    return session;
  }

  /**
   * Updates gyro / operating mode on the active session.
   * @param {number} sessionToken
   * @param {object} modeConfig
   */
  updateSessionMode(sessionToken, modeConfig) {
    const session = this.sessions.get(sessionToken);
    if (!session) return;

    session.gyroMode = modeConfig.gyroMode;
    session.currentMode =
      modeConfig.gyroMode === GYRO_MODES.GYRO_TO_MOUSE
        ? 'gyro_to_mouse'
        : modeConfig.gyroMode === GYRO_MODES.GYRO_TO_STICK
        ? 'gyro_to_stick'
        : 'off';

    session.gyroConfig = {
      invertX: modeConfig.invertX,
      invertY: modeConfig.invertY,
      ratchetActive: modeConfig.ratchetActive,
      sensitivityX: modeConfig.sensitivityX,
      sensitivityY: modeConfig.sensitivityY,
      deadzone: modeConfig.deadzone,
      smoothingFactor: modeConfig.smoothingFactor,
    };

    this.emit('mode_changed', { session, modeConfig });
  }

  /**
   * Returns the primary active session.
   * @returns {object|null}
   */
  getActiveSession() {
    return this.currentSession;
  }

  /**
   * Marks a session disconnected and removes it.
   * @param {number} [sessionToken]
   * @returns {object|null}
   */
  clearSession(sessionToken) {
    let session = null;
    if (sessionToken && this.sessions.has(sessionToken)) {
      session = this.sessions.get(sessionToken);
      session.pairedStatus = false;
      this.sessions.delete(sessionToken);
      if (this.currentSession?.sessionToken === sessionToken) {
        this.currentSession = null;
      }
    } else if (this.currentSession) {
      session = this.currentSession;
      session.pairedStatus = false;
      this.sessions.delete(session.sessionToken);
      this.currentSession = null;
    }

    if (session) {
      this.emit('session_cleared', session);
    }
    return session;
  }
}
