import { EventEmitter } from 'node:events';

export class HeartbeatWatchdog extends EventEmitter {
  /**
   * @param {object} options
   * @param {number} [options.timeoutMs=1500]
   * @param {number} [options.tickMs=100]
   */
  constructor({ timeoutMs = 1500, tickMs = 100 } = {}) {
    super();
    this.timeoutMs = timeoutMs;
    this.tickMs = tickMs;
    this.timer = null;
    this.lastPacketTime = 0;
    this.isActive = false;
    this.softLagEmitted = false;
  }

  start() {
    if (this.timer) clearInterval(this.timer);
    this.timer = setInterval(() => this._checkWatchdog(), this.tickMs);
  }

  stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.isActive = false;
    this.softLagEmitted = false;
  }

  /**
   * Called whenever ANY valid authenticated packet arrives.
   */
  feed() {
    this.lastPacketTime = Date.now();
    this.isActive = true;
    if (this.softLagEmitted) {
      this.softLagEmitted = false;
      this.emit('connection_recovered');
    }
  }

  _checkWatchdog() {
    if (!this.isActive || this.lastPacketTime === 0) return;

    const elapsed = Date.now() - this.lastPacketTime;

    if (elapsed > 500 && !this.softLagEmitted && elapsed < this.timeoutMs) {
      this.softLagEmitted = true;
      this.emit('soft_lag', { elapsedMs: elapsed });
    }

    if (elapsed >= this.timeoutMs) {
      console.warn(`[Watchdog] Timeout reached (${elapsed}ms without packet). Triggering failsafe input release!`);
      this.isActive = false;
      this.softLagEmitted = false;
      this.emit('timeout', { elapsedMs: elapsed });
    }
  }
}
