import { Sequelize } from 'sequelize';
import defineDevice from './models/Device.js';
import defineDeviceTelemetry from './models/DeviceTelemetry.js';
import dotenv from 'dotenv';

dotenv.config({ quiet: true });

const logging = process.env.DB_LOGGING === 'true' ? console.log : false;
const pool = {
    max: Number(process.env.DB_POOL_MAX || 10),
    min: Number(process.env.DB_POOL_MIN || 0),
    acquire: Number(process.env.DB_POOL_ACQUIRE || 30000),
    idle: Number(process.env.DB_POOL_IDLE || 10000)
};

const sequelize = process.env.DATABASE_URL
    ? new Sequelize(process.env.DATABASE_URL, { logging, pool })
    : new Sequelize({
        dialect: process.env.DB_DIALECT || 'sqlite',
        storage: process.env.DB_STORAGE || ':memory:',
        logging,
        pool
    });

const Device = defineDevice(sequelize);
const DeviceTelemetry = defineDeviceTelemetry(sequelize);

Device.hasMany(DeviceTelemetry, {
    foreignKey: 'device_id',
    as: 'telemetries',
    onDelete: 'CASCADE'
});

DeviceTelemetry.belongsTo(Device, {
    foreignKey: 'device_id',
    as: 'device'
});

export default {
    sequelize,
    Device,
    DeviceTelemetry,
    sync: async (options = {}) => {
        await sequelize.sync(options);
        console.log('Database synced');
    }
};
