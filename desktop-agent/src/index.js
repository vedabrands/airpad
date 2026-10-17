import { CONFIG, configManager, getAllLocalIpAddresses } from './config.js';
import { DiscoveryService } from './server/discovery-service.js';
import { HeartbeatWatchdog } from './server/heartbeat-watchdog.js';
import { PairingManager } from './server/pairing-manager.js';
import { UdpServer } from './server/udp-server.js';
import { InputManager } from './input/input-manager.js';
import { TrayManager } from './tray/tray-manager.js';
import { encodeActiveWindowInfo, createPacket, OPCODES } from './protocol/packet.js';

console.clear();
console.log('===============================================================');
console.log('         AIRPAD DESKTOP SERVICE (UDP Protocol Host)           ');
console.log('===============================================================');
console.log(` Host Name        : ${CONFIG.SERVER_NAME}`);
console.log(` Local IP Address : ${getAllLocalIpAddresses().join(', ')}`);
console.log(` Control Port     : ${CONFIG.CONTROL_PORT} (UDP Unicast)`);
console.log(` Discovery Port   : ${CONFIG.DISCOVERY_PORT} (UDP Subnet Broadcast)`);
console.log(` Virtual Gamepad  : ${CONFIG.ENABLE_VIRTUAL_GAMEPAD ? 'Enabled (ViGEmBus Xbox 360)' : 'Disabled'}`);
console.log(` System Tray UI   : ${CONFIG.ENABLE_SYSTEM_TRAY ? 'Enabled (Windows Taskbar)' : 'Disabled'}`);
console.log(` Log Level        : ${CONFIG.LOG_LEVEL}`);
console.log('---------------------------------------------------------------');

// 1. Initialize Pairing Manager
const pairingManager = new PairingManager({
  configuredPin: CONFIG.PAIRING_PIN,
  requirePin: CONFIG.REQUIRE_PIN,
});

function printPinBanner(pin) {
  console.log(`\n >>> AIRPAD PAIRING PIN: [ ${pin} ] <<<\n`);
  console.log(' Enter this 6-digit PIN in the AirPad Android app to connect.');
  console.log('---------------------------------------------------------------');
}

printPinBanner(pairingManager.getPin());

// 2. Initialize Input Subsystem
const inputManager = new InputManager({
  enableGamepad: CONFIG.ENABLE_VIRTUAL_GAMEPAD,
});

// 3. Initialize Heartbeat Watchdog
const watchdog = new HeartbeatWatchdog({
  timeoutMs: CONFIG.DISCONNECT_TIMEOUT_MS,
  tickMs: CONFIG.WATCHDOG_TICK_MS,
});

// 4. Initialize UDP Datagram Control Server
const udpServer = new UdpServer({
  port: CONFIG.CONTROL_PORT,
  pairingManager,
  watchdog,
  inputManager,
  logLevel: CONFIG.LOG_LEVEL,
  statsIntervalMs: CONFIG.STATS_INTERVAL_MS,
});

// 5. Initialize LAN Discovery Broadcast Service
const discoveryService = new DiscoveryService({
  broadcastPort: CONFIG.DISCOVERY_PORT,
  controlPort: CONFIG.CONTROL_PORT,
  serverName: CONFIG.SERVER_NAME,
  osType: CONFIG.OS_TYPE,
  requirePin: CONFIG.REQUIRE_PIN,
  intervalMs: CONFIG.BROADCAST_INTERVAL_MS,
});

// 6. Initialize Windows System Tray Manager
let trayManager = null;
if (CONFIG.ENABLE_SYSTEM_TRAY) {
  trayManager = new TrayManager({
    pin: String(pairingManager.getPin()),
    enableGamepad: CONFIG.ENABLE_VIRTUAL_GAMEPAD,
  });

  trayManager.on('regenerate_pin', () => {
    const newPin = pairingManager.regeneratePin();
    trayManager.updateState({ pin: String(newPin) });
    trayManager.showNotification('AirPad PIN Regenerated', `New Pairing PIN: ${newPin}`);
    printPinBanner(newPin);
  });

  trayManager.on('toggle_gamepad', async (enabled) => {
    configManager.update({ enableVirtualGamepad: enabled });
    await inputManager.setGamepadEnabled(enabled);
    console.log(`[Config] Virtual Gamepad ${enabled ? 'Enabled' : 'Disabled'}`);
    trayManager.showNotification(
      'AirPad Gamepad',
      `Virtual Xbox 360 controller ${enabled ? 'Enabled' : 'Disabled'}.`
    );
  });

  trayManager.on('disconnect_client', () => {
    const active = pairingManager.getActiveSession();
    if (active) {
      console.log(`[Status] Disconnecting client "${active.deviceName}" via System Tray command.`);
      inputManager.failsafeReleaseAll();
      pairingManager.clearSession(active.sessionToken);
      trayManager.updateState({ connected: false, deviceName: '' });
      trayManager.showNotification('AirPad Disconnected', `Disconnected from ${active.deviceName}`);
    }
  });

  trayManager.on('exit_requested', () => {
    shutdown();
  });

  trayManager.start();
}

// 7. Wire Events between Server and System Tray
udpServer.on('client_paired', ({ deviceName }) => {
  console.log(`\n[Status] >>> Phone Connected: "${deviceName}" <<<\n`);
  if (trayManager) {
    trayManager.updateState({
      connected: true,
      deviceName,
    });
    trayManager.showNotification('AirPad Connected', `Connected to "${deviceName}"`);
  }
});

udpServer.on('client_disconnected', ({ session, reason }) => {
  const name = session?.deviceName || 'Phone';
  console.log(`\n[Status] >>> Phone Disconnected: "${name}" (${reason}) <<<\n`);
  if (trayManager) {
    trayManager.updateState({
      connected: false,
      deviceName: '',
    });
    trayManager.showNotification('AirPad Disconnected', `Device "${name}" has disconnected.`);
  }
});

// Start Network Services
udpServer.start();
discoveryService.start();

// Wire active window detection to game profile auto-detection
inputManager.startWindowPolling((info) => {
  console.log(
    `[WindowDetector] Active window changed -> Process: "${info.processName}" | Window: "${info.windowTitle}"`
  );
  // Broadcast active window info to the connected client
  const activeSession = pairingManager.getActiveSession();
  if (activeSession) {
    const payload = encodeActiveWindowInfo(info.processName || '', info.windowTitle || '');
    const packet = createPacket(
      OPCODES.ACTIVE_WINDOW_INFO,
      activeSession.sessionToken,
      udpServer.sequence++,
      0x00,
      payload
    );
    udpServer.socket.send(
      packet,
      0,
      packet.length,
      activeSession.remotePort,
      activeSession.remoteAddress,
      (err) => {
        if (err) {
          console.error('[WindowDetector] Failed to send ACTIVE_WINDOW_INFO:', err.message);
        }
      }
    );
  }
}, 500);

console.log('[Status] LAN Discovery beacons broadcasting on port ' + CONFIG.DISCOVERY_PORT);
console.log('[Status] Waiting for AirPad Android app connection...\n');

// Graceful Shutdown
function shutdown() {
  console.log('\n[Status] Shutting down AirPad Desktop Agent...');
  if (trayManager) {
    trayManager.destroy();
  }
  inputManager.destroy();
  udpServer.stop();
  discoveryService.stop();
  process.exit(0);
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
