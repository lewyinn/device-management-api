import { DataTypes } from 'sequelize';

export default (sequelize) => {
    const AlertRuleState = sequelize.define('AlertRuleState', {
        ruleId: {
            type: DataTypes.STRING(64),
            allowNull: false,
            primaryKey: true,
            field: 'rule_id'
        },
        deviceId: {
            type: DataTypes.UUID,
            allowNull: false,
            primaryKey: true,
            field: 'device_id'
        },
        lastTriggeredAt: {
            type: DataTypes.DATE,
            allowNull: false,
            field: 'last_triggered_at'
        },
        lastTelemetryTs: {
            type: DataTypes.BIGINT,
            allowNull: false,
            field: 'last_telemetry_ts'
        }
    }, {
        tableName: 'alert_rule_state',
        underscored: true,
        timestamps: false
    });

    return AlertRuleState;
};
