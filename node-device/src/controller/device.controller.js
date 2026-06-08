import db from '../db/index.js';
import { isUuid, isValidStatus } from '../utils/validation.js';

const { sequelize, Device } = db;

const validationFailed = (res, details) => res.status(400).json({
    error: 'Validation failed',
    details
});

const invalidDeviceId = (res) => validationFailed(res, 'Device ID must be a valid UUID');

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

const validateStatus = (res, status) => {
    if (status !== undefined && !isValidStatus(status)) {
        validationFailed(res, "Status must be 'active' or 'inactive'");
        return false;
    }

    return true;
};

export const createDevice = async (req, res, next) => {
    try {
        const { name, type, status } = req.body;
        if (!name || !type) {
            return validationFailed(res, !name ? "Attribute 'name' is required" : "Attribute 'type' is required");
        }
        if (!validateStatus(res, status)) return;

        const device = await sequelize.transaction(async (transaction) => {
            return Device.create({ name, type, status }, { transaction });
        });

        return res.status(201).json({
            message: 'Device successfully registered',
            data: device.toJSON()
        });
    } catch (err) {
        return next(err);
    }
};

export const getDevices = async (req, res, next) => {
    try {
        const pagination = getPagination(req.query.page, req.query.limit);
        if (!pagination) {
            return validationFailed(res, "Query parameter 'page' or 'limit' must be a valid number");
        }

        const { pageNum, limitNum, offset } = pagination;
        const { count, rows } = await Device.findAndCountAll({
            offset,
            limit: limitNum,
            order: [['name', 'ASC']]
        });

        return res.status(200).json({
            message: 'Success retrieving devices',
            meta: {
                page: pageNum,
                limit: limitNum,
                total_data: count,
                total_pages: Math.ceil(count / limitNum)
            },
            data: rows
        });
    } catch (err) {
        return next(err);
    }
};

export const getDeviceById = async (req, res, next) => {
    try {
        if (!isUuid(req.params.id)) return invalidDeviceId(res);

        const device = await Device.findByPk(req.params.id);
        if (!device) return res.status(404).json({ error: `Device ID ${req.params.id} not found` });

        return res.status(200).json({ message: 'Device found', data: device.toJSON() });
    } catch (err) {
        return next(err);
    }
};

export const updateDevice = async (req, res, next) => {
    try {
        if (!isUuid(req.params.id)) return invalidDeviceId(res);

        const { name, type, status } = req.body;
        if (!name || !type || !status) {
            return validationFailed(res, 'All attributes (name, type, status) are required for PUT method');
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
        if (!isUuid(req.params.id)) return invalidDeviceId(res);

        const updates = {};
        if (req.body.name !== undefined) updates.name = req.body.name;
        if (req.body.type !== undefined) updates.type = req.body.type;
        if (req.body.status !== undefined) updates.status = req.body.status;

        if (Object.keys(updates).length === 0) {
            return validationFailed(res, 'At least one field must be provided');
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
        if (!isUuid(req.params.id)) return invalidDeviceId(res);

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
