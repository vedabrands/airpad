import dgram from 'node:dgram';
import { EventEmitter } from 'node:events';
import {
  OPCODES,
  OPCODE_NAMES,
  PAIR_STATUS,
  PAIR_STATUS_NAMES,
  GYRO_MODE_NAMES,
  KEY_ACTION_NAMES,
  KEY_NAMES,
} from '../protocol/constants.js';
import {
  decodePacket,
  createPacket,
  parseHeartbeat,
  encodeHeartbeatAck,
  parsePairRequest,
  encodePairResponse,
  parseModeSwitch,
  parseMouseDelta,
  parseMouseButton,
  parseMouseScroll,
  parseMouseCombo,
  parseKeyboardKey,
  parseGamepadState,
  parseGamepadStick,
  parseGamepadButton,
  parseGamepadTrigger,
  parseRawImu,
  parseGyroCalibrate,
  parseDisconnect,
  parseActiveWindowInfo,
  parseGameProfileSync,
  parseProfileChange,
  parseMacroTrigger,
} from '../protocol/packet.js';

export class UdpServer extends EventEmitter {
  /**
   * @param {object} options
   * @param {number} [options.port=54321]
   * @param {import('./pairing-manager.js').PairingManager} options.pairingManager
   * @param {import('./heartbeat-watchdog.js').HeartbeatWatchdog} options.watchdog
   * @param {import('../input/input-manager.js').InputManager} [options.inputManager]
   * @param {string} [options.logLevel='info']
   * @param {number} [options.statsIntervalMs=2000]
   */
  constructor({
    port = 54321,
    pairingManager,
    watchdog,
    inputManager = null,
    logLevel = 'info',
    statsIntervalMs = 2000,
  }) {
    super();
    this.port = port;
    this.pairingManager = pairingManager;
    this.watchdog = watchdog;
    this.inputManager = inputManager;
    this.logLevel = logLevel;
    this.statsIntervalMs = statsIntervalMs;
    this.socket = null;
    this.sequence = 0;
    this.statsTimer = null;

    // Traffic statistics
    this.stats = {
      windowPackets: 0,
      windowBytes: 0,
      totalPackets: 0,
      totalBytes: 0,
      lastStatsTime: Date.now(),
    };
  }

  /**
   * Starts the UDP datagram server on the configured port.
   */
  start() {
    this.socket = dgram.createSocket('udp4');

    this.socket.on('error', (err) => {
      console.error(`[UdpServer] Socket error: ${err.message}`);
      this.emit('error', err);
    });

    this.socket.on('message', (msg, rinfo) => {
      this._handleMessage(msg, rinfo);
    });

    this.socket.bind(this.port, () => {
      console.log(`[UdpServer] Control server listening on UDP port ${this.port}`);
      this.emit('listening', { port: this.port });
    });

    // Wire watchdog timeout to disconnect handler & failsafe
    this.watchdog.on('timeout', ({ elapsedMs }) => {
      if (this.inputManager) {
        this.inputManager.failsafeReleaseAll();
      }
      const session = this.pairingManager.clearSession();
      if (session) {
        console.warn(
          `[UdpServer] Heartbeat timeout triggered (${elapsedMs}ms without packet). ` +
          `Client "${session.deviceName}" (${session.remoteAddress}:${session.remotePort}) marked DISCONNECTED.`
        );
        this.emit('client_disconnected', { session, reason: 'timeout' });
      }
    });

    this.watchdog.on('soft_lag', ({ elapsedMs }) => {
      if (this.logLevel === 'debug') {
        console.warn(`[UdpServer] Soft lag warning: ${elapsedMs}ms since last packet.`);
      }
    });

    this.watchdog.start();

    // Start periodic traffic rate reporter
    if (this.statsIntervalMs > 0) {
      this.statsTimer = setInterval(() => {
        this._reportTrafficStats();
      }, this.statsIntervalMs);
    }
  }

