import { findDeviceForTelemetry } from '../repository/device.repository.js';
import { findAllDevicesForTelemetry } from '../repository/device.repository.js';
import {
    findLatestTelemetry,
    findTelemetryByMonthRange,
    insertTelemetry,
    findRecentTelemetryForDevice,
    recordMonth
} from '../repository/telemetry.cassandra.repository.js';

const isUuid = (value) => (
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
);
const isNumber = (value) => typeof value === 'number' && Number.isFinite(value);
const isRecordMonth = (value) => /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
const DEFAULT_START_MONTH = '2026-01';
const DEFAULT_END_MONTH = '2026-12';
const DEFAULT_RECENT_LIMIT = 10;
const MAX_RECENT_LIMIT = 100;

const formatTelemetry = (telemetry, device) => ({
    device_id: device.id,
    deviceName: device.name,
    deviceType: device.type,
    data: telemetry
});

const parseLimit = (value) => {
    const limit = value === undefined ? DEFAULT_RECENT_LIMIT : Number(value);

    if (!Number.isInteger(limit) || limit < 1 || limit > MAX_RECENT_LIMIT) {
        return null;
    }

    return limit;
};

export const createTelemetry = async (req, res, next) => {
    try {
        const { id } = req.params;
        const { values = {} } = req.body;
        const ts = Date.now();

        if (!isUuid(id)) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'Device ID must be a valid UUID'
            });
        }

        if (!isNumber(values.temperature) || !isNumber(values.humidity)) {
            return res.status(400).json({
                error: 'Validation failed',
                details: "Attributes 'values.temperature' and 'values.humidity' must be numbers"
            });
        }

        const device = await findDeviceForTelemetry(id);
        if (!device) return res.status(404).json({ error: `Device ID ${id} not found` });

        const telemetry = await insertTelemetry({
            deviceId: device.id,
            ts,
            temperature: values.temperature,
            humidity: values.humidity
        });

        return res.status(201).json({
            message: 'Telemetry successfully recorded',
            data: formatTelemetry(telemetry, device)
        });
    } catch (err) {
        return next(err);
    }
};


export const getTelemetryByDevice = async (req, res, next) => {
    try {
        const { id } = req.params;
        if (!isUuid(id)) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'Device ID must be a valid UUID'
            });
        }

        const device = await findDeviceForTelemetry(id);
        if (!device) return res.status(404).json({ error: `Device ID ${id} not found` });

        const startMonth = req.query.start_month || DEFAULT_START_MONTH;
        const endMonth = req.query.end_month || DEFAULT_END_MONTH;

        if (!isRecordMonth(startMonth) || !isRecordMonth(endMonth) || startMonth > endMonth) {
            return res.status(400).json({
                error: 'Validation failed',
                details: "Query parameter 'start_month' and 'end_month' must use YYYY-MM format"
            });
        }

        const telemetries = await findTelemetryByMonthRange({
            deviceId: device.id,
            startMonth,
            endMonth
        });

        return res.status(200).json({
            message: 'Success retrieving telemetry',
            device_id: device.id,
            deviceName: device.name,
            deviceType: device.type,
            data: telemetries
        });
    } catch (err) {
        return next(err);
    }
};

export const getLatestTelemetryByDevice = async (req, res, next) => {
    try {
        const { id } = req.params;
        if (!isUuid(id)) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'Device ID must be a valid UUID'
            });
        }

        const device = await findDeviceForTelemetry(id);
        if (!device) return res.status(404).json({ error: `Device ID ${id} not found` });

        const telemetry = await findLatestTelemetry({ deviceId: device.id });
        if (!telemetry) {
            return res.status(404).json({ error: `Telemetry for device ID ${id} not found` });
        }

        return res.status(200).json({
            message: 'Latest telemetry found',
            data: formatTelemetry(telemetry, device)
        });
    } catch (err) {
        return next(err);
    }
};

export const getRecentTelemetry = async (req, res, next) => {
    try {
        const limit = parseLimit(req.query.limit);
        if (!limit) {
            return res.status(400).json({
                error: 'Validation failed',
                details: `Query parameter 'limit' must be an integer between 1 and ${MAX_RECENT_LIMIT}`
            });
        }

        const devices = await findAllDevicesForTelemetry();
        const month = recordMonth(new Date());
        const telemetryByDevice = await Promise.all(
            devices.map(async (device) => {
                const telemetries = await findRecentTelemetryForDevice({
                    deviceId: device.id,
                    month,
                    limit
                });

                return telemetries.map((telemetry) => ({
                    device_id: device.id,
                    device_name: device.name,
                    device_type: device.type,
                    ...telemetry
                }));
            })
        );

        const data = telemetryByDevice
            .flat()
            .sort((left, right) => right.ts - left.ts)
            .slice(0, limit);

        return res.status(200).json({
            message: 'Latest telemetry retrieved',
            data
        });
    } catch (error) {
        return next(error);
    }
};
