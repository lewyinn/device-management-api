import express from 'express';
import {
    createTelemetry,
    getTelemetryByDevice,
    getLatestTelemetryByDevice,
    getRecentTelemetry
} from '../controller/telemetry.controller.js';

const router = express.Router();
const recentTelemetryRouter = express.Router();

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
 *                   ts: generated_epoch_ms
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
router.post(
    '/:id/telemetry',
    createTelemetry
);

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
 *         description: Bulan awal data telemetry dengan format YYYY-MM. Default 2026-01.
 *         schema: { type: string, default: "2026-01", example: "2026-01" }
 *       - in: query
 *         name: end_month
 *         required: false
 *         description: Bulan akhir data telemetry dengan format YYYY-MM. Default 2026-12.
 *         schema: { type: string, default: "2026-12", example: "2026-12" }
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
 *                 - ts: generated_epoch_ms
 *                   temperature: 28.5
 *                   humidity: 75.2
 *                 - ts: generated_epoch_ms
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
router.get(
    '/:id/telemetry',
    getTelemetryByDevice
);

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
 *                   ts: generated_epoch_ms
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
router.get(
    '/:id/telemetry/latest',
    getLatestTelemetryByDevice
);


/**
 * @swagger
 * /telemetry/recent:
 *   get:
 *     summary: Get Recent Telemetry Across Devices
 *     description: Mengambil telemetry terbaru bulan berjalan dari seluruh device untuk initial load dashboard.
 *     tags: [Telemetry]
 *     parameters:
 *       - in: query
 *         name: limit
 *         required: false
 *         description: Jumlah telemetry yang dikembalikan.
 *         schema: { type: integer, default: 10, minimum: 1, maximum: 100 }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Latest telemetry retrieved
 *               data:
 *                 - device_id: 550e8400-e29b-41d4-a716-446655440000
 *                   device_name: Sensor-Suhu
 *                   device_type: PM2120
 *                   ts: 1781490918553
 *                   temperature: 27.47
 *                   humidity: 61.1
 *       400:
 *         description: Bad Request
 */
recentTelemetryRouter.get('/recent', getRecentTelemetry);

export default router;
export { recentTelemetryRouter };
