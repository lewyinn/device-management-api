import { createServer } from 'node:http';
import { once } from 'node:events';

import {
    afterAll,
    beforeAll,
    describe,
    expect,
    test
} from '@jest/globals';
import { WebSocket } from 'ws';

import {
    broadcastDeviceRegistered,
    broadcastTelemetry,
    initializeWebSocketServer,
    shutdownWebSocketServer
} from '../../src/websocket/websocketServer.js';

let httpServer;
let webSocketUrl;

const waitForMessage = (client, expectedType) => (
    new Promise((resolve, reject) => {
        const timeout = setTimeout(() => {
            reject(new Error(`Timeout waiting for ${expectedType}`));
        }, 3000);

        client.once('message', (rawMessage) => {
            clearTimeout(timeout);
            const message = JSON.parse(rawMessage.toString());

            if (message.type !== expectedType) {
                reject(new Error(
                    `Expected ${expectedType}, received ${message.type}`
                ));
                return;
            }

            resolve(message);
        });
    })
);

const connectClient = async () => {
    const client = new WebSocket(webSocketUrl);
    const connectedMessage = waitForMessage(client, 'connected');

    await once(client, 'open');
    await connectedMessage;
    return client;
};

const subscribe = async (client, channel) => {
    const subscribedMessage = waitForMessage(client, 'subscribed');

    client.send(JSON.stringify({
        type: 'subscribe',
        channels: [channel]
    }));

    await subscribedMessage;
};

const closeClient = async (client) => {
    const closed = once(client, 'close');
    client.close();
    await closed;
};

beforeAll(async () => {
    httpServer = createServer();
    initializeWebSocketServer(httpServer);

    await new Promise((resolve) => {
        httpServer.listen(0, '127.0.0.1', resolve);
    });

    const address = httpServer.address();
    webSocketUrl = `ws://127.0.0.1:${address.port}/ws`;
});

afterAll(async () => {
    await shutdownWebSocketServer();

    if (httpServer.listening) {
        await new Promise((resolve) => {
            httpServer.close(resolve);
        });
    }
});

describe('WebSocket events', () => {
    const device = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        name: 'Sensor-Suhu',
        type: 'DHT22',
        status: 'active'
    };

    test('sends live telemetry to subscribed client', async () => {
        const client = await connectClient();
        await subscribe(client, 'telemetry');

        const telemetryEvent = waitForMessage(
            client,
            'telemetry_received'
        );

        broadcastTelemetry(device, {
            ts: 1781490918553,
            temperature: 27.47,
            humidity: 61.1
        });

        const event = await telemetryEvent;

        expect(event.data.device_id).toBe(device.id);
        expect(event.data.temperature).toBe(27.47);
        expect(event.data.humidity).toBe(61.1);

        await closeClient(client);
    });

    test('sends device registration to subscribed client', async () => {
        const client = await connectClient();
        await subscribe(client, 'devices');

        const deviceEvent = waitForMessage(
            client,
            'device_registered'
        );

        broadcastDeviceRegistered(device);

        const event = await deviceEvent;

        expect(event.data).toEqual(device);

        await closeClient(client);
    });

    test('rejects invalid channel', async () => {
        const client = await connectClient();
        const errorMessage = waitForMessage(client, 'error');

        client.send(JSON.stringify({
            type: 'subscribe',
            channels: ['invalid-channel']
        }));

        const event = await errorMessage;

        expect(event.error).toBe('Invalid WebSocket message');

        await closeClient(client);
    });
});
