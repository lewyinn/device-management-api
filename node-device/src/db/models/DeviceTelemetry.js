import { DataTypes } from 'sequelize';

export default (sequelize) => {
    const DeviceTelemetry = sequelize.define('DeviceTelemetry', {
        id: {
            type: DataTypes.INTEGER,
            autoIncrement: true,
            primaryKey: true
        },
        device_id: {
            type: DataTypes.UUID,
            allowNull: false
        },
        ts: {
            type: DataTypes.BIGINT,
            allowNull: false
        },
        temperature: {
            type: DataTypes.FLOAT,
            allowNull: false
        },
        humidity: {
            type: DataTypes.FLOAT,
            allowNull: false
        }
    }, {
        tableName: 'device_telemetries',
        timestamps: false,
        indexes: [
            {
                unique: true,
                fields: ['device_id', 'ts']
            }
        ]
    });

    return DeviceTelemetry;
};
