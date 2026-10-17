import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SCRIPT_PATH = path.resolve(__dirname, '../../../scripts/gamepad-bridge.py');

export class WindowsGamepadProvider {
  /**
   * @param {object} [options]
   * @param {string} [options.pythonExecutable] Custom python path
   */
  constructor({ pythonExecutable = process.env.PYTHON_PATH || 'python' } = {}) {
    this.pythonExecutable = pythonExecutable;
    this.process = null;
    this.isReady = false;
    this.isDestroyed = false;
    this.heldButtons = 0;
    this.stickPositions = { leftX: 0, leftY: 0, rightX: 0, rightY: 0 };
    this.triggers = { left: 0, right: 0 };
  }

  /**
   * Spawns the Python ViGEm helper bridge and awaits the READY handshake.
   * @returns {Promise<void>}
   */
  async start() {
    if (this.isReady) return;

    return new Promise((resolve, reject) => {
      let resolved = false;

      try {
        this.process = spawn(this.pythonExecutable, [SCRIPT_PATH], {
          stdio: ['pipe', 'pipe', 'pipe'],
          windowsHide: true,
        });
      } catch (err) {
        return reject(new Error(`Failed to spawn Python gamepad bridge: ${err.message}`));
      }

      this.process.stdout.on('data', (chunk) => {
        const text = chunk.toString();
        if (text.includes('READY') && !resolved) {
          this.isReady = true;
          resolved = true;
          console.log('[WindowsGamepad] ViGEm Virtual Xbox 360 controller initialized.');
          resolve();
        }
      });

      this.process.stderr.on('data', (chunk) => {
        console.warn(`[WindowsGamepad stderr]: ${chunk.toString().trim()}`);
      });

      this.process.on('error', (err) => {
        if (!resolved) {
          resolved = true;
          reject(err);
        } else {
          console.error(`[WindowsGamepad] Child process error: ${err.message}`);
        }
      });

      this.process.on('exit', (code, signal) => {
        this.isReady = false;
        if (!resolved) {
          resolved = true;
          reject(new Error(`Gamepad bridge exited prematurely with code ${code} (${signal})`));
        } else if (!this.isDestroyed) {
          console.warn(`[WindowsGamepad] Bridge process exited (code: ${code}, signal: ${signal}).`);
        }
      });

      // 5-second initialization timeout
      setTimeout(() => {
        if (!resolved) {
          resolved = true;
          reject(new Error('Timed out waiting for Python gamepad bridge READY response.'));
        }
      }, 5000);
    });
  }

  /**
   * Helper to write a command to stdin.
   * @param {string} cmd
   */
  _sendCommand(cmd) {
    if (!this.process || !this.isReady || this.isDestroyed) return;
    try {
      this.process.stdin.write(cmd + '\n');
    } catch (err) {
      console.warn(`[WindowsGamepad] Write error: ${err.message}`);
    }
  }

  /**
   * Updates full gamepad state.
   * @param {object} state
   */
  updateState({
    buttonMask = 0,
    leftX = 0,
    leftY = 0,
    rightX = 0,
    rightY = 0,
    leftTrigger = 0,
    rightTrigger = 0,
  } = {}) {
    this.heldButtons = buttonMask;
    this.stickPositions = { leftX, leftY, rightX, rightY };
    this.triggers = { left: leftTrigger, right: rightTrigger };
    this._sendCommand(`S ${buttonMask} ${leftX} ${leftY} ${rightX} ${rightY} ${leftTrigger} ${rightTrigger}`);
  }

  /**
   * Updates analog stick position.
   * @param {number} stickId 0 = Left, 1 = Right
   * @param {number} posX [-32768, 32767]
   * @param {number} posY [-32768, 32767]
   */
  updateStick(stickId, posX, posY) {
    if (stickId === 0) {
      this.stickPositions.leftX = posX;
      this.stickPositions.leftY = posY;
    } else {
      this.stickPositions.rightX = posX;
      this.stickPositions.rightY = posY;
    }
    this._sendCommand(`J ${stickId} ${posX} ${posY}`);
  }

  /**
   * Updates single button action.
   * @param {number} buttonBit GAMEPAD_BUTTON_BITS constant
   * @param {number} action 1 = Press, 0 = Release
   */
  updateButton(buttonBit, action) {
    if (action === 1) {
      this.heldButtons |= buttonBit;
    } else {
      this.heldButtons &= ~buttonBit;
    }
    this._sendCommand(`B ${buttonBit} ${action}`);
  }

  /**
   * Updates trigger value.
   * @param {number} triggerId 0 = Left, 1 = Right
   * @param {number} value [0, 255]
   */
  updateTrigger(triggerId, value) {
    if (triggerId === 0) {
      this.triggers.left = value;
    } else {
      this.triggers.right = value;
    }
    this._sendCommand(`T ${triggerId} ${value}`);
  }

  /**
   * Resets all buttons, triggers, and sticks to neutral.
   */
  failsafeReset() {
    this.heldButtons = 0;
    this.stickPositions = { leftX: 0, leftY: 0, rightX: 0, rightY: 0 };
    this.triggers = { left: 0, right: 0 };
    this._sendCommand('R');
  }

  /**
   * Closes child process and releases virtual controller.
   */
  destroy() {
    if (this.isDestroyed) return;
    this.isDestroyed = true;
    this.failsafeReset();
    this._sendCommand('Q');
    if (this.process) {
      setTimeout(() => {
        try {
          this.process.kill();
        } catch {}
      }, 500);
    }
  }
}
