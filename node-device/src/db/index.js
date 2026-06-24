import { Sequelize } from 'sequelize';
import cassandra from 'cassandra-driver';
import dotenv from 'dotenv';
import defineDevice from './models/Device.js';

dotenv.config({ quiet: true });

const TELEMETRY_TABLE_NAME = 'device_telemetries';

const numberFromEnv = (key, fallback) => Number(process.env[key] || fallback);

const sqlPool = {
    max: numberFromEnv('DB_POOL_MAX', 10),
    min: numberFromEnv('DB_POOL_MIN', 0),
    acquire: numberFromEnv('DB_POOL_ACQUIRE', 30000),
    idle: numberFromEnv('DB_POOL_IDLE', 10000)
};

const sqlLogging = process.env.DB_LOGGING === 'true' ? console.log : false;

const sequelize = process.env.DATABASE_URL
    ? new Sequelize(process.env.DATABASE_URL, {
        logging: sqlLogging,
        pool: sqlPool
    })
    : new Sequelize({
        dialect: process.env.DB_DIALECT || 'sqlite',
        storage: process.env.DB_STORAGE || ':memory:',
        logging: sqlLogging,
        pool: sqlPool
    });

const Device = defineDevice(sequelize);

const cassandraContactPoints = (process.env.CASSANDRA_CONTACT_POINTS || '127.0.0.1')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
const cassandraLocalDataCenter = process.env.CASSANDRA_LOCAL_DATACENTER || 'datacenter1';
const cassandraKeyspace = process.env.CASSANDRA_KEYSPACE || 'device_management';

const cassandraAdminClient = new cassandra.Client({
    contactPoints: cassandraContactPoints,
    localDataCenter: cassandraLocalDataCenter
});

const cassandraClient = new cassandra.Client({
    contactPoints: cassandraContactPoints,
    localDataCenter: cassandraLocalDataCenter,
    keyspace: cassandraKeyspace
});

const authenticateSqlDatabase = async () => {
    await sequelize.authenticate();
    console.log('SQL database connection established');
};

const syncSqlDatabase = async (options = {}) => {
    await sequelize.sync(options);
    console.log('SQL database synced');
};

const connectCassandra = async () => {
    await cassandraAdminClient.execute(`
        CREATE KEYSPACE IF NOT EXISTS ${cassandraKeyspace}
        WITH replication = {
            'class': 'NetworkTopologyStrategy',
            '${cassandraLocalDataCenter}': 1
        }
        AND durable_writes = true
    `);

    await cassandraClient.connect();
    await cassandraClient.execute(`
        CREATE TABLE IF NOT EXISTS ${TELEMETRY_TABLE_NAME} (
            device_id uuid,
            record_month text,
            ts bigint,
            temperature double,
            humidity double,
            PRIMARY KEY ((device_id, record_month), ts)
        ) WITH CLUSTERING ORDER BY (ts DESC)
    `);

    const tsColumn = await cassandraClient.execute(
        `
            SELECT type
            FROM system_schema.columns
            WHERE keyspace_name = ?
            AND table_name = ?
            AND column_name = ?
        `,
        [cassandraKeyspace, TELEMETRY_TABLE_NAME, 'ts'],
        { prepare: true }
    );
    const tsType = tsColumn.rows[0]?.type;

    if (tsType !== 'bigint') {
        throw new Error(`${TELEMETRY_TABLE_NAME}.ts must be bigint, current type is ${tsType || 'missing'}`);
    }

    console.log('Cassandra connection established');
};

const closeDatabases = async () => {
    const results = await Promise.allSettled([
        sequelize.close(),
        cassandraClient.shutdown(),
        cassandraAdminClient.shutdown()
    ]);
    const rejected = results.find((result) => result.status === 'rejected');

    if (rejected) {
        throw rejected.reason;
    }
};

export default {
    sequelize,
    Device,
    cassandraClient,
    cassandraTypes: cassandra.types,
    authenticateSqlDatabase,
    sync: syncSqlDatabase,
    connectCassandra,
    closeDatabases
};
