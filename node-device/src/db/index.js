import { Sequelize } from 'sequelize';
import cassandra from 'cassandra-driver';
import defineDevice from './models/Device.js';
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

const cassandraContactPoints = (process.env.CASSANDRA_CONTACT_POINTS || '127.0.0.1')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);

const cassandraClient = new cassandra.Client({
    contactPoints: cassandraContactPoints,
    localDataCenter: process.env.CASSANDRA_LOCAL_DATACENTER || 'datacenter1',
    keyspace: process.env.CASSANDRA_KEYSPACE || 'device_management'
});

export default {
    sequelize,
    Device,
    cassandraClient,
    cassandraTypes: cassandra.types,
    sync: async (options = {}) => {
        await sequelize.sync(options);
        console.log('Database synced');
    }
};
