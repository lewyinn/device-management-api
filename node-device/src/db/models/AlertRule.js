import { DataTypes } from 'sequelize';
import { randomBytes } from 'node:crypto';

const createAlertRuleId = () => `rule_${randomBytes(5).toString('base64url').toLowerCase()}`;

export default (sequelize) => {
    const AlertRule = sequelize.define('AlertRule', {
        id: {
            type: DataTypes.STRING(64),
            defaultValue: createAlertRuleId,
            primaryKey: true
        },
        name: {
            type: DataTypes.STRING(100),
            allowNull: false,
            validate: { notEmpty: true }
        },
        description: {
            type: DataTypes.TEXT,
            allowNull: true
        },
        deviceId: {
            type: DataTypes.UUID,
            allowNull: true,
            field: 'device_id'
        },
        deviceType: {
            type: DataTypes.STRING(100),
            allowNull: true,
            field: 'device_type'
        },
        metricKey: {
            type: DataTypes.STRING(100),
            allowNull: false,
            field: 'metric_key',
            validate: { notEmpty: true }
        },
        operator: {
            type: DataTypes.ENUM('>', '>=', '<', '<=', '==', 'between'),
            allowNull: false
        },
        thresholdValue: {
            type: DataTypes.DOUBLE,
            allowNull: false,
            field: 'threshold_value'
        },
        secondThresholdValue: {
            type: DataTypes.DOUBLE,
            allowNull: true,
            field: 'second_threshold_value'
        },
        severity: {
            type: DataTypes.ENUM('info', 'warning', 'critical'),
            allowNull: false,
            defaultValue: 'warning'
        },
        enabled: {
            type: DataTypes.BOOLEAN,
            allowNull: false,
            defaultValue: true
        },
        cooldownSeconds: {
            type: DataTypes.INTEGER,
            allowNull: false,
            defaultValue: 300,
            field: 'cooldown_seconds',
            validate: { min: 0 }
        },
        messageTemplate: {
            type: DataTypes.TEXT,
            allowNull: false,
            field: 'message_template',
            validate: { notEmpty: true }
        }
    }, {
        tableName: 'alert_rules',
        underscored: true,
        timestamps: true,
        indexes: [
            { name: 'idx_alert_rules_enabled', fields: ['enabled'] },
            { name: 'idx_alert_rules_device_id', fields: ['device_id'] },
            { name: 'idx_alert_rules_device_type', fields: ['device_type'] }
        ]
    });

    return AlertRule;
};
