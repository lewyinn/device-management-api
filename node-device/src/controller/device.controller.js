import { createHash } from 'crypto';
import db from '../db/index.js';
import { sendDeviceRegisteredTelegramNotification } from '../service/telegram.service.js';

const { sequelize, Device } = db;
const LONG_POLL_TIMEOUT_MS = 25 * 1000;
const LONG_POLL_INTERVAL_MS = 1000;

const isUuid = (value) => (
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
);

const isValidStatus = (value) => ['active', 'inactive'].includes(value);

const getPagination = (page = '1', limit = '10') => {
    const pageNum = Number(page);
    const limitNum = Number(limit);

    if (!Number.isInteger(pageNum) || pageNum < 1 || !Number.isInteger(limitNum) || limitNum < 1) {
        return null;
    }

    const normalizedLimit = Math.min(limitNum, 50);
    return {
        pageNum,
        limitNum: normalizedLimit,
        offset: (pageNum - 1) * normalizedLimit
    };
};

const wait = (milliseconds) => new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
});

const buildSnapshot = ({ count, data }) => createHash('sha256')
    .update(JSON.stringify({ count, data }))
    .digest('hex');

const getDevicePage = async (page, limit) => {
    const pagination = getPagination(page, limit);
    if (!pagination) {
        return null;
    }

    const { pageNum, limitNum, offset } = pagination;
    const { count, rows } = await Device.findAndCountAll({
        offset,
        limit: limitNum,
        order: [['name', 'ASC']]
    });
    const data = rows.map((row) => row.toJSON());

    return {
        meta: {
            page: pageNum,
            limit: limitNum,
            total_data: count,
            total_pages: Math.ceil(count / limitNum)
        },
        snapshot: buildSnapshot({ count, data }),
        data
    };
};

const validateStatus = (res, status) => {
    if (status !== undefined && !isValidStatus(status)) {
        res.status(400).json({
            error: 'Validation failed',
            details: "Status must be 'active' or 'inactive'"
        });
        return false;
    }

    return true;
};

export const createDevice = async (req, res, next) => {
    try {
        const { name, type, status } = req.body;
        if (!name || !type) {
            return res.status(400).json({
                error: 'Validation failed',
                details: !name ? "Attribute 'name' is required" : "Attribute 'type' is required"
            });
        }
        if (!validateStatus(res, status)) return;

        const device = await sequelize.transaction(async (transaction) => {
            return Device.create({ name, type, status }, { transaction });
        });

        const deviceData = device.toJSON();

        res.status(201).json({
            message: 'Device successfully registered',
            data: deviceData
        });

        setImmediate(() => {
            void sendDeviceRegisteredTelegramNotification(deviceData).catch((error) => {
                console.error('Telegram device registration notification failed:', error.message);
            });
        });

        return;
    } catch (err) {
        return next(err);
    }
};

export const getDevices = async (req, res, next) => {
    try {
        const devicePage = await getDevicePage(req.query.page, req.query.limit);
        if (!devicePage) {
            return res.status(400).json({
                error: 'Validation failed',
                details: "Query parameter 'page' or 'limit' must be a valid number"
            });
        }

        return res.status(200).json({
            message: 'Success retrieving devices',
            meta: devicePage.meta,
            data: devicePage.data
        });
    } catch (err) {
        return next(err);
    }
};

export const getShortPollingDevices = async (req, res, next) => {
    try {
        const devicePage = await getDevicePage(req.query.page, req.query.limit);
        if (!devicePage) {
            return res.status(400).json({
                error: 'Validation failed',
                details: "Query parameter 'page' or 'limit' must be a valid number"
            });
        }

        return res.status(200).json({
            message: 'Short polling response: devices retrieved',
            pattern: 'short_polling',
            details: 'Client requests this endpoint repeatedly at a fixed interval to refresh device data',
            snapshot: devicePage.snapshot,
            meta: devicePage.meta,
            data: devicePage.data
        });
    } catch (err) {
        return next(err);
    }
};

