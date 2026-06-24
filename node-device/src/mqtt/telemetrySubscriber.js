import mqtt from 'mqtt';
import { insertTelemetry, recordMonth } from '../repository/telemetry.cassandra.repository.js';
import { findDeviceForTelemetry } from '../repository/device.repository.js';
import { broadcastTelemetry } from '../websocket/websocketServer.js';

const RECONNECT_PERIOD_MS = 2000;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

let mqttClient = null;

const isNumber = (value) => typeof value === 'number' && Number.isFinite(value);
const isEpochMilliseconds = (value) => Number.isSafeInteger(value) && value > 0;

export const extractDeviceId = (topic) => {
    const parts = topic.split('/');

    if (
        parts.length !== 6 ||
        parts[0] !== 'gedung-solu' ||
        parts[1] !== 'monitoring' ||
        parts[2] !== 'lantai-1' ||
        parts[3] !== 'devices' ||
        parts[5] !== 'telemetry'
    ) {
        return null;
    }

    const deviceId = parts[4];
    return UUID_PATTERN.test(deviceId) ? deviceId : null;
};

export const parseTelemetryPayload = (payload) => {
    try {
        const body = JSON.parse(payload.toString());
        const ts = body?.ts;
        const temperature = body?.temperature;
        const humidity = body?.humidity;

        if (!isEpochMilliseconds(ts) || !isNumber(temperature) || !isNumber(humidity)) {
            return null;
        }

        return { ts, temperature, humidity };
    } catch (error) {
        return null;
    }
};

export const handleTelemetryMessage = async (topic, payload) => {
    const deviceId = extractDeviceId(topic);
    if (!deviceId) {
        console.warn(`MQTT telemetry ignored because topic is invalid: ${topic}`);
        return;
    }

    const deviceData = await findDeviceForTelemetry(deviceId);
    if (!deviceData) {
        console.warn(`MQTT telemetry ignored because device ID ${deviceId} not found`);
        return;
    }


    const telemetry = parseTelemetryPayload(payload);
    if (!telemetry) {
        console.warn(`MQTT telemetry ignored because payload is invalid for topic: ${topic}`);
        return;
    }

    try {
        await insertTelemetry({
            deviceId,
            recordMonth: recordMonth(new Date(telemetry.ts)),
            ts: telemetry.ts,
            temperature: telemetry.temperature,
            humidity: telemetry.humidity
        });
        console.log(`MQTT telemetry persisted for device ${deviceId} at ${telemetry.ts}`);
        broadcastTelemetry(deviceData, telemetry);
    } catch (error) {
        console.error('Failed to persist MQTT telemetry:', error.message);
    }
};

export const startMqttTelemetrySubscriber = () => {
    if (mqttClient) {
        return mqttClient;
    }

    const brokerUrl = process.env.MQTT_BROKER_URL;
    const topic = process.env.MQTT_TELEMETRY_TOPIC;

    mqttClient = mqtt.connect(brokerUrl, {
        clientId: `node-device-telemetry-${Math.random().toString(16).slice(2)}`,
        clean: true,
        reconnectPeriod: RECONNECT_PERIOD_MS,
        connectTimeout: 30000
    });

    mqttClient.on('connect', () => {
        console.log(`MQTT connected to ${brokerUrl}`);
        mqttClient.subscribe(topic, { qos: 0 }, (error) => {
            if (error) {
                console.error('MQTT subscribe failed:', error.message);
                return;
            }

            console.log(`MQTT subscribed to ${topic}`);
        });
    });

    mqttClient.on('message', (topicName, payload) => {
        void handleTelemetryMessage(topicName, payload);
    });

    mqttClient.on('reconnect', () => {
        console.log('MQTT reconnecting...');
    });

    mqttClient.on('close', () => {
        console.log('MQTT connection closed');
    });

    mqttClient.on('offline', () => {
        console.log('MQTT client offline');
    });

    mqttClient.on('error', (error) => {
        console.error('MQTT client error:', error.message);
    });

    return mqttClient;
};

export const stopMqttTelemetrySubscriber = async () => {
    if (!mqttClient) {
        return;
    }

    const client = mqttClient;
    mqttClient = null;

    await new Promise((resolve) => {
        client.end(false, {}, resolve);
    });
};
