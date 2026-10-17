import test from 'node:test';
import assert from 'node:assert/strict';
import { GAMEPAD_BUTTON_BITS, GYRO_MODES } from '../src/protocol/constants.js';
import { WindowsGamepadProvider } from '../src/input/gamepad/windows-gamepad.js';
import { FallbackGamepadProvider } from '../src/input/gamepad/fallback-gamepad.js';
import { InputManager } from '../src/input/input-manager.js';

test('WindowsGamepadProvider Initialization and Command Formatting', async () => {
  const pad = new WindowsGamepadProvider();
  await pad.start();
  assert.equal(pad.isReady, true);

  pad.updateStick(0, 16384, -16384);
  assert.equal(pad.stickPositions.leftX, 16384);
  assert.equal(pad.stickPositions.leftY, -16384);

  pad.updateStick(1, -8192, 8192);
  assert.equal(pad.stickPositions.rightX, -8192);
  assert.equal(pad.stickPositions.rightY, 8192);

  pad.updateButton(GAMEPAD_BUTTON_BITS.A, 1);
  assert.equal((pad.heldButtons & GAMEPAD_BUTTON_BITS.A) !== 0, true);

  pad.updateButton(GAMEPAD_BUTTON_BITS.A, 0);
  assert.equal((pad.heldButtons & GAMEPAD_BUTTON_BITS.A) === 0, true);

  pad.updateTrigger(0, 200);
  assert.equal(pad.triggers.left, 200);

  pad.updateTrigger(1, 255);
  assert.equal(pad.triggers.right, 255);

  pad.failsafeReset();
  assert.equal(pad.heldButtons, 0);
  assert.equal(pad.triggers.left, 0);
  assert.equal(pad.triggers.right, 0);
  assert.equal(pad.stickPositions.leftX, 0);

  pad.destroy();
});

test('FallbackGamepadProvider Non-Windows Key Mappings', async () => {
  const fallback = new FallbackGamepadProvider({ threshold: 5000 });
  await fallback.start();

  // Test Left Stick deflection (WASD)
  await fallback.updateStick(0, 10000, 10000); // D and W
  assert.equal(fallback.stickPositions.leftX, 10000);
  assert.equal(fallback.stickPositions.leftY, 10000);

  // Test Right Stick deflection (Arrows)
  await fallback.updateStick(1, -10000, -10000); // Left and Down
  assert.equal(fallback.stickPositions.rightX, -10000);
  assert.equal(fallback.stickPositions.rightY, -10000);

  // Test Buttons
  await fallback.updateButton(GAMEPAD_BUTTON_BITS.A, 1);
  await fallback.updateButton(GAMEPAD_BUTTON_BITS.B, 1);
  await fallback.updateButton(GAMEPAD_BUTTON_BITS.X, 1);
  await fallback.updateButton(GAMEPAD_BUTTON_BITS.Y, 1);

  // Failsafe reset clears all state
  await fallback.failsafeReset();
  assert.equal(fallback.heldKeys.size, 0);
  assert.equal(fallback.heldButtons, 0);

  fallback.destroy();
});

test('InputManager Dual Coexistence: Gyro-to-Mouse + Virtual Gamepad Sticks', async () => {
  const pad = new WindowsGamepadProvider();
  await pad.start();

  const inputManager = new InputManager({ gamepadProvider: pad });

  // 1. Move player with virtual Left Stick
  inputManager.handleGamepadStick({ stickId: 0, posX: 20000, posY: 20000 });
  assert.equal(pad.stickPositions.leftX, 20000);
  assert.equal(pad.stickPositions.leftY, 20000);

  // 2. Set gyro mode to GYRO_TO_MOUSE (aiming with phone gyro while moving with stick)
  inputManager.handleModeSwitch({
    gyroMode: GYRO_MODES.GYRO_TO_MOUSE,
    sensitivityX: 1.5,
    sensitivityY: 1.5,
    deadzone: 0.01,
    smoothingFactor: 0.2,
    ratchetActive: false,
    invertX: false,
    invertY: false,
  });

  // Inject gyro IMU data -> mouse moves without interrupting stick
  inputManager.handleRawImu({
    gyroX: 0.1,
    gyroY: 0.0,
    gyroZ: 0.2,
    accelX: 0,
    accelY: 9.81,
    accelZ: 0,
  });

  // Sticks remain in position
  assert.equal(pad.stickPositions.leftX, 20000);
  assert.equal(pad.stickPositions.leftY, 20000);

  // 3. Switch gyro mode to GYRO_TO_STICK (gyro aims right analog stick)
  inputManager.handleModeSwitch({
    gyroMode: GYRO_MODES.GYRO_TO_STICK,
    sensitivityX: 1.0,
    sensitivityY: 1.0,
    deadzone: 0.01,
    smoothingFactor: 0.0,
    ratchetActive: false,
    invertX: false,
    invertY: false,
  });

  inputManager.handleRawImu({
    gyroX: 0.5,
    gyroY: 0.0,
    gyroZ: 0.5,
    accelX: 0,
    accelY: 9.81,
    accelZ: 0,
  });

  // Right stick should be updated by gyro
  assert.notEqual(pad.stickPositions.rightX, 0);
  assert.notEqual(pad.stickPositions.rightY, 0);

  // Failsafe release
  inputManager.failsafeReleaseAll();
  assert.equal(pad.stickPositions.leftX, 0);
  assert.equal(pad.stickPositions.leftY, 0);
  assert.equal(pad.heldButtons, 0);

  inputManager.destroy();
});
