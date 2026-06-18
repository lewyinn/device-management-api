import db from '../db/index.js';

const { Device } = db;

export const findDeviceForTelemetry = async (deviceId) => {
    const device = await Device.findByPk(deviceId);
    if (!device) return null;

    return device.toJSON();
};

export const findAllDevicesForTelemetry = async () => {
    const devices = await Device.findAll({
        attributes: ['id', 'name', 'type', 'status'],
        order: [['name', 'ASC']]
    });

    return devices.map((device) => device.toJSON());
};
