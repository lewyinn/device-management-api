import db from '../db/index.js';
import { isPositiveInteger, isUuid } from '../utils/validation.js';

const { sequelize, Device, DeviceTelemetry } = db;

const validationFailed = (res, details) => res.status(400).json({
    error: 'Validation failed',
    details
});

const invalidDeviceId = (res) => validationFailed(res, 'Device ID must be a valid UUID');
const invalidTelemetryId = (res) => validationFailed(res, 'Telemetry ID must be a valid number');

const isNumber = (value) => typeof value === 'number' && Number.isFinite(value);

const telemetryPoint = (telemetry) => {
    const data = telemetry.toJSON();
    return {
        ts: Number(data.ts),
        temperature: data.temperature,
        humidity: data.humidity
    };
};

const formatTelemetry = (telemetry, device) => ({
    device_id: device.id,
    deviceName: device.name,
    deviceType: device.type,
    data: telemetryPoint(telemetry)
});

export const createTelemetry = async (req, res, next) => {
    try {
        const { id } = req.params;
        const { values = {} } = req.body;
        const ts = Date.now();

        if (!isUuid(id)) return invalidDeviceId(res);
        if (!isNumber(values.temperature) || !isNumber(values.humidity)) {
            return validationFailed(res, "Attributes 'values.temperature' and 'values.humidity' must be numbers");
        }

        const result = await sequelize.transaction(async (transaction) => {
            const device = await Device.findByPk(id, { transaction });
            if (!device) return { notFound: true };

            const duplicate = await DeviceTelemetry.findOne({
                where: { device_id: id, ts },
                transaction
            });
            if (duplicate) return { duplicate: true, device };

            const telemetry = await DeviceTelemetry.create({
                device_id: device.id,
                ts,
                temperature: values.temperature,
                humidity: values.humidity
            }, { transaction });

            return { device, telemetry };
        });

        if (result.notFound) return res.status(404).json({ error: `Device ID ${id} not found` });
        if (result.duplicate) {
            return res.status(409).json({
                error: 'Duplicate telemetry timestamp',
                details: `Telemetry for device ID ${id} at ts ${ts} already exists`
            });
        }

        return res.status(201).json({
            message: 'Telemetry successfully recorded',
            data: formatTelemetry(result.telemetry, result.device)
        });
    } catch (err) {
        return next(err);
    }
};

export const getTelemetryByDevice = async (req, res, next) => {
    try {
        const { id } = req.params;
        if (!isUuid(id)) return invalidDeviceId(res);

        const device = await Device.findByPk(id);
        if (!device) return res.status(404).json({ error: `Device ID ${id} not found` });

        const telemetries = await DeviceTelemetry.findAll({
            where: { device_id: id },
            order: [['ts', 'DESC']]
        });

        return res.status(200).json({
            message: 'Success retrieving telemetry',
            device_id: device.id,
            deviceName: device.name,
            deviceType: device.type,
            data: telemetries.map(telemetryPoint)
        });
    } catch (err) {
        return next(err);
    }
};

export const getLatestTelemetryByDevice = async (req, res, next) => {
    try {
        const { id } = req.params;
        if (!isUuid(id)) return invalidDeviceId(res);

        const device = await Device.findByPk(id);
        if (!device) return res.status(404).json({ error: `Device ID ${id} not found` });

        const telemetry = await DeviceTelemetry.findOne({
            where: { device_id: id },
            order: [['ts', 'DESC']]
        });
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

export const deleteTelemetry = async (req, res, next) => {
    try {
        if (!isPositiveInteger(req.params.id)) return invalidTelemetryId(res);

        const deleted = await sequelize.transaction(async (transaction) => {
            const telemetry = await DeviceTelemetry.findByPk(req.params.id, { transaction });
            if (!telemetry) return false;

            await telemetry.destroy({ transaction });
            return true;
        });

        if (!deleted) return res.status(404).json({ error: `Telemetry ID ${req.params.id} not found` });
        return res.status(204).send();
    } catch (err) {
        return next(err);
    }
};
