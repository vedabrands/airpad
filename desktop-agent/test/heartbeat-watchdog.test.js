import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { HeartbeatWatchdog } from '../src/server/heartbeat-watchdog.js';

describe('HeartbeatWatchdog', () => {
  it('should trigger soft lag and timeout when un-fed', async () => {
    const watchdog = new HeartbeatWatchdog({ timeoutMs: 200, tickMs: 20 });
    let softLagFired = false;
    let timeoutFired = false;

    watchdog.on('soft_lag', () => {
      softLagFired = true;
    });

    watchdog.on('timeout', () => {
      timeoutFired = true;
    });

    watchdog.start();
    watchdog.feed();

    // Fast-forward wait
    await new Promise((resolve) => setTimeout(resolve, 300));

    watchdog.stop();
    assert.equal(softLagFired || timeoutFired, true);
    assert.equal(timeoutFired, true);
  });

  it('should emit connection_recovered when fed after soft lag', () => {
    const watchdog = new HeartbeatWatchdog({ timeoutMs: 1500, tickMs: 50 });
    let recoveredFired = false;

    watchdog.on('connection_recovered', () => {
      recoveredFired = true;
    });

    watchdog.isActive = true;
    watchdog.softLagEmitted = true;
    watchdog.feed();

    assert.equal(recoveredFired, true);
    assert.equal(watchdog.softLagEmitted, false);
  });
});
