import re
from datetime import datetime, timezone
from time import time
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.concurrency import run_in_threadpool
from sqlalchemy.orm import Session

from app.core.cassandra import (
    CassandraTelemetryRepository,
    get_telemetry_repository,
)
from app.core.database import get_db
from app.core.websocket import websocket_manager
from app.models import Device
from app.schemas import TelemetryCreate

devices_router = APIRouter(prefix="/api/v1/devices", tags=["Telemetry"])

RECORD_MONTH_PATTERN = re.compile(r"^\d{4}-(0[1-9]|1[0-2])$")
DEFAULT_START_MONTH = "2026-01"
DEFAULT_END_MONTH = "2026-12"

def validation_error(message: str):
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail={"error": "Validation failed", "details": message},
    )


def get_device(db: Session, device_id: UUID):
    normalized_device_id = str(device_id)
    device = db.query(Device).filter(Device.id == normalized_device_id).first()
    if device is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": f"Device ID {normalized_device_id} not found"},
        )
    return device


def is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def normalize_device_uuid(value) -> UUID:
    if isinstance(value, UUID):
        return value

    return UUID(str(value))


def normalize_device_id(value) -> str:
    return str(value)


def telemetry_response(telemetry, device: Device):
    return {
        "device_id": normalize_device_id(device.id),
        "deviceName": device.name,
        "deviceType": device.type,
        "data": {
            "ts": telemetry.ts,
            "temperature": telemetry.temperature,
            "humidity": telemetry.humidity,
        },
    }


@devices_router.post(
    "/{device_id}/telemetry",
    name="Create Device Telemetry",
    description="Mencatat data telemetry device ke Cassandra.",
    status_code=status.HTTP_201_CREATED,
    responses={
        201: {
            "description": "Created",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Telemetry successfully recorded",
                        "data": {
                            "device_id": "550e8400-e29b-41d4-a716-446655440000",
                            "deviceName": "Sensor-Suhu",
                            "deviceType": "PM2120",
                            "data": {
                                "ts": "generated_epoch_ms",
                                "temperature": 25.5,
                                "humidity": 60.0,
                            },
                        },
                    }
                }
            },
        },
        400: {
            "description": "Bad Request",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Validation failed",
                        "details": "Attributes 'values.temperature' and 'values.humidity' must be numbers",
                    }
                }
            },
        },
        404: {
            "description": "Device Not Found",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
                    }
                }
            },
        },
        500: {
            "description": "Internal Server Error",
            "content": {
                "application/json": {
                    "example": {"error": "Internal server error"}
                }
            },
        },
    },
)
async def create_telemetry(
    device_id: UUID,
    payload: TelemetryCreate,
    db: Session = Depends(get_db),
    telemetry_repository: CassandraTelemetryRepository = Depends(get_telemetry_repository),
):
    values = payload.values
    telemetry_ts = int(time() * 1000)
    temperature = values.temperature if values else None
    humidity = values.humidity if values else None

    if not is_number(temperature) or not is_number(humidity):
        validation_error("Attributes 'values.temperature' and 'values.humidity' must be numbers")

    device = get_device(db, device_id)
    telemetry = await run_in_threadpool(
        telemetry_repository.insert,
        device_id=normalize_device_uuid(device.id),
        ts=telemetry_ts,
        temperature=float(temperature),
        humidity=float(humidity),
    )
    telemetry_data = {
        "ts": telemetry.ts,
        "temperature": telemetry.temperature,
        "humidity": telemetry.humidity,
    }
    await websocket_manager.broadcast_telemetry(
        {
            "id": normalize_device_id(device.id),
            "name": device.name,
            "type": device.type,
            "status": device.status,
        },
        telemetry_data,
    )

    return {
        "message": "Telemetry successfully recorded",
        "data": telemetry_response(telemetry, device),
    }


