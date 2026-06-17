import express from 'express';
import {
    createDevice,
    getDevices,
    getDeviceById,
    getLongPollingDevices,
    getShortPollingDevices,
    updateDevice,
    patchDevice,
    deleteDevice
} from '../controller/device.controller.js';
import {
    deviceReadRateLimiter,
    deviceReadThrottler,
    deviceRegistrationRateLimiter,
    deviceRegistrationThrottler
} from '../middleware/deviceHttpProtection.middleware.js';

const router = express.Router();

/**
 * @swagger
 * /devices:
 *   post:
 *     summary: Create Device
 *     description: Mendaftarkan perangkat baru ke dalam sistem.
 *     tags: [Devices]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *           example:
 *             name: Sensor-Suhu
 *             type: PM2120
 *             status: active
 *     responses:
 *       201:
 *         description: Created
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Device successfully registered
 *               data:
 *                 id: 550e8400-e29b-41d4-a716-446655440000
 *                 name: Sensor-Suhu
 *                 type: PM2120
 *                 status: active
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: Attribute 'name' is required
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Internal server error
 */
router.post(
    '/',
    // deviceRegistrationRateLimiter,
    deviceRegistrationThrottler,
    createDevice
);

/**
 * @swagger
 * /devices:
 *   get:
 *     summary: Read All Devices
 *     description: Mengambil daftar seluruh perangkat dengan pagination.
 *     tags: [Devices]
 *     parameters:
 *       - in: query
 *         name: page
 *         required: false
 *         description: Nomor halaman.
 *         schema: { type: integer, default: 1 }
 *       - in: query
 *         name: limit
 *         required: false
 *         description: Jumlah data per halaman, maksimal 50.
 *         schema: { type: integer, default: 10 }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Success retrieving devices
 *               meta:
 *                 page: 1
 *                 limit: 10
 *                 total_data: 50
 *                 total_pages: 5
 *               data:
 *                 - id: 550e8400-e29b-41d4-a716-446655440000
 *                   name: Sensor-Suhu
 *                   type: PM2120
 *                   status: active
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: Query parameter 'page' or 'limit' must be a valid number
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Internal server error
 */
router.get(
    '/',
    // deviceReadThrottler,
    deviceReadRateLimiter,
    getDevices
);

// router.get(
//     '/short-poll',
//     getShortPollingDevices
// );

// router.get(
//     '/long-poll',
//     getLongPollingDevices
// );

/**
 * @swagger
 * /devices/{device_id}:
 *   get:
 *     summary: Read Device Detail
 *     description: Mengambil detail satu perangkat berdasarkan ID.
 *     tags: [Devices]
 *     parameters:
 *       - in: path
 *         name: device_id
 *         required: true
 *         description: ID perangkat.
 *         schema: { type: string, format: uuid }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Device found
 *               data:
 *                 id: 550e8400-e29b-41d4-a716-446655440000
 *                 name: Sensor-Suhu
 *                 type: PM2120
 *                 status: active
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: Device ID must be a valid UUID
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Internal server error
 */
router.get(
    '/:id',
    deviceReadRateLimiter,
    getDeviceById
);

/**
 * @swagger
 * /devices/{device_id}:
 *   put:
 *     summary: Update Device All
 *     description: Mengubah seluruh data atribut perangkat sekaligus. Semua atribut name, type, dan status wajib dikirimkan.
 *     tags: [Devices]
 *     parameters:
 *       - in: path
 *         name: device_id
 *         required: true
 *         description: ID perangkat.
 *         schema: { type: string, format: uuid }
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *           example:
 *             name: Sensor-Suhu
 *             type: PM2120
 *             status: inactive
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Device data fully updated successfully
 *               data:
 *                 id: 550e8400-e29b-41d4-a716-446655440000
 *                 name: Sensor-Suhu
 *                 type: PM2120
 *                 status: inactive
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: All attributes (name, type, status) are required for PUT method
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Internal server error
 */
router.put('/:id', updateDevice);

/**
 * @swagger
 * /devices/{device_id}:
 *   patch:
 *     summary: Update Device Status
 *     description: Mengubah status atau sebagian data perangkat.
 *     tags: [Devices]
 *     parameters:
 *       - in: path
 *         name: device_id
 *         required: true
 *         description: ID perangkat.
 *         schema: { type: string, format: uuid }
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *           example:
 *             status: inactive
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Device status updated successfully
 *               data:
 *                 id: 550e8400-e29b-41d4-a716-446655440000
 *                 name: Sensor-Suhu
 *                 type: PM2120
 *                 status: inactive
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: At least one field must be provided
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Internal server error
 */
router.patch('/:id', patchDevice);

/**
 * @swagger
 * /devices/{device_id}:
 *   delete:
 *     summary: Delete Device
 *     description: Menghapus perangkat dari sistem.
 *     tags: [Devices]
 *     parameters:
 *       - in: path
 *         name: device_id
 *         required: true
 *         description: ID perangkat.
 *         schema: { type: string, format: uuid }
 *     responses:
 *       204:
 *         description: No Content
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: Device ID must be a valid UUID
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Internal server error
 */
router.delete('/:id', deleteDevice);

export default router;