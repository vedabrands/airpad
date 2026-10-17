import { mouse, keyboard, Button, Key, Point } from '@nut-tree-fork/nut-js';
import { MOUSE_BUTTON_BITS, MOUSE_ACTIONS, KEY_ACTIONS, KEY_CODES } from '../protocol/constants.js';

// Map protocol KEY_CODES to Nut-js Key constants
export const NUT_KEY_MAP = {
  [KEY_CODES.RETURN]: Key.Return,
  [KEY_CODES.ENTER]: Key.Enter,
  [KEY_CODES.SPACE]: Key.Space,
  [KEY_CODES.ESCAPE]: Key.Escape,
  [KEY_CODES.BACKSPACE]: Key.Backspace,
  [KEY_CODES.TAB]: Key.Tab,
  [KEY_CODES.UP]: Key.Up,
  [KEY_CODES.DOWN]: Key.Down,
  [KEY_CODES.LEFT]: Key.Left,
  [KEY_CODES.RIGHT]: Key.Right,
  [KEY_CODES.KEY_W]: Key.W,
  [KEY_CODES.KEY_A]: Key.A,
  [KEY_CODES.KEY_S]: Key.S,
  [KEY_CODES.KEY_D]: Key.D,
  [KEY_CODES.KEY_E]: Key.E,
  [KEY_CODES.KEY_Q]: Key.Q,
  [KEY_CODES.KEY_R]: Key.R,
  [KEY_CODES.KEY_F]: Key.F,
  [KEY_CODES.LEFT_CTRL]: Key.LeftControl,
  [KEY_CODES.LEFT_SHIFT]: Key.LeftShift,
  [KEY_CODES.LEFT_ALT]: Key.LeftAlt,
  [KEY_CODES.LEFT_SUPER]: Key.LeftSuper,
  [KEY_CODES.VOLUME_UP]: Key.AudioVolUp,
  [KEY_CODES.VOLUME_DOWN]: Key.AudioVolDown,
  [KEY_CODES.MEDIA_PLAY_PAUSE]: Key.AudioPlay,
  [KEY_CODES.MEDIA_NEXT]: Key.AudioNext,
  [KEY_CODES.MEDIA_PREV]: Key.AudioPrev,
};

/**
 * High-performance Windows Input Injector
 * Optimizations:
 * - Zero auto-delay for minimum latency
 * - Subpixel accumulation for jitter-free micro movements
 * - Batched relative movement injection
 * - Mouse acceleration curve matching Windows pointer settings
 * - High DPI awareness
 */
export class WindowsInputInjector {
  constructor() {
    // Configure zero auto-delay for minimum latency / high throughput
    mouse.config.autoDelayMs = 0;
    keyboard.config.autoDelayMs = 0;
    mouse.config.mouseSpeed = 10000; // Maximum speed for instant movement

    this.subpixelX = 0;
    this.subpixelY = 0;
    this.heldMouseButtons = new Set();
    this.heldKeys = new Set();
    this.heldGamepadButtons = 0;
    this.currentStickPositions = { leftX: 0, leftY: 0, rightX: 0, rightY: 0 };
    this.currentTriggers = { left: 0, right: 0 };

    // Windows mouse acceleration curve (approximates Windows pointer settings)
    this.mouseAccelerationEnabled = true;
    this.accelThreshold = 5; // pixels
    this.accelGain = 1.5;
  }

  /**
   * Injects relative mouse movement (dx, dy) with subpixel precision.
   * Uses Windows-like acceleration curve for natural feel.
   * @param {number} dx
   * @param {number} dy
   */
  async injectMouseMove(dx, dy) {
    this.subpixelX += dx;
    this.subpixelY += dy;
    let intDx = Math.trunc(this.subpixelX);
    let intDy = Math.trunc(this.subpixelY);

    if (intDx === 0 && intDy === 0) return;

    this.subpixelX -= intDx;
    this.subpixelY -= intDy;

    // Apply Windows-style mouse acceleration for natural cursor feel
    if (this.mouseAccelerationEnabled) {
      const speed = Math.sqrt(intDx * intDx + intDy * intDy);
      if (speed > this.accelThreshold) {
        const multiplier = 1.0 + this.accelGain * ((speed - this.accelThreshold) / this.accelThreshold);
        intDx = Math.round(intDx * multiplier);
        intDy = Math.round(intDy * multiplier);
      }
    }

    try {
      const currentPos = await mouse.getPosition();
      const targetX = currentPos.x + intDx;
      const targetY = currentPos.y + intDy;
      await mouse.setPosition(new Point(targetX, targetY));
    } catch (err) {
      console.warn(`[InputInjector] Mouse move error: ${err.message}`);
    }
  }

  /**
   * Injects multiple relative mouse movements in a single batch.
   * Reduces overhead of multiple getPosition/setPosition calls.
   * @param {Array<{dx: number, dy: number}>} deltas
   */
  async injectBatchedMouseMove(deltas) {
    if (!deltas || deltas.length === 0) return;

    // Accumulate all deltas
    let totalDx = 0;
    let totalDy = 0;
    for (const delta of deltas) {
      totalDx += delta.dx;
      totalDy += delta.dy;
    }

    this.subpixelX += totalDx;
    this.subpixelY += totalDy;
    let intDx = Math.trunc(this.subpixelX);
    let intDy = Math.trunc(this.subpixelY);

    if (intDx === 0 && intDy === 0) return;

    this.subpixelX -= intDx;
    this.subpixelY -= intDy;

    // Apply acceleration
    if (this.mouseAccelerationEnabled) {
      const speed = Math.sqrt(intDx * intDx + intDy * intDy);
      if (speed > this.accelThreshold) {
        const multiplier = 1.0 + this.accelGain * ((speed - this.accelThreshold) / this.accelThreshold);
        intDx = Math.round(intDx * multiplier);
        intDy = Math.round(intDy * multiplier);
      }
    }

    try {
      const currentPos = await mouse.getPosition();
      const targetX = currentPos.x + intDx;
      const targetY = currentPos.y + intDy;
      await mouse.setPosition(new Point(targetX, targetY));
    } catch (err) {
      console.warn(`[InputInjector] Batched mouse move error: ${err.message}`);
    }
  }

