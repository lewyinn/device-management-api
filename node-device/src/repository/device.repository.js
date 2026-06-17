import db from '../db/index.js';

const { Device } = db;

export const findDeviceForTelemetry = async (deviceId) => {
    const device = await Device.findByPk(deviceId);
    if (!device) return null;

    return device.toJSON();
};