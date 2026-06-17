import db from '../db/index.js';

const { AlertRule, AlertRuleState } = db;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const ALERT_RULE_ID_PATTERN = /^rule_[a-z0-9_-]{6,24}$/i;
const SEVERITIES = ['info', 'warning', 'critical'];
const OPERATORS = ['>', '>=', '<', '<=', '==', 'between'];

const isUuid = (value) => UUID_PATTERN.test(value);
const isAlertRuleId = (value) => ALERT_RULE_ID_PATTERN.test(value);
const isNumber = (value) => typeof value === 'number' && Number.isFinite(value);

const validationError = (res, details) => res.status(400).json({
    error: 'Validation failed',
    details
});

const notFound = (res, message) => res.status(404).json({ error: message });

const getPagination = (page = '1', limit = '10') => {
    const pageNumber = Number(page);
    const limitNumber = Number(limit);

    if (!Number.isInteger(pageNumber) || pageNumber < 1 || !Number.isInteger(limitNumber) || limitNumber < 1) {
        return null;
    }

    const normalizedLimit = Math.min(limitNumber, 50);
    return {
        page: pageNumber,
        limit: normalizedLimit,
        offset: (pageNumber - 1) * normalizedLimit
    };
};

const formatRule = (rule) => {
    const data = rule.toJSON();
    return {
        id: data.id,
        name: data.name,
        description: data.description,
        device_id: data.deviceId,
        device_type: data.deviceType,
        metric_key: data.metricKey,
        operator: data.operator,
        threshold_value: data.thresholdValue,
        second_threshold_value: data.secondThresholdValue,
        severity: data.severity,
        enabled: data.enabled,
        cooldown_seconds: data.cooldownSeconds,
        message_template: data.messageTemplate,
        created_at: data.createdAt,
        updated_at: data.updatedAt
    };
};

const formatState = (state) => {
    const data = state.toJSON();
    return {
        rule_id: data.ruleId,
        device_id: data.deviceId,
        last_triggered_at: data.lastTriggeredAt,
        last_telemetry_ts: Number(data.lastTelemetryTs)
    };
};

const validateRulePayload = (payload, current = {}, partial = false) => {
    const next = {};

    if (!partial || payload.name !== undefined) {
        if (!payload.name) return { error: "Attribute 'name' is required" };
        next.name = payload.name;
    }

    if (payload.description !== undefined) next.description = payload.description;

    if (payload.device_id !== undefined) {
        if (payload.device_id !== null && !isUuid(payload.device_id)) {
            return { error: "Attribute 'device_id' must be a valid UUID or null" };
        }
        next.deviceId = payload.device_id;
    }

    if (payload.device_type !== undefined) {
        next.deviceType = payload.device_type;
    }

    const effectiveDeviceId = next.deviceId !== undefined ? next.deviceId : current.deviceId;
    const effectiveDeviceType = next.deviceType !== undefined ? next.deviceType : current.deviceType;
    if (effectiveDeviceId && effectiveDeviceType) {
        return { error: "Use either 'device_id' or 'device_type', not both" };
    }

    if (!partial || payload.metric_key !== undefined) {
        if (!payload.metric_key) return { error: "Attribute 'metric_key' is required" };
        next.metricKey = payload.metric_key;
    }

    if (!partial || payload.operator !== undefined) {
        if (!OPERATORS.includes(payload.operator)) {
            return { error: "Attribute 'operator' must be >, >=, <, <=, ==, or between" };
        }
        next.operator = payload.operator;
    }

    if (!partial || payload.threshold_value !== undefined) {
        if (!isNumber(payload.threshold_value)) return { error: "Attribute 'threshold_value' must be a number" };
        next.thresholdValue = payload.threshold_value;
    }

    if (payload.second_threshold_value !== undefined) {
        if (payload.second_threshold_value !== null && !isNumber(payload.second_threshold_value)) {
            return { error: "Attribute 'second_threshold_value' must be a number or null" };
        }
        next.secondThresholdValue = payload.second_threshold_value;
    }

    const effectiveOperator = next.operator || current.operator;
    const effectiveSecondThreshold = next.secondThresholdValue !== undefined
        ? next.secondThresholdValue
        : current.secondThresholdValue;

    if (effectiveOperator === 'between' && !isNumber(effectiveSecondThreshold)) {
        return { error: "Attribute 'second_threshold_value' is required when operator is between" };
    }
    if (effectiveOperator !== 'between') {
        next.secondThresholdValue = null;
    }

    if (!partial || payload.severity !== undefined) {
        const severity = payload.severity || 'warning';
        if (!SEVERITIES.includes(severity)) return { error: "Attribute 'severity' must be info, warning, or critical" };
        next.severity = severity;
    }

    if (payload.enabled !== undefined) {
        if (typeof payload.enabled !== 'boolean') return { error: "Attribute 'enabled' must be boolean" };
        next.enabled = payload.enabled;
    } else if (!partial) {
        next.enabled = true;
    }

    if (!partial || payload.cooldown_seconds !== undefined) {
        const cooldownSeconds = payload.cooldown_seconds ?? 300;
        if (!Number.isInteger(cooldownSeconds) || cooldownSeconds < 0) {
            return { error: "Attribute 'cooldown_seconds' must be a non-negative integer" };
        }
        next.cooldownSeconds = cooldownSeconds;
    }

    if (!partial || payload.message_template !== undefined) {
        if (!payload.message_template) return { error: "Attribute 'message_template' is required" };
        next.messageTemplate = payload.message_template;
    }

    return { data: next };
};

