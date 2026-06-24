import { WebSocket, WebSocketServer } from 'ws';

const WEBSOCKET_PATH = '/ws';
const HEARTBEAT_INTERVAL_MS = 30000;
const MAX_BUFFERED_BYTES = 1024 * 1024;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SHARED_CHANNELS = new Set(['telemetry', 'devices']);

let webSocketServer = null;
let heartbeatTimer = null;

const sendJson = (client, message) => {
    if (
        client.readyState !== WebSocket.OPEN ||
        client.bufferedAmount > MAX_BUFFERED_BYTES
    ) {
        return false;
    }

    client.send(JSON.stringify(message));
    return true;
};

const isValidChannel = (channel) => {
    if (SHARED_CHANNELS.has(channel)) {
        return true;
    }

    const [prefix, deviceId] = channel.split(':');
    return (
        prefix === 'device_telemetry' &&
        UUID_PATTERN.test(deviceId || '')
    );
};

const parseClientMessage = (rawMessage) => {
    try {
        const message = JSON.parse(rawMessage.toString());
        const channels = Array.isArray(message.channels)
            ? message.channels
            : message.channel
                ? [message.channel]
                : [];

        if (
            !['subscribe', 'unsubscribe'].includes(message.type) ||
            channels.length === 0 ||
            !channels.every((channel) => typeof channel === 'string' && isValidChannel(channel))
        ) {
            return null;
        }

        return { type: message.type, channels };
    } catch {
        return null;
    }
};

const handleClientMessage = (client, rawMessage) => {
    const message = parseClientMessage(rawMessage);
    if (!message) {
        sendJson(client, {
            type: 'error',
            error: 'Invalid WebSocket message'
        });
        return;
    }

    if (message.type === 'subscribe') {
        message.channels.forEach((channel) => client.subscriptions.add(channel));
    } else {
        message.channels.forEach((channel) => client.subscriptions.delete(channel));
    }

    sendJson(client, {
        type: message.type === 'subscribe' ? 'subscribed' : 'unsubscribed',
        channels: message.channels
    });
};

const startHeartbeat = () => {
    heartbeatTimer = setInterval(() => {
        for (const client of webSocketServer.clients) {
            if (!client.isAlive) {
                client.terminate();
                continue;
            }

            client.isAlive = false;
            client.ping();
        }
    }, HEARTBEAT_INTERVAL_MS);

    heartbeatTimer.unref();
};

export const initializeWebSocketServer = (httpServer) => {
    if (webSocketServer) {
        return webSocketServer;
    }

    webSocketServer = new WebSocketServer({
        server: httpServer,
        path: WEBSOCKET_PATH,
        maxPayload: 16 * 1024,
        perMessageDeflate: false
    });

    webSocketServer.on('connection', (client) => {
        client.isAlive = true;
        client.subscriptions = new Set();

        client.on('pong', () => {
            client.isAlive = true;
        });

        client.on('message', (message) => {
            handleClientMessage(client, message);
        });

        client.on('error', (error) => {
            console.error('WebSocket client error:', error.message);
        });

        sendJson(client, {
            type: 'connected',
            message: 'WebSocket connection established'
        });
    });

    webSocketServer.on('error', (error) => {
        console.error('WebSocket server error:', error.message);
    });

    startHeartbeat();
    console.log(`WebSocket server initialized at ${WEBSOCKET_PATH}`);
    return webSocketServer;
};

const broadcastToChannels = (channels, event) => {
    if (!webSocketServer) {
        return 0;
    }

    let delivered = 0;

    for (const client of webSocketServer.clients) {
        const isSubscribed = channels.some((channel) => client.subscriptions.has(channel));
        if (isSubscribed && sendJson(client, event)) {
            delivered += 1;
        }
    }

    return delivered;
};

export const broadcastToChannel = (channel, event) => (
    broadcastToChannels([channel], event)
);

export const broadcastTelemetry = (device, telemetry) => {
    const event = {
        type: 'telemetry_received',
        data: {
            device_id: device.id,
            device_name: device.name,
            device_type: device.type,
            ts: telemetry.ts,
            temperature: telemetry.temperature,
            humidity: telemetry.humidity
        }
    };

    broadcastToChannels([
        'telemetry',
        `device_telemetry:${device.id}`
    ], event);
};

export const broadcastDeviceRegistered = (device) => {
    broadcastToChannel('devices', {
        type: 'device_registered',
        data: {
            id: device.id,
            name: device.name,
            type: device.type,
            status: device.status
        }
    });
};

export const shutdownWebSocketServer = async () => {
    if (!webSocketServer) {
        return;
    }

    if (heartbeatTimer) {
        clearInterval(heartbeatTimer);
        heartbeatTimer = null;
    }

    const server = webSocketServer;
    webSocketServer = null;

    for (const client of server.clients) {
        client.close(1001, 'Application shutting down');
    }

    await new Promise((resolve) => {
        server.close(() => resolve());

        setTimeout(() => {
            for (const client of server.clients) {
                client.terminate();
            }
            resolve();
        }, 2000).unref();
    });
};