  /**
   * Periodic traffic rate summary logger.
   */
  _reportTrafficStats() {
    const now = Date.now();
    const elapsedSec = (now - this.stats.lastStatsTime) / 1000;
    if (elapsedSec <= 0) return;

    const pps = Math.round(this.stats.windowPackets / elapsedSec);
    const kbps = ((this.stats.windowBytes * 8) / (elapsedSec * 1000)).toFixed(1);

    const activeSession = this.pairingManager.getActiveSession();
    if (pps > 0 && activeSession && this.logLevel !== 'quiet') {
      const modeLabel = activeSession.currentMode || 'off';
      console.log(
        `[Traffic] ${pps} pkts/s (${kbps} kbps) | Client: "${activeSession.deviceName}" | Mode: ${modeLabel}`
      );
    }

    this.stats.windowPackets = 0;
    this.stats.windowBytes = 0;
    this.stats.lastStatsTime = now;
  }

  /**
   * Internal packet processor and dispatcher.
   * @param {Buffer} msg
   * @param {dgram.RemoteInfo} rinfo
   */
  _handleMessage(msg, rinfo) {
    this.stats.windowPackets++;
    this.stats.windowBytes += msg.length;
    this.stats.totalPackets++;
    this.stats.totalBytes += msg.length;

    const packet = decodePacket(msg);
    if (!packet) {
      // Drop corrupted, unparseable, or non-protocol packets silently
      return;
    }

    const { sequence, sessionToken, opcode, opcodeName, payload } = packet;

    // -----------------------------------------------------------------------
    // Opcode 0x06: PAIR_REQUEST (Unauthenticated Handshake)
    // -----------------------------------------------------------------------
    if (opcode === OPCODES.PAIR_REQUEST) {
      const pairReq = parsePairRequest(payload);
      if (!pairReq) {
        console.warn(`[UdpServer] Malformed PAIR_REQUEST from ${rinfo.address}:${rinfo.port}`);
        return;
      }

      console.log(
        `[UdpServer] <== RECV [${opcodeName}] (Seq: #${sequence}) from ${rinfo.address}:${rinfo.port} | ` +
        `PIN: ${pairReq.pinCode} | Device: "${pairReq.deviceName}" | UID: ${pairReq.clientUid.substring(0, 8)}...`
      );

      const result = this.pairingManager.handlePairRequest({
        ...pairReq,
        remoteAddress: rinfo.address,
        remotePort: rinfo.port,
      });

      const respPayload = encodePairResponse(result.status, result.sessionToken, 60);
      const respPacket = createPacket(
        OPCODES.PAIR_RESPONSE,
        result.sessionToken,
        this.sequence++,
        0x00,
        respPayload
      );

      this.socket.send(respPacket, 0, respPacket.length, rinfo.port, rinfo.address, (err) => {
        if (!err) {
          const statusStr = PAIR_STATUS_NAMES[result.status] || `STATUS_${result.status}`;
          console.log(
            `[UdpServer] ==> SENT [PAIR_RESPONSE] to ${rinfo.address}:${rinfo.port} | ` +
            `Status: ${statusStr} | Token: 0x${result.sessionToken.toString(16).toUpperCase().padStart(8, '0')}`
          );
        }
      });

      if (result.status === PAIR_STATUS.SUCCESS) {
        this.watchdog.feed();
        this.emit('client_paired', {
          deviceName: pairReq.deviceName,
          remoteAddress: rinfo.address,
          sessionToken: result.sessionToken,
        });
      }
      return;
    }

    // -----------------------------------------------------------------------
    // Authenticate Session Token for all other opcodes
    // -----------------------------------------------------------------------
    const session = this.pairingManager.validateSession(sessionToken, rinfo.address, rinfo.port, sequence);
    if (!session) {
      if (this.logLevel === 'debug') {
        console.warn(
          `[UdpServer] DROPPED unauthorized packet [${opcodeName}] from ${rinfo.address}:${rinfo.port} | ` +
          `Invalid Session Token: 0x${sessionToken.toString(16).toUpperCase()}`
        );
      }
      return;
    }

    // Feed the watchdog to prevent timeout disconnect
    this.watchdog.feed();

    // -----------------------------------------------------------------------
    // Opcode Routing, Decoding & Logging
    // -----------------------------------------------------------------------
    switch (opcode) {
      case OPCODES.HEARTBEAT: {
        const hb = parseHeartbeat(payload);
        const ackPayload = encodeHeartbeatAck(hb.clientTimestamp, Date.now());
        const ackPacket = createPacket(
          OPCODES.HEARTBEAT_ACK,
          sessionToken,
          this.sequence++,
          0x00,
          ackPayload
        );
        this.socket.send(ackPacket, 0, ackPacket.length, rinfo.port, rinfo.address);
        if (this.logLevel === 'debug') {
          console.log(
            `[UdpServer] <== RECV [HEARTBEAT] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `ClientTs: ${hb.clientTimestamp} | Replied with HEARTBEAT_ACK`
          );
        }
        break;
      }

      case OPCODES.DISCONNECT: {
        const disc = parseDisconnect(payload);
        console.log(
          `[UdpServer] <== RECV [DISCONNECT] (Seq: #${sequence}) from "${session.deviceName}" | Reason Code: ${disc.reasonCode}`
        );
        if (this.inputManager) {
          this.inputManager.failsafeReleaseAll();
        }
        this.pairingManager.clearSession(sessionToken);
        this.emit('client_disconnected', { session, reason: 'client_request' });
        break;
      }

      case OPCODES.MODE_SWITCH: {
        const modeConfig = parseModeSwitch(payload);
        if (modeConfig) {
          this.pairingManager.updateSessionMode(sessionToken, modeConfig);
          const modeName = GYRO_MODE_NAMES[modeConfig.gyroMode] || `MODE_${modeConfig.gyroMode}`;
          console.log(
            `[UdpServer] <== RECV [MODE_SWITCH] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `Gyro Mode: ${modeName} | Sens: (${modeConfig.sensitivityX.toFixed(2)}x, ${modeConfig.sensitivityY.toFixed(2)}x) | ` +
            `Deadzone: ${modeConfig.deadzone.toFixed(3)} | Smoothing: ${modeConfig.smoothingFactor.toFixed(2)} | ` +
            `Ratchet: ${modeConfig.ratchetActive}`
          );
          if (this.inputManager) {
            this.inputManager.handleModeSwitch(modeConfig);
          }
          this.emit('mode_switch', modeConfig);
        }
        break;
      }

      case OPCODES.MOUSE_DELTA: {
        const delta = parseMouseDelta(payload);
        if (delta) {
          if (this.logLevel === 'debug') {
            console.log(
              `[UdpServer] <== RECV [MOUSE_DELTA] (Seq: #${sequence}) from "${session.deviceName}" | ` +
              `dx: ${delta.dx.toFixed(2)}, dy: ${delta.dy.toFixed(2)}`
            );
          }
          if (this.inputManager) {
            this.inputManager.handleMouseDelta(delta);
          }
        }
        break;
      }

      case OPCODES.MOUSE_BUTTON: {
        const btn = parseMouseButton(payload);
        if (btn) {
          const actionName = btn.action === 1 ? 'PRESS' : btn.action === 0 ? 'RELEASE' : 'CLICK';
          console.log(
            `[UdpServer] <== RECV [MOUSE_BUTTON] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `ButtonMask: 0x${btn.buttonMask.toString(16).padStart(2, '0')} | Action: ${actionName}`
          );
          if (this.inputManager) {
            this.inputManager.handleMouseButton(btn);
          }
        }
        break;
      }

      case OPCODES.MOUSE_SCROLL: {
        const scroll = parseMouseScroll(payload);
        if (scroll) {
          if (this.logLevel === 'debug') {
            console.log(
              `[UdpServer] <== RECV [MOUSE_SCROLL] (Seq: #${sequence}) from "${session.deviceName}" | ` +
              `ScrollY: ${scroll.scrollY}, ScrollX: ${scroll.scrollX}`
            );
          }
          if (this.inputManager) {
            this.inputManager.handleMouseScroll(scroll);
          }
        }
        break;
      }

      case OPCODES.MOUSE_COMBO: {
        const combo = parseMouseCombo(payload);
        if (combo) {
          if (this.logLevel === 'debug') {
            console.log(
              `[UdpServer] <== RECV [MOUSE_COMBO] (Seq: #${sequence}) from "${session.deviceName}" | ` +
              `dx: ${combo.dx.toFixed(2)}, dy: ${combo.dy.toFixed(2)}, buttons: 0x${combo.buttonState.toString(16)}` +
              (combo.deltas ? ` | BATCHED: ${combo.deltas.length} deltas` : '')
            );
          }
          if (this.inputManager) {
            // Handle batched deltas for lower latency
            if (combo.deltas && combo.deltas.length > 1) {
              this.inputManager.handleBatchedMouseDeltas(combo.deltas);
            } else if (combo.dx !== 0 || combo.dy !== 0) {
              this.inputManager.handleMouseCombo(combo);
            }
          }
        }
        break;
      }

      case OPCODES.KEYBOARD_KEY: {
        const keyEvent = parseKeyboardKey(payload);
        if (keyEvent) {
          const keyName = KEY_NAMES[keyEvent.keyCode] || `KEY_0x${keyEvent.keyCode.toString(16)}`;
          const actName = KEY_ACTION_NAMES[keyEvent.action] || `ACT_${keyEvent.action}`;
          console.log(
            `[UdpServer] <== RECV [KEYBOARD_KEY] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `Key: ${keyName} | Action: ${actName}`
          );
          if (this.inputManager) {
            this.inputManager.handleKeyboardKey(keyEvent);
          }
        }
        break;
      }

      case OPCODES.GAMEPAD_STATE: {
        const gp = parseGamepadState(payload);
        if (gp) {
          if (this.logLevel === 'debug') {
            console.log(
              `[UdpServer] <== RECV [GAMEPAD_STATE] (Seq: #${sequence}) from "${session.deviceName}" | ` +
              `Buttons: 0x${gp.buttonMask.toString(16).padStart(4, '0')} | LS: (${gp.leftX}, ${gp.leftY})`
            );
          }
          if (this.inputManager) {
            this.inputManager.handleGamepadState(gp);
          }
        }
        break;
      }

      case OPCODES.GAMEPAD_STICK: {
        const stick = parseGamepadStick(payload);
        if (stick) {
          if (this.logLevel === 'debug') {
            const stickLabel = stick.stickId === 0 ? 'LeftStick' : 'RightStick';
            console.log(
              `[UdpServer] <== RECV [GAMEPAD_STICK] (Seq: #${sequence}) from "${session.deviceName}" | ` +
              `${stickLabel} Pos: (${stick.posX}, ${stick.posY})`
            );
          }
          if (this.inputManager) {
            this.inputManager.handleGamepadStick(stick);
          }
        }
        break;
      }

      case OPCODES.GAMEPAD_BUTTON: {
        const btn = parseGamepadButton(payload);
        if (btn) {
          const actStr = btn.action === 1 ? 'PRESS' : 'RELEASE';
          console.log(
            `[UdpServer] <== RECV [GAMEPAD_BUTTON] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `Bit: 0x${btn.buttonBit.toString(16)} | Action: ${actStr}`
          );
          if (this.inputManager) {
            this.inputManager.handleGamepadButton(btn);
          }
        }
        break;
      }

      case OPCODES.GAMEPAD_TRIGGER: {
        const trg = parseGamepadTrigger(payload);
        if (trg) {
          if (this.logLevel === 'debug') {
            const trgLabel = trg.triggerId === 0 ? 'LeftTrigger' : 'RightTrigger';
            console.log(
              `[UdpServer] <== RECV [GAMEPAD_TRIGGER] (Seq: #${sequence}) from "${session.deviceName}" | ` +
              `${trgLabel}: ${trg.value}`
            );
          }
          if (this.inputManager) {
            this.inputManager.handleGamepadTrigger(trg);
          }
        }
        break;
      }

      case OPCODES.RAW_IMU_DATA: {
        const imu = parseRawImu(payload);
        if (imu) {
          if (this.logLevel === 'debug') {
            console.log(
              `[UdpServer] <== RECV [RAW_IMU_DATA] (Seq: #${sequence}) from "${session.deviceName}" | ` +
              `Gyro: (${imu.gyroX.toFixed(3)}, ${imu.gyroY.toFixed(3)}, ${imu.gyroZ.toFixed(3)})`
            );
          }
          if (this.inputManager) {
            this.inputManager.handleRawImu(imu);
          }
        }
        break;
      }

      case OPCODES.GYRO_CALIBRATE: {
        const cal = parseGyroCalibrate(payload);
        console.log(
          `[UdpServer] <== RECV [GYRO_CALIBRATE] (Seq: #${sequence}) from "${session.deviceName}" | Command: ${cal.command}`
        );
        if (this.inputManager) {
          this.inputManager.handleGyroCalibrate(cal);
        }
        break;
      }

      case OPCODES.ACTIVE_WINDOW_INFO: {
        const winInfo = parseActiveWindowInfo(payload);
        if (winInfo) {
          console.log(
            `[UdpServer] <== RECV [ACTIVE_WINDOW_INFO] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `Process: "${winInfo.processName}" | Window: "${winInfo.windowTitle}"`
          );
          // Forward to game profile auto-detection system
          this.emit('active_window_detected', {
            processName: winInfo.processName,
            windowTitle: winInfo.windowTitle,
            clientAddress: rinfo.address
          });
        }
        break;
      }

      case OPCODES.GAME_PROFILE_SYNC: {
        const profileSync = parseGameProfileSync(payload);
        if (profileSync) {
          console.log(
            `[UdpServer] <== RECV [GAME_PROFILE_SYNC] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `Profile: "${profileSync.profileId}"`
          );
          this.emit('profile_synced', {
            profileId: profileSync.profileId,
            clientAddress: rinfo.address
          });
        }
        break;
      }

      case OPCODES.PROFILE_CHANGE: {
        const profileChange = parseProfileChange(payload);
        if (profileChange) {
          console.log(
            `[UdpServer] <== RECV [PROFILE_CHANGE] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `Profile: "${profileChange.profileId}"`
          );
          this.emit('profile_changed', {
            profileId: profileChange.profileId,
            clientAddress: rinfo.address
          });
        }
        break;
      }

      case OPCODES.MACRO_TRIGGER: {
        const macro = parseMacroTrigger(payload);
        if (macro) {
          console.log(
            `[UdpServer] <== RECV [MACRO_TRIGGER] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `Macro: "${macro.macroId}" | Timestamp: ${macro.timestamp}`
          );
          this.emit('macro_triggered', {
            macroId: macro.macroId,
            timestamp: macro.timestamp,
            clientAddress: rinfo.address
          });
        }
        break;
      }

      default:
        if (this.logLevel === 'debug') {
          console.log(
            `[UdpServer] <== RECV [${opcodeName}] (Seq: #${sequence}) from "${session.deviceName}" | ` +
            `Payload: ${payload.length} bytes`
          );
        }
        break;
    }
  }

  /**
   * Stops the server and releases the socket and timers.
   */
  stop() {
    if (this.statsTimer) {
      clearInterval(this.statsTimer);
      this.statsTimer = null;
    }
    this.watchdog.stop();
    if (this.socket) {
      try {
        this.socket.close();
      } catch {}
      this.socket = null;
    }
    console.log('[UdpServer] Control server stopped.');
  }
}
