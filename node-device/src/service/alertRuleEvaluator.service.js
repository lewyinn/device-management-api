import db from '../db/index.js';
import { sendTelegramMessage } from './telegram.service.js';
import { broadcastAlertTriggered } from '../websocket/websocketServer.js';

const { AlertRule, AlertRuleState } = db;

const escapeHtml = (value) => String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');

const formatDate = (timestamp) => new Date(timestamp).toLocaleString('id-ID', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
});

const isRuleScopedToDevice = (rule, device) => {
    if (rule.deviceId) return rule.deviceId === device.id;
    if (rule.deviceType) return rule.deviceType === device.type;
    return true;
};

const evaluateRule = (rule, telemetry) => {
    const actualValue = telemetry[rule.metricKey];
    if (typeof actualValue !== 'number' || !Number.isFinite(actualValue)) {
        return false;
    }

    switch (rule.operator) {
        case '>':
            return actualValue > rule.thresholdValue;
        case '>=':
            return actualValue >= rule.thresholdValue;
        case '<':
            return actualValue < rule.thresholdValue;
        case '<=':
            return actualValue <= rule.thresholdValue;
        case '==':
            return actualValue === rule.thresholdValue;
        case 'between':
            return actualValue >= rule.thresholdValue && actualValue <= rule.secondThresholdValue;
        default:
            return false;
    }
};

const isCooldownActive = async (rule, deviceId) => {
    if (rule.cooldownSeconds <= 0) return false;

    const state = await AlertRuleState.findOne({
        where: { ruleId: rule.id, deviceId }
    });
    if (!state) return false;

    const elapsedMs = Date.now() - new Date(state.lastTriggeredAt).getTime();
    return elapsedMs < rule.cooldownSeconds * 1000;
};

const renderTemplate = ({ rule, device, telemetry }) => {
    const condition = rule.operator === 'between'
        ? `${rule.metricKey} between ${rule.thresholdValue} and ${rule.secondThresholdValue}`
        : `${rule.metricKey} ${rule.operator} ${rule.thresholdValue}`;
    const variables = {
        ruleName: rule.name,
        severity: rule.severity,
        deviceId: device.id,
        deviceName: device.name,
        deviceType: device.type,
        ts: telemetry.ts,
        time: formatDate(telemetry.ts),
        temperature: telemetry.temperature,
        humidity: telemetry.humidity,
        metric: rule.metricKey,
        value: telemetry[rule.metricKey],
        operator: rule.operator,
        threshold: rule.thresholdValue,
        secondThreshold: rule.secondThresholdValue ?? '',
        condition
    };

    return rule.messageTemplate.replace(/\{\{(\w+)}}/g, (match, key) => (
        Object.prototype.hasOwnProperty.call(variables, key)
            ? escapeHtml(variables[key])
            : match
    ));
};

export const evaluateTelemetryAlerts = async ({ device, telemetry }) => {
    const rules = await AlertRule.findAll({
        where: { enabled: true },
        order: [['createdAt', 'ASC']]
    });

    for (const rule of rules) {
        if (!isRuleScopedToDevice(rule, device)) {
            continue;
        }

        if (!evaluateRule(rule, telemetry)) {
            continue;
        }

        if (await isCooldownActive(rule, device.id)) {
            continue;
        }

        const message = renderTemplate({ rule, device, telemetry });

        try {
            await sendTelegramMessage(message);
        } catch (error) {
            console.error('Telegram alert notification failed:', error.message);
        }

        await AlertRuleState.upsert({
            ruleId: rule.id,
            deviceId: device.id,
            lastTriggeredAt: new Date(),
            lastTelemetryTs: telemetry.ts
        });

        broadcastAlertTriggered({
            rule_id: rule.id,
            rule_name: rule.name,
            severity: rule.severity,
            device_id: device.id,
            device_name: device.name,
            device_type: device.type,
            metric: rule.metricKey,
            value: telemetry[rule.metricKey],
            operator: rule.operator,
            threshold: rule.thresholdValue,
            second_threshold: rule.secondThresholdValue,
            message,
            ts: telemetry.ts
        });
    }
};
