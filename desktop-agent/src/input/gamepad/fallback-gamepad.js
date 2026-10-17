import { keyboard, Key } from '@nut-tree-fork/nut-js';
import { GAMEPAD_BUTTON_BITS } from '../../protocol/constants.js';

/**
 * Fallback Gamepad Provider for Non-Windows Platforms (Linux, macOS, BSD).
 *
 * Emulates gamepad controls by mapping analog stick axes and controller buttons
 * directly into keyboard keystrokes (WASD movement, Arrow camera cluster, Space/Escape/Action keys).
 */
export class FallbackGamepadProvider {
  /**
   * @param {object} [options]
   * @param {number} [options.threshold=10000] Deadzone threshold for stick-to-key mapping
   */
  constructor({ threshold = 10000 } = {}) {
    this.threshold = threshold;
    this.heldKeys = new Set();
    this.stickPositions = { leftX: 0, leftY: 0, rightX: 0, rightY: 0 };
    this.heldButtons = 0;
    this.triggers = { left: 0, right: 0 };

    console.log(
      '======================================================================\n' +
      '[FallbackGamepad] NON-WINDOWS GAMEPAD EMULATION ACTIVE\n' +
      '  - Left Stick  -> WASD (Movement)\n' +
      '  - Right Stick -> Arrow Keys (Look / Aim)\n' +
      '  - A / B / X / Y -> Space / Escape / E / R\n' +
      '  - LB / RB     -> Left Shift / Left Ctrl\n' +
      '  - Start / Back -> Enter / Backspace\n' +
      '======================================================================'
    );
  }

  async start() {
    // No subprocess required for fallback
    return Promise.resolve();
  }

  /**
   * Helper to safely press/release a nut-js key.
   * @param {Key} key
   * @param {boolean} press
   */
  async _setKeyState(key, press) {
    if (press) {
      if (!this.heldKeys.has(key)) {
        this.heldKeys.add(key);
        try {
          await keyboard.pressKey(key);
        } catch (err) {}
      }
    } else {
      if (this.heldKeys.has(key)) {
        this.heldKeys.delete(key);
        try {
          await keyboard.releaseKey(key);
        } catch (err) {}
      }
    }
  }

  /**
   * Updates full gamepad state.
   */
  async updateState({
    buttonMask = 0,
    leftX = 0,
    leftY = 0,
    rightX = 0,
    rightY = 0,
    leftTrigger = 0,
    rightTrigger = 0,
  } = {}) {
    this.updateStick(0, leftX, leftY);
    this.updateStick(1, rightX, rightY);
    this.updateTrigger(0, leftTrigger);
    this.updateTrigger(1, rightTrigger);

    // Map button mask diffs
    const diff = this.heldButtons ^ buttonMask;
    if (diff) {
      for (const [bitName, bitVal] of Object.entries(GAMEPAD_BUTTON_BITS)) {
        if (diff & bitVal) {
          const isPressed = (buttonMask & bitVal) !== 0;
          this._handleButtonBit(bitVal, isPressed ? 1 : 0);
        }
      }
      this.heldButtons = buttonMask;
    }
  }

  /**
   * Maps analog stick coordinates to WASD and Arrow Keys.
   * @param {number} stickId 0 = Left (WASD), 1 = Right (Arrows)
   * @param {number} posX [-32768, 32767]
   * @param {number} posY [-32768, 32767]
   */
  async updateStick(stickId, posX, posY) {
    if (stickId === 0) {
      this.stickPositions.leftX = posX;
      this.stickPositions.leftY = posY;

      // Left Stick -> WASD (Y+ = Up/W, Y- = Down/S, X- = Left/A, X+ = Right/D)
      await this._setKeyState(Key.W, posY > this.threshold);
      await this._setKeyState(Key.S, posY < -this.threshold);
      await this._setKeyState(Key.A, posX < -this.threshold);
      await this._setKeyState(Key.D, posX > this.threshold);
    } else {
      this.stickPositions.rightX = posX;
      this.stickPositions.rightY = posY;

      // Right Stick -> Arrow Keys
      await this._setKeyState(Key.Up, posY > this.threshold);
      await this._setKeyState(Key.Down, posY < -this.threshold);
      await this._setKeyState(Key.Left, posX < -this.threshold);
      await this._setKeyState(Key.Right, posX > this.threshold);
    }
  }

  /**
   * Maps individual button actions to keyboard keys.
   * @param {number} buttonBit
   * @param {number} action 1 = Press, 0 = Release
   */
  async updateButton(buttonBit, action) {
    if (action === 1) {
      this.heldButtons |= buttonBit;
    } else {
      this.heldButtons &= ~buttonBit;
    }
    await this._handleButtonBit(buttonBit, action);
  }

  async _handleButtonBit(buttonBit, action) {
    const isDown = action === 1;
    switch (buttonBit) {
      case GAMEPAD_BUTTON_BITS.A:
        await this._setKeyState(Key.Space, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.B:
        await this._setKeyState(Key.Escape, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.X:
        await this._setKeyState(Key.E, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.Y:
        await this._setKeyState(Key.R, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.LB:
        await this._setKeyState(Key.LeftShift, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.RB:
        await this._setKeyState(Key.LeftControl, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.LS:
        await this._setKeyState(Key.LeftAlt, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.RS:
        await this._setKeyState(Key.Tab, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.START:
        await this._setKeyState(Key.Return, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.BACK:
        await this._setKeyState(Key.Backspace, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.DPAD_UP:
        await this._setKeyState(Key.Up, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.DPAD_DOWN:
        await this._setKeyState(Key.Down, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.DPAD_LEFT:
        await this._setKeyState(Key.Left, isDown);
        break;
      case GAMEPAD_BUTTON_BITS.DPAD_RIGHT:
        await this._setKeyState(Key.Right, isDown);
        break;
      default:
        break;
    }
  }

  /**
   * Maps triggers to modifier keys or action keys.
   * @param {number} triggerId 0 = Left Trigger, 1 = Right Trigger
   * @param {number} value [0, 255]
   */
  async updateTrigger(triggerId, value) {
    const isEngaged = value > 128;
    if (triggerId === 0) {
      this.triggers.left = value;
      await this._setKeyState(Key.LeftShift, isEngaged);
    } else {
      this.triggers.right = value;
      await this._setKeyState(Key.Space, isEngaged);
    }
  }

  /**
   * Releases all emulated keys.
   */
  async failsafeReset() {
    for (const key of this.heldKeys) {
      try {
        await keyboard.releaseKey(key);
      } catch {}
    }
    this.heldKeys.clear();
    this.heldButtons = 0;
    this.stickPositions = { leftX: 0, leftY: 0, rightX: 0, rightY: 0 };
    this.triggers = { left: 0, right: 0 };
  }

  destroy() {
    this.failsafeReset();
  }
}