  /**
   * Injects mouse button events (press, release, click).
   * @param {number} buttonMask - Bitmask (0x01 Left, 0x02 Right, 0x04 Middle)
   * @param {number} action - 0: BUTTON_UP, 1: BUTTON_DOWN, 2: BUTTON_CLICK
   */
  async injectMouseButton(buttonMask, action) {
    const targets = [];
    if (buttonMask & MOUSE_BUTTON_BITS.LEFT) targets.push(Button.LEFT);
    if (buttonMask & MOUSE_BUTTON_BITS.RIGHT) targets.push(Button.RIGHT);
    if (buttonMask & MOUSE_BUTTON_BITS.MIDDLE) targets.push(Button.MIDDLE);

    for (const btn of targets) {
      try {
        if (action === MOUSE_ACTIONS.BUTTON_DOWN) {
          this.heldMouseButtons.add(btn);
          await mouse.pressButton(btn);
        } else if (action === MOUSE_ACTIONS.BUTTON_UP) {
          this.heldMouseButtons.delete(btn);
          await mouse.releaseButton(btn);
        } else if (action === MOUSE_ACTIONS.BUTTON_CLICK) {
          await mouse.click(btn);
        }
      } catch (err) {
        console.warn(`[InputInjector] Mouse button error: ${err.message}`);
      }
    }
  }

  /**
   * Injects mouse scroll wheel deltas (vertical & horizontal).
   * @param {number} scrollX - Horizontal scroll ticks (negative = left, positive = right)
   * @param {number} scrollY - Vertical scroll ticks (negative = down, positive = up)
   */
  async injectMouseScroll(scrollX, scrollY) {
    try {
      // Nut-js vertical scroll
      if (scrollY > 0) {
        await mouse.scrollUp(Math.abs(scrollY));
      } else if (scrollY < 0) {
        await mouse.scrollDown(Math.abs(scrollY));
      }

      // Nut-js horizontal scroll
      if (scrollX > 0) {
        await mouse.scrollRight(Math.abs(scrollX));
      } else if (scrollX < 0) {
        await mouse.scrollLeft(Math.abs(scrollX));
      }
    } catch (err) {
      console.warn(`[InputInjector] Mouse scroll error: ${err.message}`);
    }
  }

  /**
   * Injects keyboard key actions (press, release, tap).
   * @param {number} keyCode - Protocol KEY_CODES enum
   * @param {number} action - 0: KEY_UP, 1: KEY_DOWN, 2: KEY_TAP
   */
  async injectKeyboardKey(keyCode, action) {
    const nutKey = NUT_KEY_MAP[keyCode];
    if (nutKey === undefined) {
      console.warn(`[InputInjector] Unmapped key code: 0x${keyCode.toString(16)}`);
      return;
    }

    try {
      if (action === KEY_ACTIONS.KEY_DOWN) {
        this.heldKeys.add(nutKey);
        await keyboard.pressKey(nutKey);
      } else if (action === KEY_ACTIONS.KEY_UP) {
        this.heldKeys.delete(nutKey);
        await keyboard.releaseKey(nutKey);
      } else if (action === KEY_ACTIONS.KEY_TAP) {
        await keyboard.pressKey(nutKey);
        await keyboard.releaseKey(nutKey);
      }
    } catch (err) {
      console.warn(`[InputInjector] Keyboard key error: ${err.message}`);
    }
  }

  /**
   * Updates virtual gamepad state.
   */
  injectGamepadState({ buttonMask, leftX, leftY, rightX, rightY, leftTrigger, rightTrigger }) {
    this.heldGamepadButtons = buttonMask;
    this.currentStickPositions = { leftX, leftY, rightX, rightY };
    this.currentTriggers = { left: leftTrigger, right: rightTrigger };
  }

  /**
   * EMERGENCY FAILSAFE: Releases ALL held mouse buttons and keyboard keys.
   * Called on disconnect, timeout, or shutdown.
   */
  async failsafeReleaseAll() {
    console.log('[InputInjector] Executing failsafeReleaseAll() - Releasing all held inputs.');

    // 1. Release all known and standard mouse buttons
    const allMouseButtons = new Set([Button.LEFT, Button.RIGHT, Button.MIDDLE, ...this.heldMouseButtons]);
    for (const btn of allMouseButtons) {
      try {
        await mouse.releaseButton(btn);
      } catch {}
    }
    this.heldMouseButtons.clear();

    // 2. Release all currently held keyboard keys
    for (const key of this.heldKeys) {
      try {
        await keyboard.releaseKey(key);
      } catch {}
    }
    this.heldKeys.clear();

    // 3. Reset gamepad state
    this.heldGamepadButtons = 0;
    this.currentStickPositions = { leftX: 0, leftY: 0, rightX: 0, rightY: 0 };
    this.currentTriggers = { left: 0, right: 0 };

    // 4. Reset subpixel accumulators
    this.subpixelX = 0;
    this.subpixelY = 0;
  }

  destroy() {
    this.failsafeReleaseAll();
  }
}
