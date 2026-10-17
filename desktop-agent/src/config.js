import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import 'dotenv/config';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const CONFIG_FILE_PATH = path.resolve(__dirname, '..', 'config.json');

/**
 * Discovers all active non-internal IPv4 addresses on the host.
 * @returns {string[]}
 */
export function getAllLocalIpAddresses() {
  const ips = [];
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === 'IPv4' && !iface.internal) {
        ips.push(iface.address);
      }
    }
  }
  return ips.length > 0 ? ips : ['127.0.0.1'];
}

/**
 * Calculates broadcast addresses for all local IPv4 interfaces.
 * (e.g., 192.168.1.150/24 -> 192.168.1.255)
 * @returns {string[]}
 */
export function getSubnetBroadcastAddresses() {
  const broadcasts = new Set(['255.255.255.255']);
  const interfaces = os.networkInterfaces();

  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === 'IPv4' && !iface.internal && iface.netmask && iface.address) {
        try {
          const ipParts = iface.address.split('.').map(Number);
          const maskParts = iface.netmask.split('.').map(Number);
          const bcastParts = ipParts.map((part, i) => (part | (~maskParts[i] & 0xff)) >>> 0);
          broadcasts.add(bcastParts.join('.'));
        } catch {}
      }
    }
  }
  return Array.from(broadcasts);
}

/**
 * Default configuration values.
 */
export const DEFAULT_CONFIG = {
  // Network Ports
  controlPort: 54321,
  discoveryPort: 54320,

  // Server Info
  serverName: `${os.hostname()} (AirPad Host)`,
  enableVirtualGamepad: true,
  enableSystemTray: true,

  // Pairing & Security
  pairingPin: 'auto', // 'auto' generates a random 6-digit PIN on start
  requirePin: true,

  // Timing & Watchdog (ms)
  broadcastIntervalMs: 1000,
  disconnectTimeoutMs: 1500,
  watchdogTickMs: 100,
  statsIntervalMs: 2000,

  // Logging level: 'info' | 'debug' | 'quiet'
  logLevel: 'info',

  // Default Gyro Tuning
  gyro: {
    sensitivityX: 1.2,
    sensitivityY: 1.2,
    deadzone: 0.02,
    smoothingFactor: 0.25,
  },
};

class ConfigManager {
  constructor() {
    this.configPath = CONFIG_FILE_PATH;
    this.data = { ...DEFAULT_CONFIG };
    this.load();
  }

  /**
   * Loads config from config.json if it exists; creates it otherwise.
   */
  load() {
    try {
      if (fs.existsSync(this.configPath)) {
        const raw = fs.readFileSync(this.configPath, 'utf8');
        const parsed = JSON.parse(raw);
        this.data = {
          ...DEFAULT_CONFIG,
          ...parsed,
          gyro: {
            ...DEFAULT_CONFIG.gyro,
            ...(parsed.gyro || {}),
          },
        };
      } else {
        this.save();
      }
    } catch (err) {
      console.warn(`[Config] Failed to load config.json (${err.message}). Using defaults.`);
      this.data = { ...DEFAULT_CONFIG };
    }
    return this.data;
  }

  /**
   * Persists current config object to config.json.
   */
  save() {
    try {
      fs.writeFileSync(this.configPath, JSON.stringify(this.data, null, 2), 'utf8');
    } catch (err) {
      console.warn(`[Config] Failed to write config.json: ${err.message}`);
    }
  }

  /**
   * Updates partial settings and saves to disk.
   * @param {object} updates
   */
  update(updates) {
    this.data = {
      ...this.data,
      ...updates,
      gyro: {
        ...this.data.gyro,
        ...(updates.gyro || {}),
      },
    };
    this.save();
    return this.data;
  }

  get(key) {
    return this.data[key];
  }

  getAll() {
    return { ...this.data };
  }
}

export const configManager = new ConfigManager();

// Convenience legacy-compatible CONFIG export
export const CONFIG = {
  get CONTROL_PORT() { return configManager.get('controlPort'); },
  get DISCOVERY_PORT() { return configManager.get('discoveryPort'); },
  get SERVER_NAME() { return configManager.get('serverName'); },
  get OS_TYPE() { return os.platform() === 'win32' ? 1 : os.platform() === 'linux' ? 2 : 3; },
  get HOST_IP() { return getAllLocalIpAddresses()[0]; },
  get ALL_HOST_IPS() { return getAllLocalIpAddresses(); },
  get BROADCAST_ADDRESSES() { return getSubnetBroadcastAddresses(); },
  get ENABLE_VIRTUAL_GAMEPAD() { return configManager.get('enableVirtualGamepad'); },
  get ENABLE_SYSTEM_TRAY() { return configManager.get('enableSystemTray'); },
  get PAIRING_PIN() { return configManager.get('pairingPin'); },
  get REQUIRE_PIN() { return configManager.get('requirePin'); },
  get BROADCAST_INTERVAL_MS() { return configManager.get('broadcastIntervalMs'); },
  get DISCONNECT_TIMEOUT_MS() { return configManager.get('disconnectTimeoutMs'); },
  get WATCHDOG_TICK_MS() { return configManager.get('watchdogTickMs'); },
  get STATS_INTERVAL_MS() { return configManager.get('statsIntervalMs'); },
  get LOG_LEVEL() { return configManager.get('logLevel'); },
  get GYRO() { return configManager.get('gyro'); },
};
