import express from 'express';
import swaggerUi from 'swagger-ui-express';
import swaggerJsdoc from 'swagger-jsdoc';
import { errorHandler } from './middleware/errorHandler.js';
import devicesRoutes from './routes/device.routes.js';
import telemetryRoutes from './routes/telemetry.routes.js';

const app = express();
app.disable("x-powered-by");
app.use(express.json());

const swaggerOptions = {
    definition: {
        openapi: '3.0.0',
        info: {
            title: 'Device Management API',
            version: '1.0.0',
            description: 'API untuk mengelola perangkat IoT'
        },
        servers: [{ url: 'http://localhost:3000/api/v1' }],
        components: {
            schemas: {
                Device: {
                    type: 'object',
                    properties: {
                        id: { type: 'string', format: 'uuid', example: '550e8400-e29b-41d4-a716-446655440000' },
                        name: { type: 'string', example: 'Sensor-Suhu' },
                        type: { type: 'string', example: 'DHT22' },
                        status: { type: 'string', enum: ['active', 'inactive'], example: 'active' }
                    }
                },
                CreateDeviceRequest: {
                    type: 'object',
                    required: ['name', 'type'],
                    properties: {
                        name: { type: 'string', example: 'Sensor-Suhu' },
                        type: { type: 'string', example: 'DHT22' },
                        status: { type: 'string', enum: ['active', 'inactive'], example: 'active' }
                    }
                },
                UpdateDeviceRequest: {
                    type: 'object',
                    required: ['name', 'type', 'status'],
                    properties: {
                        name: { type: 'string', example: 'Sensor-Suhu' },
                        type: { type: 'string', example: 'DHT22' },
                        status: { type: 'string', enum: ['active', 'inactive'], example: 'inactive' }
                    }
                },
                PatchDeviceRequest: {
                    type: 'object',
                    properties: {
                        name: { type: 'string', example: 'Sensor-Suhu' },
                        type: { type: 'string', example: 'DHT22' },
                        status: { type: 'string', enum: ['active', 'inactive'], example: 'inactive' }
                    },
                    example: { status: 'inactive' }
                },
                DeviceResponse: {
                    type: 'object',
                    properties: {
                        message: { type: 'string' },
                        data: { $ref: '#/components/schemas/Device' }
                    }
                },
                DeviceListResponse: {
                    type: 'object',
                    properties: {
                        message: { type: 'string', example: 'Success retrieving devices' },
                        meta: {
                            type: 'object',
                            properties: {
                                page: { type: 'integer', example: 1 },
                                limit: { type: 'integer', example: 10 },
                                total_data: { type: 'integer', example: 50 },
                                total_pages: { type: 'integer', example: 5 }
                            }
                        },
                        data: {
                            type: 'array',
                            items: { $ref: '#/components/schemas/Device' }
                        }
                    }
                },
                TelemetryRequest: {
                    type: 'object',
                    required: ['values'],
                    properties: {
                        values: {
                            type: 'object',
                            required: ['temperature', 'humidity'],
                            properties: {
                                temperature: { type: 'number', format: 'float', example: 28.5 },
                                humidity: { type: 'number', format: 'float', example: 75.2 }
                            }
                        }
                    }
                },
                TelemetryDataPoint: {
                    type: 'object',
                    properties: {
                        ts: { type: 'integer', format: 'int64', example: 1717488000000 },
                        temperature: { type: 'number', format: 'float', example: 28.5 },
                        humidity: { type: 'number', format: 'float', example: 75.2 }
                    }
                },
                Telemetry: {
                    type: 'object',
                    properties: {
                        device_id: { type: 'string', format: 'uuid', example: '550e8400-e29b-41d4-a716-446655440000' },
                        deviceName: { type: 'string', example: 'Sensor-Suhu' },
                        deviceType: { type: 'string', example: 'DHT22' },
                        data: { $ref: '#/components/schemas/TelemetryDataPoint' }
                    }
                },
                TelemetryResponse: {
                    type: 'object',
                    properties: {
                        message: { type: 'string', example: 'Telemetry successfully recorded' },
                        data: { $ref: '#/components/schemas/Telemetry' }
                    }
                },
                TelemetryListResponse: {
                    type: 'object',
                    properties: {
                        message: { type: 'string', example: 'Success retrieving telemetry' },
                        device_id: { type: 'string', format: 'uuid', example: '550e8400-e29b-41d4-a716-446655440000' },
                        deviceName: { type: 'string', example: 'Sensor-Suhu' },
                        deviceType: { type: 'string', example: 'DHT22' },
                        data: {
                            type: 'array',
                            items: { $ref: '#/components/schemas/TelemetryDataPoint' }
                        }
                    }
                },
                ErrorResponse: {
                    type: 'object',
                    properties: {
                        error: { type: 'string' },
                        details: { oneOf: [{ type: 'string' }, { type: 'array', items: { type: 'string' } }] }
                    }
                }
            }
        }
    },
    apis: ['./src/routes/*.js']
};

const swaggerSpec = swaggerJsdoc(swaggerOptions);
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec));

app.use('/api/v1/devices', devicesRoutes);
app.use('/api/v1/telemetry', telemetryRoutes);

app.use(errorHandler);

export default app;