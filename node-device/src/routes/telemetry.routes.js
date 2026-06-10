import express from 'express';
import {
    createTelemetry,
    getTelemetryByDevice,
    getLatestTelemetryByDevice
} from '../controller/telemetry.controller.js';

const router = express.Router();

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
 *             type: object
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
 *               type: object
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
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: Attributes 'values.temperature' and 'values.humidity' must be numbers
 *       404:
 *         description: Device Not Found
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
 *       - in: query
 *         name: start_month
 *         required: false
 *         description: Bulan awal data telemetry dengan format YYYY-MM. Default bulan sekarang.
 *         schema: { type: string, example: "2026-06" }
 *       - in: query
 *         name: end_month
 *         required: false
 *         description: Bulan akhir data telemetry dengan format YYYY-MM. Default sama dengan start_month.
 *         schema: { type: string, example: "2026-06" }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
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
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: Device ID must be a valid UUID
 *       404:
 *         description: Device Not Found
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
 *               type: object
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
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: Device ID must be a valid UUID
 *       404:
 *         description: Device or Telemetry Not Found
 *         content:
 *           application/json:
 *             schema:
 *               type: object
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
 *               type: object
 *             example:
 *               error: Internal server error
 */
router.get('/:id/telemetry/latest', getLatestTelemetryByDevice);

export default router;
