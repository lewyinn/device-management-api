import {
    afterAll,
    beforeAll,
    beforeEach,
    describe,
    expect,
    test
} from '@jest/globals';

import dotenv from 'dotenv';
import request from 'supertest';

dotenv.config({
    path: '.env',
});

const { default: app } = await import('../../src/index.js');
const { default: database } = await import('../../src/db/index.js');

const { Device, sequelize } = database;

describe('Device API PostgreSQL integration', () => {
    beforeAll(async () => {
        if (!process.env.DATABASE_URL?.includes('devices_db')) {
            throw new Error(
                'Integration test must use the devices_db database'
            );
        }

        await database.authenticateSqlDatabase();

        await database.sync({ force: false });
    });

    // beforeEach(async () => {
    //     await Device.destroy({
    //         where: {},
    //         truncate: true
    //     });
    // });

    afterAll(async () => {
        await sequelize.close();
    });

    test('creates device and stores it in PostgreSQL', async () => {
        const response = await request(app)
            .post('/api/v1/devices')
            .send({
                name: 'Sensor-Suhu',
                type: 'DHT22',
                status: 'active'
            })
            .expect('Content-Type', /json/)
            .expect(201);

        expect(response.body.message).toBe(
            'Device successfully registered'
        );

        const storedDevice = await Device.findByPk(
            response.body.data.id
        );

        expect(storedDevice).not.toBeNull();
        expect(storedDevice.name).toBe('Sensor-Suhu');
        expect(storedDevice.type).toBe('DHT22');
        expect(storedDevice.status).toBe('active');
    });

    test('reads devices from PostgreSQL', async () => {
        await Device.create({
            name: 'Sensor-1',
            type: 'DHT22',
            status: 'active'
        });

        await Device.create({
            name: 'Sensor-2',
            type: 'DHT11',
            status: 'inactive'
        });

        const response = await request(app)
            .get('/api/v1/devices?page=1&limit=10')
            .expect(200);

        expect(response.body.message).toBe(
            'Success retrieving devices'
        );
    });

    test('updates device in PostgreSQL', async () => {
        const device = await Device.create({
            name: 'Sensor-Lama',
            type: 'DHT11',
            status: 'active'
        });

        const response = await request(app)
            .put(`/api/v1/devices/${device.id}`)
            .send({
                name: 'Sensor-Baru',
                type: 'DHT22',
                status: 'inactive'
            })
            .expect(200);

        expect(response.body.data.name).toBe('Sensor-Baru');
        expect(response.body.data.status).toBe('inactive');

        await device.reload();

        expect(device.name).toBe('Sensor-Baru');
        expect(device.type).toBe('DHT22');
        expect(device.status).toBe('inactive');
    });

    test('deletes device from PostgreSQL', async () => {
        const device = await Device.create({
            name: 'Sensor-Hapus',
            type: 'DHT22',
            status: 'active'
        });

        await request(app)
            .delete(`/api/v1/devices/${device.id}`)
            .expect(204);

        const deletedDevice = await Device.findByPk(device.id);

        expect(deletedDevice).toBeNull();
    });

    test('rejects invalid pagination', async () => {
        const response = await request(app)
            .get('/api/v1/devices?page=invalid&limit=10')
            .expect(400);

        expect(response.body).toEqual({
            error: 'Validation failed',
            details:
                "Query parameter 'page' or 'limit' must be a valid number"
        });
    });

    test('returns 404 when device does not exist', async () => {
        const deviceId =
            '550e8400-e29b-41d4-a716-446655440000';

        const response = await request(app)
            .get(`/api/v1/devices/${deviceId}`)
            .expect(404);

        expect(response.body).toEqual({
            error: `Device ID ${deviceId} not found`
        });
    });
});