export const getLongPollingDevices = async (req, res, next) => {
    try {
        const clientSnapshot = req.query.snapshot;

        if (clientSnapshot !== undefined && typeof clientSnapshot !== 'string') {
            return res.status(400).json({
                error: 'Validation failed',
                details: "Query parameter 'snapshot' must be a string"
            });
        }

        const startedAt = Date.now();
        const deadline = startedAt + LONG_POLL_TIMEOUT_MS;

        while (Date.now() < deadline) {
            const devicePage = await getDevicePage(req.query.page, req.query.limit);
            if (!devicePage) {
                return res.status(400).json({
                    error: 'Validation failed',
                    details: "Query parameter 'page' or 'limit' must be a valid number"
                });
            }

            if (!clientSnapshot || devicePage.snapshot !== clientSnapshot) {
                return res.status(200).json({
                    message: 'Long polling response: device data changed',
                    pattern: 'long_polling',
                    details: 'Server held the HTTP request until the device list snapshot changed',
                    snapshot: devicePage.snapshot,
                    meta: devicePage.meta,
                    data: devicePage.data
                });
            }

            await wait(Math.min(LONG_POLL_INTERVAL_MS, Math.max(0, deadline - Date.now())));
        }

        return res.status(200).json({
            message: 'Long polling timeout: device data did not change',
            pattern: 'long_polling',
            details: `No device data change was detected within ${LONG_POLL_TIMEOUT_MS / 1000} seconds`,
            snapshot: clientSnapshot,
            data: null
        });
    } catch (err) {
        return next(err);
    }
};

export const getDeviceById = async (req, res, next) => {
    try {
        if (!isUuid(req.params.id)) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'Device ID must be a valid UUID'
            });
        }

        const device = await Device.findByPk(req.params.id);
        if (!device) return res.status(404).json({ error: `Device ID ${req.params.id} not found` });

        return res.status(200).json({ message: 'Device found', data: device.toJSON() });
    } catch (err) {
        return next(err);
    }
};

export const updateDevice = async (req, res, next) => {
    try {
        if (!isUuid(req.params.id)) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'Device ID must be a valid UUID'
            });
        }

        const { name, type, status } = req.body;
        if (!name || !type || !status) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'All attributes (name, type, status) are required for PUT method'
            });
        }
        if (!validateStatus(res, status)) return;

        const device = await sequelize.transaction(async (transaction) => {
            const foundDevice = await Device.findByPk(req.params.id, { transaction });
            if (!foundDevice) return null;

            await foundDevice.update({ name, type, status }, { transaction });
            return foundDevice;
        });

        if (!device) return res.status(404).json({ error: `Device ID ${req.params.id} not found` });

        return res.status(200).json({
            message: 'Device data fully updated successfully',
            data: device.toJSON()
        });
    } catch (err) {
        return next(err);
    }
};

export const patchDevice = async (req, res, next) => {
    try {
        if (!isUuid(req.params.id)) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'Device ID must be a valid UUID'
            });
        }

        const updates = {};
        if (req.body.name !== undefined) updates.name = req.body.name;
        if (req.body.type !== undefined) updates.type = req.body.type;
        if (req.body.status !== undefined) updates.status = req.body.status;

        if (Object.keys(updates).length === 0) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'At least one field must be provided'
            });
        }
        if (!validateStatus(res, updates.status)) return;

        const device = await sequelize.transaction(async (transaction) => {
            const foundDevice = await Device.findByPk(req.params.id, { transaction });
            if (!foundDevice) return null;

            await foundDevice.update(updates, { transaction });
            return foundDevice;
        });

        if (!device) return res.status(404).json({ error: `Device ID ${req.params.id} not found` });

        return res.status(200).json({
            message: 'Device status updated successfully',
            data: device.toJSON()
        });
    } catch (err) {
        return next(err);
    }
};

export const deleteDevice = async (req, res, next) => {
    try {
        if (!isUuid(req.params.id)) {
            return res.status(400).json({
                error: 'Validation failed',
                details: 'Device ID must be a valid UUID'
            });
        }

        const deleted = await sequelize.transaction(async (transaction) => {
            const device = await Device.findByPk(req.params.id, { transaction });
            if (!device) return false;

            await device.destroy({ transaction });
            return true;
        });

        if (!deleted) return res.status(404).json({ error: `Device ID ${req.params.id} not found` });
        return res.status(204).send();
    } catch (err) {
        return next(err);
    }
};
