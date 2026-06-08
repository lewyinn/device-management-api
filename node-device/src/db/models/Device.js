import { DataTypes } from 'sequelize';

export default (sequelize) => {
    const Device = sequelize.define('Device', {
        id: {
            type: DataTypes.UUID,
            defaultValue: DataTypes.UUIDV4,
            primaryKey: true
        },
        name: {
            type: DataTypes.STRING,
            allowNull: false,
            validate: { notEmpty: true }
        },
        type: {
            type: DataTypes.STRING,
            allowNull: false,
            validate: { notEmpty: true }
        },
        status: {
            type: DataTypes.ENUM('active', 'inactive'),
            allowNull: false,
            defaultValue: 'active'
        }
    }, {
        tableName: 'devices',
        timestamps: false,
        indexes: [
            {
                name: 'idx_devices_name',
                fields: ['name']
            }
        ]
    });

    return Device;
};
