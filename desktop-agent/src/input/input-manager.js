import { GyroProcessor } from './gyro-processor.js';
import { WindowsInputInjector } from './windows-injector.js';
import { createGamepadProvider } from './gamepad/index.js';
import { WindowDetector } from './window-detector.js';

export class InputManager {
  /**
   * @param {object} [options]
   * @param {object} [options.gamepadProvider] Optional pre-created gamepad provider instance
   * @param {boolean} [options.enableGamepad=true]
   * @param {boolean} [options.enableWindowDetection=true]
   */
  constructor(options = {}) {
    this.injector = new WindowsInputInjector();
    this.gyroProcessor = new GyroProcessor();
    this.gamepad = options.gamepadProvider || null;
    this.enableGamepad = options.enableGamepad !== false;
    this.isGamepadReady = !!this.gamepad;

    // Window / game detection
    this.enableWindowDetection = options.enableWindowDetection !== false;
    this.windowDetector = new WindowDetector();
    this.activeWindowInfo = { processName: null, windowTitle: null };
    this.windowPollingTimer = null;

    if (!this.gamepad && this.enableGamepad) {
      this._initGamepad(options);
    }
  }

  async _initGamepad(options) {
    try {
      this.gamepad = await createGamepadProvider(options);
      this.isGamepadReady = true;
    } catch (err) {
      console.warn(`[InputManager] Gamepad provider initialization failed: ${err.message}`);
    }
  }

  /**
   * Dynamically toggles virtual gamepad emulation.
   * @param {boolean} enabled
   */
  async setGamepadEnabled(enabled) {
    this.enableGamepad = enabled;
    if (!enabled && this.gamepad) {
      this.gamepad.failsafeReset();
    } else if (enabled && !this.gamepad) {
      await this._initGamepad({});
    }
  }

  /**
   * Start polling for active window changes.
   * @param {function} onWindowChange - Called with { processName, windowTitle } when the active window changes.
   * @param {number} [intervalMs=500]
   */
  startWindowPolling(onWindowChange, intervalMs = 500) {
    if (!this.enableWindowDetection) return;
    if (this.windowPollingTimer) return;

    this.windowPollingTimer = setInterval(() => {
      const info = this.windowDetector.getActiveWindowInfo();
      if (info) {
        this.activeWindowInfo = info;
        onWindowChange(info);
      }
    }, intervalMs);
  }

  /**
   * Stop polling for active window changes.
   */
  stopWindowPolling() {
    if (this.windowPollingTimer) {
      clearInterval(this.windowPollingTimer);
      this.windowPollingTimer = null;
    }
  }

  /**
   * Get the most recently detected active window info.
   */
  getActiveWindowInfo() {
    return { ...this.activeWindowInfo };
  }

  handleMouseDelta({ dx, dy }) {
    this.injector.injectMouseMove(dx, dy);
  }

  handleBatchedMouseDeltas(deltas) {
    if (deltas && deltas.length > 0) {
      this.injector.injectBatchedMouseMove(deltas);
    }
  }

  handleMouseButton({ buttonMask, action }) {
    this.injector.injectMouseButton(buttonMask, action);
  }

  handleMouseScroll({ scrollX, scrollY }) {
    this.injector.injectMouseScroll(scrollX, scrollY);
  }

  handleKeyboardKey({ keyCode, action }) {
    this.injector.injectKeyboardKey(keyCode, action);
  }

  handleMouseCombo({ dx, dy, buttonState, scrollX, scrollY }) {
    if (dx !== 0 || dy !== 0) {
      this.injector.injectMouseMove(dx, dy);
    }
    if (scrollY !== 0 || scrollX !== 0) {
      this.injector.injectMouseScroll(scrollX, scrollY * 120);
    }
  }

  handleModeSwitch(config) {
    console.log(
      `[InputManager] Gyro Mode switched to: ${config.gyroMode} ` +
      `(X-Sens: ${config.sensitivityX}, Y-Sens: ${config.sensitivityY})`
    );
    this.gyroProcessor.setModeConfig(config);
  }

  handleRawImu(imu) {
    const result = this.gyroProcessor.processImu(imu);
    if (result.type === 'mouse' && (result.dx !== 0 || result.dy !== 0)) {
      // Coexistence: Gyro aims mouse while sticks simultaneously drive player movement
      this.injector.injectMouseMove(result.dx, result.dy);
    } else if (result.type === 'stick') {
      // Gyro drives virtual right stick (look/aim)
      if (this.enableGamepad && this.gamepad) {
        this.gamepad.updateStick(1, result.stickX, result.stickY);
      }
    }
  }

  handleGamepadState(state) {
    if (this.enableGamepad && this.gamepad) {
      this.gamepad.updateState(state);
    }
  }

  handleGamepadStick(stick) {
    if (this.enableGamepad && this.gamepad) {
      this.gamepad.updateStick(stick.stickId, stick.posX, stick.posY);
    }
  }

  handleGamepadButton(button) {
    if (this.enableGamepad && this.gamepad) {
      this.gamepad.updateButton(button.buttonBit, button.action);
    }
  }

  handleGamepadTrigger(trigger) {
    if (this.enableGamepad && this.gamepad) {
      this.gamepad.updateTrigger(trigger.triggerId, trigger.value);
    }
  }

  handleGyroCalibrate(cal) {
    // 0x01: Start calibration / Zero bias
    this.gyroProcessor.calibrateBias(cal.samples || 50);
  }

  failsafeReleaseAll() {
    this.gyroProcessor.resetCalibration();
    this.injector.failsafeReleaseAll();
    if (this.gamepad) {
      this.gamepad.failsafeReset();
    }
  }

  destroy() {
    this.stopWindowPolling();
    this.failsafeReleaseAll();
    this.injector.destroy();
    if (this.gamepad) {
      this.gamepad.destroy();
    }
  }
}
