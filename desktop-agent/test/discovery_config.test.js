import test from 'node:test';
import assert from 'node:assert/strict';
import dgram from 'node:dgram';
import { DiscoveryService } from '../src/server/discovery-service.js';
import { PairingManager } from '../src/server/pairing-manager.js';
import { getSubnetBroadcastAddresses, getAllLocalIpAddresses } from '../src/config.js';
import { decodePacket, parseDiscoveryBeacon, createPacket } from '../src/protocol/packet.js';
import { OPCODES } from '../src/protocol/constants.js';

test('Subnet Broadcast & IP resolution', () => {
  const localIps = getAllLocalIpAddresses();
  assert.ok(localIps.length >= 1);

  const bcastAddrs = getSubnetBroadcastAddresses();
  assert.ok(bcastAddrs.includes('255.255.255.255'));
});

test('PairingManager PIN lifecycle and regeneration', () => {
  const pm = new PairingManager({ configuredPin: 'auto', requirePin: true });
  const pin1 = pm.getPin();
  assert.ok(pin1 >= 100000 && pin1 <= 999999);

  let pinChangedEmitted = false;
  pm.on('pin_changed', (newPin) => {
    pinChangedEmitted = true;
    assert.equal(newPin, pm.getPin());
  });

  const pin2 = pm.regeneratePin();
  assert.equal(pinChangedEmitted, true);
  assert.ok(pin2 >= 100000 && pin2 <= 999999);
});

test('DiscoveryService LAN Beacons and Unicast Query Response', async () => {
  const discoveryPort = 54380;
  const controlPort = 54381;

  const discoveryService = new DiscoveryService({
    broadcastPort: discoveryPort,
    controlPort: controlPort,
    serverName: 'Test AirPad Host',
    osType: 1,
    requirePin: true,
    intervalMs: 150,
  });

  // Client socket simulating the mobile phone listening on discoveryPort
  const listenerSocket = dgram.createSocket({ type: 'udp4', reuseAddr: true });
  await new Promise((resolve) => listenerSocket.bind(discoveryPort, resolve));

  discoveryService.start();

  // Test 1: Verify mobile receives periodic broadcast beacon
  const broadcastBeaconReceived = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Broadcast beacon timeout')), 2000);
    listenerSocket.on('message', (msg) => {
      const packet = decodePacket(msg);
      if (packet && packet.opcode === OPCODES.DISCOVERY_BEACON) {
        const beacon = parseDiscoveryBeacon(packet.payload);
        if (beacon && beacon.serverName === 'Test AirPad Host') {
          clearTimeout(timeout);
          resolve(beacon);
        }
      }
    });
  });

  assert.equal(broadcastBeaconReceived.controlPort, controlPort);
  assert.equal(broadcastBeaconReceived.serverName, 'Test AirPad Host');
  assert.equal(broadcastBeaconReceived.requirePin, true);

  listenerSocket.close();

  // Test 2: Mobile sends discovery ping to desktop discoveryPort -> Desktop responds with immediate unicast beacon to client
  const clientQuerySocket = dgram.createSocket('udp4');
  await new Promise((resolve) => clientQuerySocket.bind(0, resolve));

  const unicastBeaconReceived = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Unicast beacon timeout')), 2000);
    clientQuerySocket.on('message', (msg) => {
      const packet = decodePacket(msg);
      if (packet && packet.opcode === OPCODES.DISCOVERY_BEACON) {
        const beacon = parseDiscoveryBeacon(packet.payload);
        if (beacon && beacon.serverName === 'Test AirPad Host') {
          clearTimeout(timeout);
          resolve(beacon);
        }
      }
    });

    // Send discovery query packet
    const queryPacket = createPacket(OPCODES.DISCOVERY_BEACON, 0x00000000, 1, 0, Buffer.alloc(0));
    clientQuerySocket.send(queryPacket, 0, queryPacket.length, discoveryPort, '127.0.0.1');
  });

  assert.equal(unicastBeaconReceived.serverName, 'Test AirPad Host');
  assert.equal(unicastBeaconReceived.controlPort, controlPort);

  // Clean up
  discoveryService.stop();
  clientQuerySocket.close();
});