export const createAlertRule = async (req, res, next) => {
    try {
        const { data, error } = validateRulePayload(req.body);
        if (error) return validationError(res, error);

        const rule = await AlertRule.create(data);
        return res.status(201).json({
            message: 'Alert rule successfully created',
            data: formatRule(rule)
        });
    } catch (error) {
        return next(error);
    }
};

export const listAlertRules = async (req, res, next) => {
    try {
        const pagination = getPagination(req.query.page, req.query.limit);
        if (!pagination) return validationError(res, "Query parameter 'page' or 'limit' must be a valid number");

        const where = {};
        if (req.query.enabled !== undefined) {
            if (!['true', 'false'].includes(req.query.enabled)) return validationError(res, "Query parameter 'enabled' must be true or false");
            where.enabled = req.query.enabled === 'true';
        }

        const { count, rows } = await AlertRule.findAndCountAll({
            where,
            offset: pagination.offset,
            limit: pagination.limit,
            order: [['createdAt', 'DESC']]
        });

        return res.status(200).json({
            message: 'Success retrieving alert rules',
            meta: {
                page: pagination.page,
                limit: pagination.limit,
                total_data: count,
                total_pages: Math.ceil(count / pagination.limit)
            },
            data: rows.map(formatRule)
        });
    } catch (error) {
        return next(error);
    }
};

export const getAlertRuleById = async (req, res, next) => {
    try {
        if (!isAlertRuleId(req.params.id)) return validationError(res, 'Alert rule ID must be a valid rule identifier');

        const rule = await AlertRule.findByPk(req.params.id);
        if (!rule) return notFound(res, `Alert rule ID ${req.params.id} not found`);

        return res.status(200).json({ message: 'Alert rule found', data: formatRule(rule) });
    } catch (error) {
        return next(error);
    }
};

export const patchAlertRule = async (req, res, next) => {
    try {
        if (!isAlertRuleId(req.params.id)) return validationError(res, 'Alert rule ID must be a valid rule identifier');
        if (Object.keys(req.body).length === 0) return validationError(res, 'At least one field must be provided');

        const rule = await AlertRule.findByPk(req.params.id);
        if (!rule) return notFound(res, `Alert rule ID ${req.params.id} not found`);

        const { data, error } = validateRulePayload(req.body, rule.toJSON(), true);
        if (error) return validationError(res, error);

        await rule.update(data);
        return res.status(200).json({ message: 'Alert rule updated successfully', data: formatRule(rule) });
    } catch (error) {
        return next(error);
    }
};

export const deleteAlertRule = async (req, res, next) => {
    try {
        if (!isAlertRuleId(req.params.id)) return validationError(res, 'Alert rule ID must be a valid rule identifier');

        const deleted = await AlertRule.destroy({ where: { id: req.params.id } });
        if (!deleted) return notFound(res, `Alert rule ID ${req.params.id} not found`);

        return res.status(204).send();
    } catch (error) {
        return next(error);
    }
};

export const listAlertRuleStates = async (req, res, next) => {
    try {
        const where = {};
        if (req.query.rule_id) {
            if (!isAlertRuleId(req.query.rule_id)) return validationError(res, "Query parameter 'rule_id' must be a valid rule identifier");
            where.ruleId = req.query.rule_id;
        }
        if (req.query.device_id) {
            if (!isUuid(req.query.device_id)) return validationError(res, "Query parameter 'device_id' must be a valid UUID");
            where.deviceId = req.query.device_id;
        }

        const states = await AlertRuleState.findAll({
            where,
            order: [['lastTriggeredAt', 'DESC']]
        });
        return res.status(200).json({
            message: 'Success retrieving alert rule states',
            data: states.map(formatState)
        });
    } catch (error) {
        return next(error);
    }
};

export const deleteAlertRuleState = async (req, res, next) => {
    try {
        if (!isAlertRuleId(req.params.ruleId)) return validationError(res, 'Alert rule ID must be a valid rule identifier');
        if (!isUuid(req.params.deviceId)) return validationError(res, 'Device ID must be a valid UUID');

        const deleted = await AlertRuleState.destroy({
            where: { ruleId: req.params.ruleId, deviceId: req.params.deviceId }
        });
        if (!deleted) return notFound(res, 'Alert rule state not found');

        return res.status(204).send();
    } catch (error) {
        return next(error);
    }
};
