import asyncio
from dataclasses import dataclass, field
from uuid import UUID

from fastapi import WebSocket, WebSocketDisconnect
from starlette.websockets import WebSocketState

SHARED_CHANNELS = {"telemetry", "deviceRegister"}
DEVICE_TELEMETRY_PREFIX = "device_telemetry"


@dataclass
class WebSocketConnection:
    websocket: WebSocket
    subscriptions: set[str] = field(default_factory=set)
    send_lock: asyncio.Lock = field(default_factory=asyncio.Lock)


class WebSocketManager:
    def __init__(self):
        self.connections: list[WebSocketConnection] = []
        self.connection_lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        connection = WebSocketConnection(websocket=websocket)

        async with self.connection_lock:
            self.connections.append(connection)

        await self.send_json(
            connection,
            {
                "type": "connected",
                "message": "WebSocket connection established",
            },
        )

        return connection

    async def disconnect(self, connection: WebSocketConnection):
        async with self.connection_lock:
            if connection in self.connections:
                self.connections.remove(connection)

    async def handle_connection(self, websocket: WebSocket):
        connection = await self.connect(websocket)

        try:
            while True:
                try:
                    message = await websocket.receive_json()
                except ValueError:
                    await self.send_json(
                        connection,
                        {
                            "type": "error",
                            "error": "Invalid WebSocket message",
                        },
                    )
                    continue

                await self.handle_message(connection, message)
        except WebSocketDisconnect:
            pass
        finally:
            await self.disconnect(connection)

    async def handle_message(self, connection: WebSocketConnection, message):
        parsed_message = self.parse_message(message)

        if parsed_message is None:
            await self.send_json(
                connection,
                {
                    "type": "error",
                    "error": "Invalid WebSocket message",
                },
            )
            return

        message_type, channels = parsed_message

        if message_type == "subscribe":
            connection.subscriptions.update(channels)
        else:
            connection.subscriptions.difference_update(channels)

        await self.send_json(
            connection,
            {
                "type": "subscribed" if message_type == "subscribe" else "unsubscribed",
                "channels": channels,
            },
        )

    async def broadcast(self, channels: list[str], event: dict):
        async with self.connection_lock:
            connections = list(self.connections)

        recipients = [
            connection
            for connection in connections
            if any(channel in connection.subscriptions for channel in channels)
        ]

        if not recipients:
            return 0

        results = await asyncio.gather(
            *(self.send_json(connection, event) for connection in recipients),
            return_exceptions=True,
        )

        disconnected = [
            connection
            for connection, result in zip(recipients, results)
            if result is False or isinstance(result, Exception)
        ]

        for connection in disconnected:
            await self.disconnect(connection)

        return len(recipients) - len(disconnected)

    async def broadcast_telemetry(self, device: dict, telemetry: dict):
        await self.broadcast(
            [
                "telemetry",
                f"{DEVICE_TELEMETRY_PREFIX}:{device['id']}",
            ],
            {
                "type": "telemetry_received",
                "data": {
                    "device_id": device["id"],
                    "device_name": device["name"],
                    "device_type": device["type"],
                    "ts": telemetry["ts"],
                    "temperature": telemetry["temperature"],
                    "humidity": telemetry["humidity"],
                },
            },
        )

    async def broadcast_device_registered(self, device: dict):
        await self.broadcast(
            ["deviceRegister"],
            {
                "type": "device_registered",
                "data": {
                    "id": device["id"],
                    "name": device["name"],
                    "type": device["type"],
                    "status": device["status"],
                },
            },
        )

    async def shutdown(self):
        async with self.connection_lock:
            connections = list(self.connections)
            self.connections.clear()

        await asyncio.gather(
            *(self.close_connection(connection) for connection in connections),
            return_exceptions=True,
        )

    @staticmethod
    def parse_message(message):
        if not isinstance(message, dict):
            return None

        message_type = message.get("type")
        channels = message.get("channels")

        if channels is None and message.get("channel") is not None:
            channels = [message["channel"]]

        if (
            message_type not in {"subscribe", "unsubscribe"}
            or not isinstance(channels, list)
            or not channels
            or not all(isinstance(channel, str) and is_valid_channel(channel) for channel in channels)
        ):
            return None

        return message_type, channels

    @staticmethod
    async def send_json(connection: WebSocketConnection, message: dict):
        if connection.websocket.application_state != WebSocketState.CONNECTED:
            return False

        async with connection.send_lock:
            try:
                await connection.websocket.send_json(message)
                return True
            except (RuntimeError, WebSocketDisconnect):
                return False

    @staticmethod
    async def close_connection(connection: WebSocketConnection):
        if connection.websocket.application_state == WebSocketState.CONNECTED:
            await connection.websocket.close(code=1001, reason="Application shutting down")


def is_valid_channel(channel: str):
    if channel in SHARED_CHANNELS:
        return True

    prefix, separator, device_id = channel.partition(":")

    if prefix != DEVICE_TELEMETRY_PREFIX or not separator:
        return False

    try:
        UUID(device_id)
        return True
    except ValueError:
        return False


websocket_manager = WebSocketManager()
