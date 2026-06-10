import db from '../db/index.js';

const TABLE_NAME = 'device_telemetries';
const { cassandraClient, cassandraTypes } = db;
const lastTimestampByDevice = new Map();

const nextTimestamp = (deviceId, ts) => {
    const lastTs = lastTimestampByDevice.get(deviceId) || 0;
    const nextTs = Math.max(ts, lastTs + 1);
    lastTimestampByDevice.set(deviceId, nextTs);
    return nextTs;
};

const recordMonth = (date) => {
    const year = date.getUTCFullYear();
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
};

const monthToDate = (month) => {
    const [year, monthNumber] = month.split('-').map(Number);
    return new Date(Date.UTC(year, monthNumber - 1, 1));
};

const monthsBetween = (startMonth, endMonth) => {
    const months = [];
    const cursor = monthToDate(startMonth);
    const end = monthToDate(endMonth);

    while (cursor <= end) {
        months.push(recordMonth(cursor));
        cursor.setUTCMonth(cursor.getUTCMonth() + 1);
    }

    return months;
};

const telemetryPoint = (row) => ({
    ts: row.ts.getTime(),
    temperature: row.temperature,
    humidity: row.humidity
});

export const insertTelemetry = async ({ deviceId, ts, temperature, humidity }) => {
    const safeTs = nextTimestamp(deviceId, ts);
    const tsDate = new Date(safeTs);
    const query = `
        INSERT INTO ${TABLE_NAME} (device_id, record_month, ts, temperature, humidity)
        VALUES (?, ?, ?, ?, ?)
    `;

    await cassandraClient.execute(
        query,
        [
            cassandraTypes.Uuid.fromString(deviceId),
            recordMonth(tsDate),
            tsDate,
            temperature,
            humidity
        ],
        { prepare: true }
    );

    return {
        ts: safeTs,
        temperature,
        humidity
    };
};

export const findTelemetryByMonthRange = async ({ deviceId, startMonth, endMonth }) => {
    const deviceUuid = cassandraTypes.Uuid.fromString(deviceId);
    const query = `
        SELECT ts, temperature, humidity
        FROM ${TABLE_NAME}
        WHERE device_id = ?
        AND record_month = ?
    `;

    const results = await Promise.all(
        monthsBetween(startMonth, endMonth).map((month) => cassandraClient.execute(
            query,
            [deviceUuid, month],
            { prepare: true }
        ))
    );

    return results
        .flatMap((result) => result.rows.map(telemetryPoint))
        .sort((left, right) => right.ts - left.ts);
};

export const findLatestTelemetry = async ({ deviceId, monthLookback = 3 }) => {
    const deviceUuid = cassandraTypes.Uuid.fromString(deviceId);
    const now = new Date();
    const query = `
        SELECT ts, temperature, humidity
        FROM ${TABLE_NAME}
        WHERE device_id = ?
        AND record_month = ?
        LIMIT 1
    `;

    for (let index = 0; index < monthLookback; index += 1) {
        const cursor = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - index, 1));
        const result = await cassandraClient.execute(
            query,
            [deviceUuid, recordMonth(cursor)],
            { prepare: true }
        );

        if (result.rows.length > 0) {
            return telemetryPoint(result.rows[0]);
        }
    }

    return null;
};