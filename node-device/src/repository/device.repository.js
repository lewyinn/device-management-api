import db from '../db/index.js';

const { Device } = db;
const CACHE_TTL_MS = Number(process.env.DEVICE_CACHE_TTL_MS || 300000);
const deviceCache = new Map();

const fromCache = (deviceId) => {
    const cached = deviceCache.get(deviceId);
    if (!cached) return null;

    if (cached.expiresAt <= Date.now()) {
        deviceCache.delete(deviceId);
        return null;
    }

    return cached.device;
};

const saveCache = (device) => {
    const value = {
        id: device.id,
        name: device.name,
        type: device.type,
        status: device.status
    };

    deviceCache.set(device.id, {
        device: value,
        expiresAt: Date.now() + CACHE_TTL_MS
    });

    return value;
};

export const findDeviceForTelemetry = async (deviceId) => {
    const cached = fromCache(deviceId);
    if (cached) return cached;

    const device = await Device.findByPk(deviceId);
    if (!device) return null;

    return saveCache(device.toJSON());
};

export const clearDeviceCache = (deviceId) => {
    deviceCache.delete(deviceId);
};
