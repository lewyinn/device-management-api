import express from 'express';
import {
    createDevice,
    getDevices,
    getDeviceById,
    updateDevice,
    patchDevice,
    deleteDevice
} from '../controller/device.controller.js';
import {
    createTelemetry,
    getTelemetryByDevice,
    getLatestTelemetryByDevice
} from '../controller/telemetry.controller.js';

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
 *             $ref: '#/components/schemas/CreateDeviceRequest'
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
 *               $ref: '#/components/schemas/DeviceResponse'
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
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: Attribute 'name' is required
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Internal server error
 */
router.post('/', createDevice);

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
 *               $ref: '#/components/schemas/DeviceListResponse'
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
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: Query parameter 'page' or 'limit' must be a valid number
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Internal server error
 */
router.get('/', getDevices);

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
 *               $ref: '#/components/schemas/DeviceResponse'
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
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: Device ID must be a valid UUID
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Internal server error
 */
router.get('/:id', getDeviceById);

/**
 * @swagger
 * /devices/{device_id}/telemetry:
 *   post:
 *     summary: Create Device Telemetry
 *     description: Mencatat data telemetry device dengan format ts ala ThingsBoard dan values temperature/humidity.
 *     tags: [Telemetry]
 *     parameters:
 *       - in: path
 *         name: device_id
 *         required: true
 *         description: ID device.
 *         schema: { type: string, format: uuid }
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             $ref: '#/components/schemas/TelemetryRequest'
 *           example:
 *             values:
 *               temperature: 28.5
 *               humidity: 75.2
 *     responses:
 *       201:
 *         description: Created
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/TelemetryResponse'
 *             example:
 *               message: Telemetry successfully recorded
 *               data:
 *                 device_id: 550e8400-e29b-41d4-a716-446655440000
 *                 deviceName: Sensor-Suhu
 *                 deviceType: PM2120
 *                 data:
 *                   ts: 1717488000000
 *                   temperature: 28.5
 *                   humidity: 75.2
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: Attributes 'values.temperature' and 'values.humidity' must be numbers
 *       404:
 *         description: Device Not Found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       409:
 *         description: Duplicate Telemetry Timestamp
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Duplicate telemetry timestamp
 *               details: Telemetry for device ID 550e8400-e29b-41d4-a716-446655440000 at ts 1717488000000 already exists
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Internal server error
 */
router.post('/:id/telemetry', createTelemetry);

/**
 * @swagger
 * /devices/{device_id}/telemetry:
 *   get:
 *     summary: Get Device Telemetry
 *     description: Mengambil seluruh telemetry milik satu device, diurutkan dari ts terbaru.
 *     tags: [Telemetry]
 *     parameters:
 *       - in: path
 *         name: device_id
 *         required: true
 *         description: ID device.
 *         schema: { type: string, format: uuid }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/TelemetryListResponse'
 *             example:
 *               message: Success retrieving telemetry
 *               device_id: 550e8400-e29b-41d4-a716-446655440000
 *               deviceName: Sensor-Suhu
 *               deviceType: PM2120
 *               data:
 *                 - ts: 1717488000000
 *                   temperature: 28.5
 *                   humidity: 75.2
 *                 - ts: 1717484400000
 *                   temperature: 27.3
 *                   humidity: 80.1
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: Device ID must be a valid UUID
 *       404:
 *         description: Device Not Found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Internal server error
 */
router.get('/:id/telemetry', getTelemetryByDevice);

/**
 * @swagger
 * /devices/{device_id}/telemetry/latest:
 *   get:
 *     summary: Get Latest Device Telemetry
 *     description: Mengambil telemetry terbaru milik satu device berdasarkan nilai ts terbesar.
 *     tags: [Telemetry]
 *     parameters:
 *       - in: path
 *         name: device_id
 *         required: true
 *         description: ID device.
 *         schema: { type: string, format: uuid }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/TelemetryResponse'
 *             example:
 *               message: Latest telemetry found
 *               data:
 *                 device_id: 550e8400-e29b-41d4-a716-446655440000
 *                 deviceName: Sensor-Suhu
 *                 deviceType: PM2120
 *                 data:
 *                   ts: 1717488000000
 *                   temperature: 28.5
 *                   humidity: 75.2
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: Device ID must be a valid UUID
 *       404:
 *         description: Device or Telemetry Not Found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             examples:
 *               deviceNotFound:
 *                 summary: Device not found
 *                 value:
 *                   error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *               telemetryNotFound:
 *                 summary: Telemetry not found
 *                 value:
 *                   error: Telemetry for Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Internal server error
 */
router.get('/:id/telemetry/latest', getLatestTelemetryByDevice);

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
 *             $ref: '#/components/schemas/UpdateDeviceRequest'
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
 *               $ref: '#/components/schemas/DeviceResponse'
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
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: All attributes (name, type, status) are required for PUT method
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
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
 *             $ref: '#/components/schemas/PatchDeviceRequest'
 *           example:
 *             status: inactive
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/DeviceResponse'
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
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: At least one field must be provided
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
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
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Validation failed
 *               details: Device ID must be a valid UUID
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Device ID 550e8400-e29b-41d4-a716-446655440000 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Internal server error
 */
router.delete('/:id', deleteDevice);

export default router;