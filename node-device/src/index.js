import express from 'express';
import swaggerUi from 'swagger-ui-express';
import swaggerJsdoc from 'swagger-jsdoc';
import { errorHandler } from './middleware/errorHandler.js';
import devicesRoutes from './routes/device.routes.js';
import telemetryRoutes, { recentTelemetryRouter } from './routes/telemetry.routes.js';
import {
    alertRuleRouter,
    alertRuleStateRouter
} from './routes/alertRule.routes.js';

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
        servers: [{ url: 'http://localhost:3000/api/v1' }]
    },
    apis: ['./src/routes/*.js']
};

const swaggerSpec = swaggerJsdoc(swaggerOptions);
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec));

app.use('/api/v1/devices', telemetryRoutes);
app.use('/api/v1/devices', devicesRoutes);
app.use('/api/v1/telemetry', recentTelemetryRouter);
app.use('/api/v1/alert-rules', alertRuleRouter);
app.use('/api/v1/alert-rule-states', alertRuleStateRouter);

app.use(errorHandler);

export default app;
