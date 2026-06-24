import asyncio
import json
import os
from contextlib import suppress
from urllib.parse import urlparse
from uuid import UUID

from dotenv import load_dotenv
from fastapi_mqtt import FastMQTT, MQTTConfig

from app.core.database_pg import SessionLocal
from app.services.device_service import device_service, serialize_device
from app.services.telemetry_service import serialize_telemetry, telemetry_service
from app.websocket.websocket import websocket_manager

load_dotenv()

RECONNECT_DELAY_SECONDS = 2
MQTT_BROKER_URL = os.getenv("MQTT_BROKER_URL", "mqtt://127.0.0.1:1883")
MQTT_TELEMETRY_TOPIC = os.getenv(
    "MQTT_TELEMETRY_TOPIC",
    "gedung-solu/monitoring/lantai-1/devices/+/telemetry",
)

mqtt_started = False
mqtt_retry_task: asyncio.Task | None = None


def parse_broker_url(broker_url: str) -> tuple[str, int]:
    broker = urlparse(broker_url)
    if broker.scheme != "mqtt" or not broker.hostname:
        raise RuntimeError("MQTT_BROKER_URL must use mqtt://host:port format")

    return broker.hostname, broker.port or 1883


MQTT_HOST, MQTT_PORT = parse_broker_url(MQTT_BROKER_URL)

fast_mqtt = FastMQTT(
    config=MQTTConfig(
        host=MQTT_HOST,
        port=MQTT_PORT,
        keepalive=60,
        reconnect_retries=None,
        reconnect_delay=RECONNECT_DELAY_SECONDS,
    )
)


def extract_device_id(topic: str) -> UUID | None:
    parts = topic.split("/")
    if (
        len(parts) != 6
        or parts[0] != "gedung-solu"
        or parts[1] != "monitoring"
        or parts[2] != "lantai-1"
        or parts[3] != "devices"
        or parts[5] != "telemetry"
    ):
        return None

    try:
        return UUID(parts[4])
    except ValueError:
        return None


def parse_telemetry_payload(payload: bytes) -> dict | None:
    try:
        body = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None

    if not isinstance(body, dict):
        return None

    timestamp = body.get("ts")
    temperature = body.get("temperature")
    humidity = body.get("humidity")

    if type(timestamp) is not int or timestamp <= 0:
        return None
    if isinstance(temperature, bool) or not isinstance(temperature, (int, float)):
        return None
    if isinstance(humidity, bool) or not isinstance(humidity, (int, float)):
        return None

    return {
        "ts": timestamp,
        "temperature": float(temperature),
        "humidity": float(humidity),
    }


def persist_telemetry(topic: str, payload: bytes):
    device_id = extract_device_id(topic)
    if device_id is None:
        print(f"MQTT telemetry ignored because topic is invalid: {topic}")
        return None

    payload_data = parse_telemetry_payload(payload)
    if payload_data is None:
        print(f"MQTT telemetry ignored because payload is invalid for topic: {topic}")
        return None

    with SessionLocal() as database:
        device = device_service.get(database, device_id)
        telemetry = telemetry_service.insert(
            device_id=device_id,
            ts=payload_data["ts"],
            temperature=payload_data["temperature"],
            humidity=payload_data["humidity"],
        )

        print(f"MQTT telemetry persisted for device {device_id} at {telemetry.ts}")
        return {
            "device": serialize_device(device),
            "telemetry": serialize_telemetry(telemetry),
        }


@fast_mqtt.on_connect()
def handle_connect(client, flags, rc, properties):
    print(f"MQTT connected to {MQTT_BROKER_URL}")


@fast_mqtt.on_disconnect()
def handle_disconnect(client, packet, exc=None):
    if exc:
        print(f"MQTT disconnected unexpectedly: {exc}")
    else:
        print("MQTT disconnected")


@fast_mqtt.on_subscribe()
def handle_subscribe(client, mid, qos, properties):
    print(f"MQTT subscribed to {MQTT_TELEMETRY_TOPIC}")


@fast_mqtt.subscribe(MQTT_TELEMETRY_TOPIC, qos=0)
async def handle_telemetry_message(client, topic, payload, qos, properties):
    try:
        result = await asyncio.to_thread(persist_telemetry, topic, payload)
        if result is not None:
            await websocket_manager.broadcast_telemetry(
                result["device"],
                result["telemetry"],
            )
    except Exception as error:
        print(f"Failed to process MQTT telemetry: {error}")


async def start_mqtt_subscriber():
    global mqtt_retry_task

    if mqtt_started or mqtt_retry_task is not None:
        return

    try:
        await _start_mqtt()
    except Exception as error:
        print(f"MQTT startup failed: {error}")
        mqtt_retry_task = asyncio.create_task(_retry_mqtt_startup())


async def stop_mqtt_subscriber():
    global mqtt_retry_task, mqtt_started

    if mqtt_retry_task is not None:
        mqtt_retry_task.cancel()
        with suppress(asyncio.CancelledError):
            await mqtt_retry_task
        mqtt_retry_task = None

    if not mqtt_started:
        return

    try:
        await fast_mqtt.mqtt_shutdown()
        mqtt_started = False
        print("MQTT subscriber stopped")
    except Exception as error:
        print(f"MQTT shutdown failed: {error}")


async def _start_mqtt():
    global mqtt_started

    await fast_mqtt.mqtt_startup()
    mqtt_started = True


async def _retry_mqtt_startup():
    global mqtt_retry_task

    while not mqtt_started:
        await asyncio.sleep(RECONNECT_DELAY_SECONDS)
        try:
            await _start_mqtt()
        except Exception as error:
            print(f"MQTT startup retry failed: {error}")

    mqtt_retry_task = None
