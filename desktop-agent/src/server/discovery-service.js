import dgram from 'node:dgram';
import { EventEmitter } from 'node:events';
import { OPCODES } from '../protocol/constants.js';
import { createPacket, encodeDiscoveryBeacon, decodePacket } from '../protocol/packet.js';
import { getSubnetBroadcastAddresses } from '../config.js';

export class DiscoveryService extends EventEmitter {
  /**
   * @param {object} options
   * @param {number} [options.broadcastPort=54320]
   * @param {number} [options.controlPort=54321]
   * @param {string} [options.serverName='AirPad Host']
   * @param {number} [options.osType=1]
   * @param {boolean} [options.requirePin=true]
   * @param {number} [options.intervalMs=1000]
   */
  constructor({
    broadcastPort = 54320,
    controlPort = 54321,
    serverName = 'AirPad Host',
    osType = 1,
    requirePin = true,
    intervalMs = 1000,
  } = {}) {
    super();
    this.broadcastPort = broadcastPort;
    this.controlPort = controlPort;
    this.serverName = serverName;
    this.osType = osType;
    this.requirePin = requirePin;
    this.intervalMs = intervalMs;
    this.socket = null;
    this.beaconTimer = null;
    this.sequence = 0;
    this.isRunning = false;
  }

  /**
   * Starts the discovery beacon broadcast and active query listener.
   */
  start() {
    if (this.isRunning) return;

    this.socket = dgram.createSocket({ type: 'udp4', reuseAddr: true });

    this.socket.on('error', (err) => {
      this.emit('error', err);
    });

    // Listen for incoming discovery queries / pings from mobile clients
    this.socket.on('message', (msg, rinfo) => {
      this._handleIncomingMessage(msg, rinfo);
    });

    this.socket.bind(this.broadcastPort, () => {
      try {
        this.socket.setBroadcast(true);
      } catch (err) {
        // Some systems restrict broadcast flag on bound ports
      }

      this.isRunning = true;
      this.emit('listening', { port: this.broadcastPort });

      // Start periodic broadcast timer
      this.beaconTimer = setInterval(() => {
        this._broadcastBeacon();
      }, this.intervalMs);

      // Send initial beacon immediately
      this._broadcastBeacon();
    });
  }

  /**
   * Handles incoming discovery ping or query packet from a mobile client.
   * Responds with an immediate unicast DISCOVERY_BEACON back to the sender.
   *
   * @param {Buffer} msg
   * @param {dgram.RemoteInfo} rinfo
   */
  _handleIncomingMessage(msg, rinfo) {
    const packet = decodePacket(msg);
    // If it's a DISCOVERY_BEACON query or any AirPad probe
    if (packet && packet.opcode === OPCODES.DISCOVERY_BEACON) {
      this.emit('query_received', { remoteAddress: rinfo.address, remotePort: rinfo.port });
      this._sendUnicastBeacon(rinfo.address, rinfo.port);
    }
  }

  /**
   * Encodes a fresh DISCOVERY_BEACON packet.
   * @returns {Buffer}
   */
  _createBeaconPacket() {
    const payload = encodeDiscoveryBeacon(
      this.controlPort,
      this.osType,
      this.requirePin,
      this.serverName
    );

    return createPacket(
      OPCODES.DISCOVERY_BEACON,
      0x00000000,
      this.sequence++,
      0x00,
      payload
    );
  }

  /**
   * Broadcasts beacon to 255.255.255.255 and all local subnet broadcast IPs.
   */
  _broadcastBeacon() {
    if (!this.socket || !this.isRunning) return;

    const packet = this._createBeaconPacket();
    const targets = getSubnetBroadcastAddresses();

    for (const targetIp of targets) {
      this.socket.send(packet, 0, packet.length, this.broadcastPort, targetIp, (err) => {
        if (!err) {
          this.emit('beacon_sent', { targetIp, port: this.broadcastPort });
        }
      });
    }
  }

  /**
   * Sends a targeted unicast beacon in response to a discovery ping.
   * @param {string} remoteAddress
   * @param {number} remotePort
   */
  _sendUnicastBeacon(remoteAddress, remotePort) {
    if (!this.socket || !this.isRunning) return;

    const packet = this._createBeaconPacket();
    this.socket.send(packet, 0, packet.length, remotePort, remoteAddress, (err) => {
      if (!err) {
        this.emit('unicast_beacon_sent', { remoteAddress, remotePort });
      }
    });
  }

  /**
   * Updates the server name broadcast in beacons.
   * @param {string} newName
   */
  setServerName(newName) {
    this.serverName = newName;
  }

  /**
   * Updates the required PIN flag.
   * @param {boolean} requirePin
   */
  setRequirePin(requirePin) {
    this.requirePin = requirePin;
  }

  /**
   * Stops the discovery service and releases resources.
   */
  stop() {
    this.isRunning = false;
    if (this.beaconTimer) {
      clearInterval(this.beaconTimer);
      this.beaconTimer = null;
    }
    if (this.socket) {
      try {
        this.socket.close();
      } catch {}
      this.socket = null;
    }
    this.emit('stopped');
  }
}
