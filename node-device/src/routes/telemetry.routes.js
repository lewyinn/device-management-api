import express from 'express';
import { deleteTelemetry } from '../controller/telemetry.controller.js';

const router = express.Router();

/**
 * @swagger
 * /telemetry/{id}:
 *   delete:
 *     summary: Delete Telemetry
 *     description: Menghapus data telemetry berdasarkan ID transaksi.
 *     tags: [Telemetry]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         description: ID telemetry.
 *         schema: { type: integer }
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
 *               details: Telemetry ID must be a valid number
 *       404:
 *         description: Not Found
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Telemetry ID 1 not found
 *       500:
 *         description: Internal server error
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *             example:
 *               error: Internal server error
 */
router.delete('/:id', deleteTelemetry);

export default router;
