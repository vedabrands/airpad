import { WindowsGamepadProvider } from './windows-gamepad.js';
import { FallbackGamepadProvider } from './fallback-gamepad.js';

/**
 * Factory that creates the appropriate Gamepad Provider based on the current platform
 * and driver availability.
 *
 * @param {object} [options]
 * @returns {Promise<WindowsGamepadProvider|FallbackGamepadProvider>}
 */
export async function createGamepadProvider(options = {}) {
  const isWindows = process.platform === 'win32';

  if (isWindows) {
    try {
      const windowsProvider = new WindowsGamepadProvider(options);
      await windowsProvider.start();
      return windowsProvider;
    } catch (err) {
      console.warn(
        `[GamepadFactory] Failed to initialize Windows ViGEm virtual gamepad (${err.message}). ` +
        `Falling back to keyboard-mapped Gamepad emulation.`
      );
      const fallback = new FallbackGamepadProvider(options);
      await fallback.start();
      return fallback;
    }
  } else {
    console.info(`[GamepadFactory] Non-Windows OS detected (${process.platform}). Using keyboard fallback emulation.`);
    const fallback = new FallbackGamepadProvider(options);
    await fallback.start();
    return fallback;
  }
}

export { WindowsGamepadProvider, FallbackGamepadProvider };
