import asyncio
import json
import os
from contextlib import suppress
from urllib.parse import urlparse
from uuid import UUID

from dotenv import load_dotenv
from fastapi_mqtt import FastMQTT, MQTTConfig

from app.core.cassandra import get_telemetry_repository

load_dotenv()

RECONNECT_DELAY_SECONDS = 2

MQTT_TOPIC = os.getenv("MQTT_TELEMETRY_TOPIC")
mqtt_started = False
mqtt_retry_task: asyncio.Task | None = None


def parse_broker_url(value: str) -> tuple[str, int]:
    broker = urlparse(value)
    if broker.scheme != "mqtt" or not broker.hostname:
        raise RuntimeError("MQTT_BROKER_URL must use mqtt://host:port format")

    return broker.hostname, broker.port or 1883


MQTT_HOST, MQTT_PORT = parse_broker_url(os.getenv("MQTT_BROKER_URL"))

fast_mqtt = FastMQTT(
    config=MQTTConfig(
        host=MQTT_HOST,
        port=MQTT_PORT,
        keepalive=60,
        reconnect_retries=None,
        reconnect_delay=RECONNECT_DELAY_SECONDS,
    )
)


def is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def is_epoch_milliseconds(value):
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


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

    ts = body.get("ts") if isinstance(body, dict) else None
    temperature = body.get("temperature") if isinstance(body, dict) else None
    humidity = body.get("humidity") if isinstance(body, dict) else None

    if not is_epoch_milliseconds(ts) or not is_number(temperature) or not is_number(humidity):
        return None

    return {
        "ts": ts,
        "temperature": float(temperature),
        "humidity": float(humidity),
    }


def persist_telemetry(topic: str, payload: bytes):
    device_id = extract_device_id(topic)
    if device_id is None:
        print(f"MQTT telemetry ignored because topic is invalid: {topic}")
        return

    telemetry = parse_telemetry_payload(payload)
    if telemetry is None:
        print(f"MQTT telemetry ignored because payload is invalid for topic: {topic}")
        return

    try:
        get_telemetry_repository().insert(
            device_id=device_id,
            ts=telemetry["ts"],
            temperature=telemetry["temperature"],
            humidity=telemetry["humidity"],
        )
        print(f"MQTT telemetry persisted for device {device_id} at {telemetry['ts']}")
    except Exception as error:
        print(f"Failed to persist MQTT telemetry: {error}")


@fast_mqtt.on_connect()
def handle_connect(client, flags, rc, properties):
    print(f"MQTT connected to mqtt://{MQTT_HOST}:{MQTT_PORT}")


@fast_mqtt.on_disconnect()
def handle_disconnect(client, packet, exc=None):
    if exc:
        print(f"MQTT disconnected unexpectedly: {exc}")
        return

    print("MQTT disconnected")


@fast_mqtt.on_subscribe()
def handle_subscribe(client, mid, qos, properties):
    print(f"MQTT subscribed to {MQTT_TOPIC}")


@fast_mqtt.subscribe(MQTT_TOPIC, qos=0)
async def handle_telemetry_message(client, topic, payload, qos, properties):
    persist_telemetry(topic, payload)


async def start_mqtt_subscriber():
    global mqtt_retry_task

    if mqtt_started or mqtt_retry_task is not None:
        return

    try:
        await run_mqtt_startup()
    except Exception as error:
        print(f"MQTT startup failed: {error}")
        mqtt_retry_task = asyncio.create_task(retry_mqtt_startup())


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


async def run_mqtt_startup():
    global mqtt_started

    await fast_mqtt.mqtt_startup()
    mqtt_started = True


async def retry_mqtt_startup():
    global mqtt_retry_task

    while not mqtt_started:
        await asyncio.sleep(RECONNECT_DELAY_SECONDS)
        try:
            await run_mqtt_startup()
        except Exception as error:
            print(f"MQTT startup retry failed: {error}")

    mqtt_retry_task = None
