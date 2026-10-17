import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { GyroProcessor } from '../src/input/gyro-processor.js';
import { GYRO_MODES } from '../src/protocol/constants.js';

describe('GyroProcessor', () => {
  it('should return none when mode is OFF', () => {
    const gp = new GyroProcessor({ mode: GYRO_MODES.OFF });
    const result = gp.processImu({ gyroX: 1.0, gyroY: 0, gyroZ: 1.0 });
    assert.deepEqual(result, { type: 'none' });
  });

  it('should respect ratchet clutch in GYRO_TO_MOUSE mode', () => {
    const gp = new GyroProcessor({ mode: GYRO_MODES.GYRO_TO_MOUSE });
    gp.ratchetActive = true;
    gp.setRatchetHeld(false);

    // Ratchet not held -> none
    let res = gp.processImu({ gyroX: 0.5, gyroY: 0, gyroZ: 0.5 });
    assert.deepEqual(res, { type: 'none' });

    // Ratchet held -> active mouse motion
    gp.setRatchetHeld(true);
    res = gp.processImu({ gyroX: 0.5, gyroY: 0, gyroZ: 0.5 });
    assert.equal(res.type, 'mouse');
    assert.ok(typeof res.dx === 'number');
    assert.ok(typeof res.dy === 'number');
  });

  it('should correctly process GYRO_TO_STICK with deadzone and EMA smoothing', () => {
    const gp = new GyroProcessor({
      mode: GYRO_MODES.GYRO_TO_STICK,
      sensitivityX: 1.0,
      sensitivityY: 1.0,
      deadzone: 0.1,
      smoothing: 0.5,
    });

    // Below deadzone -> returns 0
    const deadRes = gp.processImu({ gyroX: 0.05, gyroY: 0, gyroZ: 0.05 });
    assert.deepEqual(deadRes, { type: 'stick', stickX: 0, stickY: 0 });

    // Above deadzone -> returns stick coordinates
    const moveRes = gp.processImu({ gyroX: 0.5, gyroY: 0, gyroZ: 0.5 });
    assert.equal(moveRes.type, 'stick');
    assert.ok(moveRes.stickX > 0);
    assert.ok(moveRes.stickY > 0);
  });

  it('should handle setModeConfig and resetCalibration', () => {
    const gp = new GyroProcessor();
    gp.setModeConfig({
      gyroMode: GYRO_MODES.GYRO_TO_MOUSE,
      invertX: true,
      invertY: true,
      ratchetActive: false,
      sensitivityX: 2.0,
      sensitivityY: 3.0,
      deadzone: 0.05,
      smoothingFactor: 0.4,
      resetBias: true,
    });

    assert.equal(gp.mode, GYRO_MODES.GYRO_TO_MOUSE);
    assert.equal(gp.invertX, true);
    assert.equal(gp.invertY, true);
    assert.equal(gp.sensitivityX, 2.0);
    assert.equal(gp.sensitivityY, 3.0);
    assert.equal(gp.deadzone, 0.05);
    assert.equal(gp.smoothing, 0.4);

    gp.calibrateBias(50);
    assert.equal(gp.biasX, 0);
    assert.equal(gp.biasY, 0);
    assert.equal(gp.biasZ, 0);
  });
});