@devices_router.get(
    "/{device_id}/telemetry",
    name="Get Device Telemetry",
    description="Mengambil telemetry device dari Cassandra berdasarkan rentang bulan.",
    responses={
        200: {
            "description": "OK",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Success retrieving telemetry",
                        "device_id": "550e8400-e29b-41d4-a716-446655440000",
                        "deviceName": "Sensor-Suhu",
                        "deviceType": "PM2120",
                        "data": [
                            {
                                "ts": "generated_epoch_ms",
                                "temperature": 25.7,
                                "humidity": 59.5,
                            },
                            {
                                "ts": "generated_epoch_ms",
                                "temperature": 25.5,
                                "humidity": 60.0,
                            },
                        ],
                    }
                }
            },
        },
        400: {
            "description": "Bad Request",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Validation failed",
                        "details": "Query parameter 'start_month' and 'end_month' must use YYYY-MM format",
                    }
                }
            },
        },
        404: {
            "description": "Device Not Found",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
                    }
                }
            },
        },
        500: {
            "description": "Internal Server Error",
            "content": {
                "application/json": {
                    "example": {"error": "Internal server error"}
                }
            },
        },
    },
)
async def list_telemetry(
    device_id: UUID,
    start_month: str | None = Query(default=DEFAULT_START_MONTH),
    end_month: str | None = Query(default=DEFAULT_END_MONTH),
    db: Session = Depends(get_db),
    telemetry_repository: CassandraTelemetryRepository = Depends(get_telemetry_repository),
):
    device = get_device(db, device_id)
    normalized_start = start_month or DEFAULT_START_MONTH
    normalized_end = end_month or DEFAULT_END_MONTH

    if (
        not RECORD_MONTH_PATTERN.fullmatch(normalized_start)
        or not RECORD_MONTH_PATTERN.fullmatch(normalized_end)
        or normalized_start > normalized_end
    ):
        validation_error("Query parameter 'start_month' and 'end_month' must use YYYY-MM format")

    start_year, start_month_number = [int(value) for value in normalized_start.split("-")]
    end_year, end_month_number = [int(value) for value in normalized_end.split("-")]
    months = []
    year = start_year
    month = start_month_number

    while (year, month) <= (end_year, end_month_number):
        months.append(f"{year:04d}-{month:02d}")
        month += 1
        if month > 12:
            year += 1
            month = 1

    telemetries = await run_in_threadpool(
        telemetry_repository.find_by_months,
        device_id=normalize_device_uuid(device.id),
        months=months,
    )

    return {
        "message": "Success retrieving telemetry",
        "device_id": normalize_device_id(device.id),
        "deviceName": device.name,
        "deviceType": device.type,
        "data": [
            {
                "ts": telemetry.ts,
                "temperature": telemetry.temperature,
                "humidity": telemetry.humidity,
            }
            for telemetry in telemetries
        ],
    }


@devices_router.get(
    "/{device_id}/telemetry/latest",
    name="Get Latest Device Telemetry",
    description="Mengambil telemetry terbaru milik satu device dari Cassandra.",
    responses={
        200: {
            "description": "OK",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Latest telemetry found",
                        "data": {
                            "device_id": "550e8400-e29b-41d4-a716-446655440000",
                            "deviceName": "Sensor-Suhu",
                            "deviceType": "PM2120",
                            "data": {
                                "ts": "generated_epoch_ms",
                                "temperature": 25.5,
                                "humidity": 60.0,
                            },
                        },
                    }
                }
            },
        },
        400: {
            "description": "Bad Request",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Validation failed",
                        "details": "Device ID must be a valid UUID",
                    }
                }
            },
        },
        404: {
            "description": "Device or Telemetry Not Found",
            "content": {
                "application/json": {
                    "examples": {
                        "deviceNotFound": {
                            "summary": "Device not found",
                            "value": {
                                "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
                            },
                        },
                        "telemetryNotFound": {
                            "summary": "Telemetry not found",
                            "value": {
                                "error": "Telemetry for device ID 550e8400-e29b-41d4-a716-446655440000 not found"
                            },
                        },
                    }
                }
            },
        },
        500: {
            "description": "Internal Server Error",
            "content": {
                "application/json": {
                    "example": {"error": "Internal server error"}
                }
            },
        },
    },
)
async def latest_telemetry(
    device_id: UUID,
    db: Session = Depends(get_db),
    telemetry_repository: CassandraTelemetryRepository = Depends(get_telemetry_repository),
):
    device = get_device(db, device_id)
    start_year, start_month_number = [int(part) for part in DEFAULT_START_MONTH.split("-")]
    year, month = [int(part) for part in DEFAULT_END_MONTH.split("-")]
    months = []

    while (year, month) >= (start_year, start_month_number):
        months.append(f"{year:04d}-{month:02d}")
        month -= 1
        if month < 1:
            year -= 1
            month = 12

    telemetry = await run_in_threadpool(
        telemetry_repository.find_latest,
        device_id=normalize_device_uuid(device.id),
        months=months,
    )

    if telemetry is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": f"Telemetry for device ID {normalize_device_id(device.id)} not found"},
        )

    return {
        "message": "Latest telemetry found",
        "data": telemetry_response(telemetry, device),
    }
