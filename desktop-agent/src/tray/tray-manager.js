import { spawn } from 'node:child_process';
import path from 'node:path';
import os from 'node:os';
import { EventEmitter } from 'node:events';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const TRAY_HELPER_PATH = path.resolve(__dirname, '..', '..', 'scripts', 'tray-helper.ps1');

export class TrayManager extends EventEmitter {
  /**
   * @param {object} options
   * @param {string} [options.pin='------']
   * @param {boolean} [options.enableGamepad=true]
   */
  constructor({ pin = '------', enableGamepad = true } = {}) {
    super();
    this.currentPin = pin;
    this.enableGamepad = enableGamepad;
    this.isConnected = false;
    this.connectedDevice = '';
    this.process = null;
    this.isSupported = os.platform() === 'win32';
    this.isReady = false;
  }

  /**
   * Starts the tray helper process on Windows.
   */
  async start() {
    if (!this.isSupported) {
      this.isReady = true;
      return;
    }

    try {
      this.process = spawn(
        'powershell.exe',
        ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', TRAY_HELPER_PATH],
        {
          stdio: ['pipe', 'pipe', 'pipe'],
          windowsHide: true,
        }
      );

      this.process.stdout.on('data', (data) => {
        const lines = data.toString('utf8').split(/\r?\n/).filter(Boolean);
        for (const line of lines) {
          try {
            const evt = JSON.parse(line.trim());
            this._handleTrayEvent(evt);
          } catch {
            // Non-JSON stdout
          }
        }
      });

      this.process.stderr.on('data', (data) => {
        // Log tray process error if any
      });

      this.process.on('error', (err) => {
        console.warn(`[TrayManager] Tray helper failed to start: ${err.message}`);
        this.process = null;
      });

      this.process.on('exit', () => {
        this.process = null;
        this.isReady = false;
      });

      // Send initial state once ready
      this.updateState({
        pin: this.currentPin,
        connected: this.isConnected,
        deviceName: this.connectedDevice,
        gamepad: this.enableGamepad,
      });

      this.isReady = true;
    } catch (err) {
      console.warn(`[TrayManager] Could not initialize Windows system tray: ${err.message}`);
      this.process = null;
    }
  }

  /**
   * Dispatches events received from the tray helper script.
   * @param {{ event: string, [key: string]: any }} evt
   */
  _handleTrayEvent(evt) {
    switch (evt.event) {
      case 'READY':
        this.isReady = true;
        this.updateState({
          pin: this.currentPin,
          connected: this.isConnected,
          deviceName: this.connectedDevice,
          gamepad: this.enableGamepad,
        });
        break;

      case 'REGENERATE_PIN':
        this.emit('regenerate_pin');
        break;

      case 'TOGGLE_GAMEPAD':
        this.enableGamepad = !this.enableGamepad;
        this.emit('toggle_gamepad', this.enableGamepad);
        this.updateState({ gamepad: this.enableGamepad });
        break;

      case 'DISCONNECT':
        this.emit('disconnect_client');
        break;

      case 'COPY_PIN':
        this._copyPinToClipboard();
        break;

      case 'EXIT':
        this.emit('exit_requested');
        break;

      default:
        break;
    }
  }

  /**
   * Copies the current pairing PIN to the Windows clipboard using PowerShell.
   */
  _copyPinToClipboard() {
    if (this.currentPin && this.currentPin !== '------') {
      try {
        const cp = spawn('powershell.exe', ['-NoProfile', '-Command', `Set-Clipboard -Value '${this.currentPin}'`], {
          windowsHide: true,
        });
        cp.on('exit', () => {
          this.showNotification('AirPad PIN Copied', `PIN ${this.currentPin} copied to clipboard.`);
        });
      } catch {}
    }
  }

  /**
   * Updates the tray menu state (status text, icon color, PIN, gamepad toggle).
   * @param {object} state
   * @param {string} [state.pin]
   * @param {boolean} [state.connected]
   * @param {string} [state.deviceName]
   * @param {boolean} [state.gamepad]
   * @param {string} [state.notifyTitle]
   * @param {string} [state.notifyMsg]
   */
  updateState({ pin, connected, deviceName, gamepad, notifyTitle, notifyMsg } = {}) {
    if (pin !== undefined) this.currentPin = pin;
    if (connected !== undefined) this.isConnected = connected;
    if (deviceName !== undefined) this.connectedDevice = deviceName;
    if (gamepad !== undefined) this.enableGamepad = gamepad;

    if (!this.process || !this.process.stdin.writable) return;

    const payload = JSON.stringify({
      type: 'UPDATE',
      pin: this.currentPin,
      connected: this.isConnected,
      deviceName: this.connectedDevice,
      gamepad: this.enableGamepad,
      notifyTitle,
      notifyMsg,
    });

    try {
      this.process.stdin.write(payload + '\n');
    } catch {}
  }

  /**
   * Displays a balloon tip notification from the tray icon.
   * @param {string} title
   * @param {string} msg
   */
  showNotification(title, msg) {
    this.updateState({ notifyTitle: title, notifyMsg: msg });
  }

  /**
   * Shuts down the tray helper.
   */
  destroy() {
    if (this.process) {
      try {
        this.process.stdin.write(JSON.stringify({ type: 'QUIT' }) + '\n');
        this.process.stdin.end();
      } catch {}
      this.process = null;
    }
  }
}
