from typing import Any
from uuid import UUID

from fastapi import APIRouter, Depends, Query, status
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session

from app.core.database_pg import get_db
from app.services.telemetry_service import (
    DEFAULT_END_MONTH,
    DEFAULT_START_MONTH,
    serialize_telemetry,
    telemetry_service,
)
from app.websocket.websocket import websocket_manager

router = APIRouter(prefix="/devices", tags=["Telemetry"])


class TelemetryValues(BaseModel):
    temperature: Any = None
    humidity: Any = None


class TelemetryCreate(BaseModel):
    values: TelemetryValues | None = None

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "values": {
                    "temperature": 28.5,
                    "humidity": 75.2,
                }
            }
        }
    )


@router.post(
    "/{device_id}/telemetry",
    status_code=status.HTTP_201_CREATED,
    name="Create Device Telemetry",
    description="Menyimpan telemetry device ke Cassandra.",
    responses={
        201: {
            "description": "OK",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Telemetry successfully recorded",
                        "data": {
                            "device_id": "550e8400-e29b-41d4-a716-446655440000",
                            "deviceName": "Sensor-Suhu",
                            "deviceType": "PM2120",
                            "data": {
                                "ts": 1781490918553,
                                "temperature": 28.5,
                                "humidity": 75.2,
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
                        "details": (
                            "Attributes 'values.temperature' and "
                            "'values.humidity' must be numbers"
                        ),
                    }
                }
            },
        },
        404: {
            "description": "Not Found",
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
                    "example": {
                        "error": "Internal Server Error"
                    }
                }
            }
        },
    },
)
async def create_telemetry(
    device_id: UUID,
    payload: TelemetryCreate,
    database: Session = Depends(get_db),
):
    values = payload.values
    device, telemetry = await telemetry_service.create(
        database,
        device_id=device_id,
        temperature=values.temperature if values else None,
        humidity=values.humidity if values else None,
    )
    device_data, telemetry_data = telemetry_service.websocket_data(device, telemetry)
    await websocket_manager.broadcast_telemetry(device_data, telemetry_data)

    return {
        "message": "Telemetry successfully recorded",
        "data": telemetry_service.response(device, telemetry),
    }


@router.get(
    "/{device_id}/telemetry",
    name="Get Device Telemetry",
    description="Mengambil telemetry Cassandra berdasarkan rentang bulan.",
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
                                "ts": 1781490918553,
                                "temperature": 28.5,
                                "humidity": 75.2,
                            }
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
                        "details": (
                            "Query parameter 'start_month' and "
                            "'end_month' must use YYYY-MM format"
                        ),
                    }
                }
            },
        },
        404: {
            "description": "Not Found",
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
                    "example": {
                        "error": "Internal Server Error"
                    }
                }
            }
        },
    },
)
async def list_telemetry(
    device_id: UUID,
    start_month: str | None = Query(default=DEFAULT_START_MONTH),
    end_month: str | None = Query(default=DEFAULT_END_MONTH),
    database: Session = Depends(get_db),
):
    device, telemetries = await telemetry_service.history(
        database,
        device_id=device_id,
        start_month=start_month,
        end_month=end_month,
    )
    return {
        "message": "Success retrieving telemetry",
        "device_id": str(device.id),
        "deviceName": device.name,
        "deviceType": device.type,
        "data": [serialize_telemetry(telemetry) for telemetry in telemetries],
    }


@router.get(
    "/{device_id}/telemetry/latest",
    name="Get Latest Device Telemetry",
    description="Mengambil telemetry terbaru milik satu device.",
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
                                "ts": 1781490918553,
                                "temperature": 28.5,
                                "humidity": 75.2,
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
                        "details": "Device ID must be a valid UUID"
                    }
                }
            },
        },
        404: {
            "description": "Not Found",
            "content": {
                "application/json": {
                    "example": {
                        "error": (
                            "Telemetry for device ID "
                            "550e8400-e29b-41d4-a716-446655440000 not found"
                        )
                    }
                }
            },
        },
    },
)
async def latest_telemetry(
    device_id: UUID,
    database: Session = Depends(get_db),
):
    device, telemetry = await telemetry_service.latest(
        database,
        device_id=device_id,
    )
    return {
        "message": "Latest telemetry found",
        "data": telemetry_service.response(device, telemetry),
    }
