import { GYRO_MODES } from '../protocol/constants.js';

export class GyroProcessor {
  /**
   * @param {object} [options]
   * @param {number} [options.mode=GYRO_MODES.OFF]
   * @param {number} [options.sensitivityX=1.2]
   * @param {number} [options.sensitivityY=1.2]
   * @param {number} [options.deadzone=0.02]
   * @param {number} [options.smoothing=0.25]
   */
  constructor({
    mode = GYRO_MODES.OFF,
    sensitivityX = 1.2,
    sensitivityY = 1.2,
    deadzone = 0.02,
    smoothing = 0.25,
  } = {}) {
    this.mode = mode;
    this.sensitivityX = sensitivityX;
    this.sensitivityY = sensitivityY;
    this.deadzone = deadzone;
    this.smoothing = smoothing;
    this.invertX = false;
    this.invertY = false;
    this.ratchetActive = false;
    this.isRatchetHeld = false;

    // Filter state
    this.prevStickX = 0;
    this.prevStickY = 0;
    this.subpixelX = 0;
    this.subpixelY = 0;

    // Calibration bias offsets
    this.biasX = 0;
    this.biasY = 0;
    this.biasZ = 0;
  }

  setModeConfig(config) {
    this.mode = config.gyroMode;
    this.invertX = config.invertX;
    this.invertY = config.invertY;
    this.ratchetActive = config.ratchetActive;
    if (config.sensitivityX) this.sensitivityX = config.sensitivityX;
    if (config.sensitivityY) this.sensitivityY = config.sensitivityY;
    if (config.deadzone !== undefined) this.deadzone = config.deadzone;
    if (config.smoothingFactor !== undefined) this.smoothing = config.smoothingFactor;

    if (config.resetBias) {
      this.resetCalibration();
    }
  }

  setRatchetHeld(isHeld) {
    this.isRatchetHeld = isHeld;
  }

  resetCalibration() {
    this.biasX = 0;
    this.biasY = 0;
    this.biasZ = 0;
    this.prevStickX = 0;
    this.prevStickY = 0;
    this.subpixelX = 0;
    this.subpixelY = 0;
  }

  /**
   * Calibrates zero bias offsets.
   * @param {number} [_samples=50]
   */
  calibrateBias(_samples = 50) {
    this.resetCalibration();
  }

  /**
   * Processes raw IMU angular velocity (rad/s).
   * @param {object} imu
   * @param {number} imu.gyroX Pitch velocity (rad/s)
   * @param {number} imu.gyroY Roll velocity (rad/s)
   * @param {number} imu.gyroZ Yaw velocity (rad/s)
   * @returns {{ type: 'mouse'|'stick'|'none', dx?: number, dy?: number, stickX?: number, stickY?: number }}
   */
  processImu(imu) {
    if (this.mode === GYRO_MODES.OFF) {
      return { type: 'none' };
    }

    // Check ratchet clutch
    if (this.ratchetActive && !this.isRatchetHeld) {
      return { type: 'none' };
    }

    // Subtract calibration bias
    let pitch = imu.gyroX - this.biasX;
    let yaw = imu.gyroZ - this.biasZ;

    // Apply deadzone
    pitch = this._applyDeadzone(pitch, this.deadzone);
    yaw = this._applyDeadzone(yaw, this.deadzone);

    if (pitch === 0 && yaw === 0 && this.mode === GYRO_MODES.GYRO_TO_STICK) {
      // Return stick back to neutral if no motion
      this.prevStickX = 0;
      this.prevStickY = 0;
      return { type: 'stick', stickX: 0, stickY: 0 };
    }

    if (this.mode === GYRO_MODES.GYRO_TO_MOUSE) {
      // Map yaw to horizontal cursor delta dx, pitch to vertical cursor delta dy
      const dirX = this.invertX ? 1 : -1;
      const dirY = this.invertY ? -1 : 1;

      const rawDx = yaw * this.sensitivityX * 25.0 * dirX;
      const rawDy = pitch * this.sensitivityY * 25.0 * dirY;

      // Subpixel accumulation for jitter-free micro movements
      this.subpixelX += rawDx;
      this.subpixelY += rawDy;

      const intDx = Math.trunc(this.subpixelX);
      const intDy = Math.trunc(this.subpixelY);

      this.subpixelX -= intDx;
      this.subpixelY -= intDy;

      return {
        type: 'mouse',
        dx: intDx,
        dy: intDy,
      };
    }

    if (this.mode === GYRO_MODES.GYRO_TO_STICK) {
      // Map to Right Stick [-32768, 32767]
      const dirX = this.invertX ? -1 : 1;
      const dirY = this.invertY ? -1 : 1;

      const targetX = Math.max(-32768, Math.min(32767, yaw * this.sensitivityX * 8000.0 * dirX));
      const targetY = Math.max(-32768, Math.min(32767, pitch * this.sensitivityY * 8000.0 * dirY));

      // EMA smoothing
      const smoothX = Math.round(this.smoothing * this.prevStickX + (1 - this.smoothing) * targetX);
      const smoothY = Math.round(this.smoothing * this.prevStickY + (1 - this.smoothing) * targetY);

      this.prevStickX = smoothX;
      this.prevStickY = smoothY;

      return {
        type: 'stick',
        stickX: smoothX,
        stickY: smoothY,
      };
    }

    return { type: 'none' };
  }

  _applyDeadzone(val, deadzone) {
    const abs = Math.abs(val);
    if (abs < deadzone) return 0;
    return Math.sign(val) * (abs - deadzone);
  }
}
