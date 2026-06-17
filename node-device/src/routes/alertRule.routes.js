import express from 'express';
import {
    createAlertRule,
    deleteAlertRule,
    deleteAlertRuleState,
    getAlertRuleById,
    listAlertRules,
    listAlertRuleStates,
    patchAlertRule
} from '../controller/alertRule.controller.js';

const alertRuleRouter = express.Router();
const alertRuleStateRouter = express.Router();

/**
 * @swagger
 * /alert-rules:
 *   post:
 *     summary: Create Alert Rule
 *     description: Membuat dynamic telemetry alert rule sederhana. Satu rule berisi satu kondisi.
 *     tags: [Alert Rules]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *           examples:
 *             temperatureGreaterThan:
 *               summary: Temperature greater than
 *               value:
 *                 name: Suhu tinggi
 *                 description: Alert jika temperature lebih dari 38
 *                 metric_key: temperature
 *                 operator: ">"
 *                 threshold_value: 38
 *                 severity: critical
 *                 enabled: true
 *                 cooldown_seconds: 300
 *                 message_template: Device {{deviceName}} temperature {{temperature}}C trigger rule {{ruleName}} pada {{time}}
 *             humidityLessThanForDeviceType:
 *               summary: Humidity less than for device type
 *               value:
 *                 name: Humidity rendah PM2120
 *                 device_type: PM2120
 *                 metric_key: humidity
 *                 operator: "<"
 *                 threshold_value: 10
 *                 severity: warning
 *                 enabled: true
 *                 cooldown_seconds: 300
 *                 message_template: Device {{deviceName}} humidity {{humidity}}% kurang dari {{threshold}} pada {{time}}
 *             temperatureBetween:
 *               summary: Temperature between
 *               value:
 *                 name: Suhu di range normal
 *                 metric_key: temperature
 *                 operator: between
 *                 threshold_value: 20
 *                 second_threshold_value: 30
 *                 severity: info
 *                 enabled: true
 *                 cooldown_seconds: 300
 *                 message_template: Device {{deviceName}} temperature {{temperature}}C berada di range {{threshold}} sampai {{secondThreshold}}
 *     responses:
 *       201:
 *         description: Created
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Alert rule successfully created
 *               data:
 *                 id: rule_4k9m2xq8
 *                 name: Suhu tinggi
 *                 description: Alert jika temperature lebih dari 38
 *                 device_id: null
 *                 device_type: null
 *                 metric_key: temperature
 *                 operator: ">"
 *                 threshold_value: 38
 *                 second_threshold_value: null
 *                 severity: critical
 *                 enabled: true
 *                 cooldown_seconds: 300
 *                 message_template: Device {{deviceName}} temperature {{temperature}}C trigger rule {{ruleName}} pada {{time}}
 *       400:
 *         description: Bad Request
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               error: Validation failed
 *               details: Attribute 'metric_key' is required
 */
alertRuleRouter.post('/', createAlertRule);

/**
 * @swagger
 * /alert-rules:
 *   get:
 *     summary: Get Alert Rules
 *     description: Mengambil daftar alert rules sederhana dengan pagination.
 *     tags: [Alert Rules]
 *     parameters:
 *       - in: query
 *         name: page
 *         required: false
 *         schema: { type: integer, default: 1 }
 *       - in: query
 *         name: limit
 *         required: false
 *         schema: { type: integer, default: 10 }
 *       - in: query
 *         name: enabled
 *         required: false
 *         schema: { type: boolean }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Success retrieving alert rules
 *               meta:
 *                 page: 1
 *                 limit: 10
 *                 total_data: 1
 *                 total_pages: 1
 *               data:
 *                 - id: rule_4k9m2xq8
 *                   name: Suhu tinggi
 *                   metric_key: temperature
 *                   operator: ">"
 *                   threshold_value: 38
 *                   severity: critical
 *                   enabled: true
 */
alertRuleRouter.get('/', listAlertRules);

/**
 * @swagger
 * /alert-rules/{id}:
 *   get:
 *     summary: Get Alert Rule Detail
 *     description: Mengambil detail satu alert rule.
 *     tags: [Alert Rules]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: string, example: rule_4k9m2xq8 }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Alert rule found
 *               data:
 *                 id: rule_4k9m2xq8
 *                 name: Suhu tinggi
 *                 metric_key: temperature
 *                 operator: ">"
 *                 threshold_value: 38
 *                 severity: critical
 *                 enabled: true
 *       404:
 *         description: Not Found
 */
alertRuleRouter.get('/:id', getAlertRuleById);

/**
 * @swagger
 * /alert-rules/{id}:
 *   patch:
 *     summary: Patch Alert Rule
 *     description: Mengubah sebagian data alert rule.
 *     tags: [Alert Rules]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: string, example: rule_4k9m2xq8 }
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *           examples:
 *             disable:
 *               summary: Disable rule
 *               value:
 *                 enabled: false
 *             updateThreshold:
 *               summary: Update threshold
 *               value:
 *                 threshold_value: 40
 *                 cooldown_seconds: 600
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Alert rule updated successfully
 *               data:
 *                 id: rule_4k9m2xq8
 *                 threshold_value: 40
 *                 cooldown_seconds: 600
 */
alertRuleRouter.patch('/:id', patchAlertRule);

/**
 * @swagger
 * /alert-rules/{id}:
 *   delete:
 *     summary: Delete Alert Rule
 *     description: Menghapus alert rule dan cooldown state miliknya.
 *     tags: [Alert Rules]
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema: { type: string, example: rule_4k9m2xq8 }
 *     responses:
 *       204:
 *         description: No Content
 */
alertRuleRouter.delete('/:id', deleteAlertRule);

/**
 * @swagger
 * /alert-rule-states:
 *   get:
 *     summary: Get Alert Rule States
 *     description: Mengambil cooldown state alert rule per device.
 *     tags: [Alert Rule States]
 *     parameters:
 *       - in: query
 *         name: rule_id
 *         required: false
 *         schema: { type: string, example: rule_4k9m2xq8 }
 *       - in: query
 *         name: device_id
 *         required: false
 *         schema: { type: string, format: uuid }
 *     responses:
 *       200:
 *         description: OK
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *             example:
 *               message: Success retrieving alert rule states
 *               data:
 *                 - rule_id: rule_4k9m2xq8
 *                   device_id: 4ebe37b9-d09a-430b-b336-59a155709d1b
 *                   last_triggered_at: 2026-06-17T03:45:00.000Z
 *                   last_telemetry_ts: 1781490918553
 */
alertRuleStateRouter.get('/', listAlertRuleStates);

/**
 * @swagger
 * /alert-rule-states/{ruleId}/{deviceId}:
 *   delete:
 *     summary: Reset Alert Rule Cooldown State
 *     description: Menghapus cooldown state untuk satu rule dan satu device supaya alert bisa trigger lagi.
 *     tags: [Alert Rule States]
 *     parameters:
 *       - in: path
 *         name: ruleId
 *         required: true
 *         schema: { type: string, example: rule_4k9m2xq8 }
 *       - in: path
 *         name: deviceId
 *         required: true
 *         schema: { type: string, format: uuid }
 *     responses:
 *       204:
 *         description: No Content
 */
alertRuleStateRouter.delete('/:ruleId/:deviceId', deleteAlertRuleState);

export {
    alertRuleRouter,
    alertRuleStateRouter
};
