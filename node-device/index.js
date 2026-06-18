import { createServer } from 'http';
import app from './src/index.js';
import database from './src/db/index.js';
import {
    startMqttTelemetrySubscriber,
    stopMqttTelemetrySubscriber
} from './src/mqtt/telemetrySubscriber.js';
import {
    initializeWebSocketServer,
    shutdownWebSocketServer
} from './src/websocket/websocketServer.js';
import dotenv from 'dotenv';

dotenv.config({ quiet: true });

let server;
let isShuttingDown = false;

const start = async () => {
    await database.authenticateSqlDatabase();
    await database.sync({ alter: false });
    await database.connectCassandra();
    startMqttTelemetrySubscriber();

    const port = process.env.PORT || 3000;
    server = createServer(app);
    initializeWebSocketServer(server);

    server.listen(port, () => {
        console.log(`Server running on http://localhost:${port}`);
        console.log(`Swagger UI: http://localhost:${port}/api-docs`);
        console.log(`WebSocket: ws://localhost:${port}/ws`);
    });
};

const closeHttpServer = () => new Promise((resolve, reject) => {
    if (!server) {
        resolve();
        return;
    }

    server.close((error) => {
        if (error) {
            reject(error);
            return;
        }

        resolve();
    });
});

const shutdown = async (signal) => {
    if (isShuttingDown) {
        return;
    }

    isShuttingDown = true;
    console.log(`${signal} received. Shutting down application...`);

    try {
        await shutdownWebSocketServer();
        await closeHttpServer();
        await stopMqttTelemetrySubscriber();
        await database.closeDatabases();
        console.log('Application shutdown completed');
        process.exit(0);
    } catch (error) {
        console.error('Application shutdown failed:', error);
        process.exit(1);
    }
};

process.on('SIGINT', () => {
    void shutdown('SIGINT');
});

process.on('SIGTERM', () => {
    void shutdown('SIGTERM');
});

try {
    await start();
} catch (error) {
    console.error('Application startup failed:', error);

    try {
        await shutdownWebSocketServer();
        await stopMqttTelemetrySubscriber();
        await database.closeDatabases();
    } catch (shutdownError) {
        console.error('Database cleanup after startup failure failed:', shutdownError);
    }

    process.exit(1);
}

export default app;
