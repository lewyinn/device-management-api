import {
    beforeEach,
    describe,
    expect,
    jest,
    test
} from '@jest/globals';

const findDeviceMock = jest.fn();
const insertTelemetryMock = jest.fn();
const recordMonthMock = jest.fn();
const broadcastTelemetryMock = jest.fn();

jest.unstable_mockModule(
    '../../../src/repository/device.repository.js',
    () => ({
        findDeviceForTelemetry: findDeviceMock
    })
);

jest.unstable_mockModule(
    '../../../src/repository/telemetry.cassandra.repository.js',
    () => ({
        insertTelemetry: insertTelemetryMock,
        recordMonth: recordMonthMock
    })
);

jest.unstable_mockModule(
    '../../../src/websocket/websocketServer.js',
    () => ({
        broadcastTelemetry: broadcastTelemetryMock
    })
);

const {
    extractDeviceId,
    parseTelemetryPayload,
    handleTelemetryMessage
} = await import('../../../src/mqtt/telemetrySubscriber.js');

const deviceId = '550e8400-e29b-41d4-a716-446655440000';
const topic = `gedung-solu/monitoring/lantai-1/devices/${deviceId}/telemetry`;

const device = {
    id: deviceId,
    name: 'Sensor-Suhu',
    type: 'DHT22',
    status: 'active'
};

const telemetry = {
    ts: 1781490918553,
    temperature: 27.47,
    humidity: 61.1
};

describe('MQTT telemetry handler', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        jest.spyOn(console, 'log').mockImplementation(() => {});
        jest.spyOn(console, 'warn').mockImplementation(() => {});

        findDeviceMock.mockResolvedValue(device);
        insertTelemetryMock.mockResolvedValue(telemetry);
        recordMonthMock.mockReturnValue('2026-06');
    });

    test('reads device UUID from MQTT topic', () => {
        const result = extractDeviceId(topic);

        expect(result).toBe(deviceId);
    });

    test('parses valid MQTT payload', () => {
        const payload = Buffer.from(JSON.stringify(telemetry));

        const result = parseTelemetryPayload(payload);

        expect(result).toEqual(telemetry);
    });

    test('stores telemetry and broadcasts WebSocket event', async () => {
        const payload = Buffer.from(JSON.stringify(telemetry));

        await handleTelemetryMessage(topic, payload);

        expect(findDeviceMock).toHaveBeenCalledWith(deviceId);
        expect(insertTelemetryMock).toHaveBeenCalledWith({
            deviceId,
            recordMonth: '2026-06',
            ts: telemetry.ts,
            temperature: telemetry.temperature,
            humidity: telemetry.humidity
        });
        expect(broadcastTelemetryMock).toHaveBeenCalledWith(
            device,
            telemetry
        );
    });

    test('ignores telemetry when device is not registered', async () => {
        findDeviceMock.mockResolvedValue(null);
        const payload = Buffer.from(JSON.stringify(telemetry));

        await handleTelemetryMessage(topic, payload);

        expect(insertTelemetryMock).not.toHaveBeenCalled();
        expect(broadcastTelemetryMock).not.toHaveBeenCalled();
    });
});
