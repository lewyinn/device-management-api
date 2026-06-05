import time
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models import Device, DeviceTelemetry
from app.schemas import TelemetryCreate

devices_router = APIRouter(prefix="/api/v1/devices", tags=["Telemetry"])
telemetry_router = APIRouter(prefix="/api/v1/telemetry", tags=["Telemetry"])


def bad_request_example(details: str):
    return {
        "description": "Bad Request",
        "content": {
            "application/json": {
                "example": {
                    "error": "Validation failed",
                    "details": details,
                }
            }
        },
    }


SERVER_ERROR = {
    "description": "Internal Server Error",
    "content": {
        "application/json": {"example": {"error": "Internal server error"}}
    },
}

DEVICE_NOT_FOUND = {
    "description": "Device Not Found",
    "content": {
        "application/json": {"example": {"error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"}}
    },
}

TELEMETRY_NOT_FOUND = {
    "description": "Telemetry Not Found",
    "content": {
        "application/json": {"example": {"error": "Telemetry ID 1 not found"}}
    },
}

CONFLICT = {
    "description": "Duplicate telemetry timestamp",
    "content": {
        "application/json": {
            "example": {
                "error": "Duplicate telemetry timestamp",
                "details": "Telemetry for device ID 550e8400-e29b-41d4-a716-446655440000 at ts 1717488000000 already exists",
            }
        }
    },
}


def validation_error(message: str):
    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail={"error": "Validation failed", "details": message},
    )


def get_device(db: Session, device_id: UUID):
    device_id = str(device_id)
    device = db.query(Device).filter(Device.id == device_id).first()
    if device is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": f"Device ID {device_id} not found"},
        )
    return device


def get_telemetry(db: Session, telemetry_id: int):
    telemetry = (
        db.query(DeviceTelemetry)
        .filter(DeviceTelemetry.id == telemetry_id)
        .first()
    )
    if telemetry is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": f"Telemetry ID {telemetry_id} not found"},
        )
    return telemetry


def telemetry_response(telemetry: DeviceTelemetry, device: Device):
    return {
        "device_id": device.id,
        "deviceName": device.name,
        "deviceType": device.type,
        "data": {
            "ts": telemetry.ts,
            "temperature": telemetry.temperature,
            "humidity": telemetry.humidity,
        },
    }


def telemetry_item(telemetry: DeviceTelemetry):
    return {
        "ts": telemetry.ts,
        "temperature": telemetry.temperature,
        "humidity": telemetry.humidity,
    }


def is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


@devices_router.post(
    "/{device_id}/telemetry",
    name="Create Device Telemetry",
    description="Mencatat data telemetry device.",
    status_code=status.HTTP_201_CREATED,
    responses={
        201: {
            "description": "Telemetry successfully recorded",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Telemetry successfully recorded",
                        "data": {
                            "device_id": "550e8400-e29b-41d4-a716-446655440000",
                            "deviceName": "Sensor-Suhu",
                            "deviceType": "PM2120",
                            "data": {
                                "ts": 1717488000000,
                                "temperature": 25.5,
                                "humidity": 60.0,
                            },
                        },
                    }
                }
            },
        },
        400: bad_request_example(
            "Attributes 'values.temperature' and 'values.humidity' must be numbers"
        ),
        404: DEVICE_NOT_FOUND,
        409: CONFLICT,
        500: SERVER_ERROR,
    },
)
async def create_telemetry(
    device_id: UUID,
    payload: TelemetryCreate,
    db: Session = Depends(get_db),
):
    values = payload.values.model_dump() if payload.values else {}
    temperature = values.get("temperature")
    humidity = values.get("humidity")

    if not is_number(temperature) or not is_number(humidity):
        validation_error(
            "Attributes 'values.temperature' and 'values.humidity' must be numbers"
        )

    device = get_device(db, device_id)
    ts = int(time.time() * 1000)

    duplicate = (
        db.query(DeviceTelemetry)
        .filter(
            DeviceTelemetry.device_id == device.id,
            DeviceTelemetry.ts == ts,
        )
        .first()
    )
    if duplicate:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "error": "Duplicate telemetry timestamp",
                "details": f"Telemetry for device ID {device.id} at ts {ts} already exists",
            },
        )

    telemetry = DeviceTelemetry(
        device_id=device.id,
        ts=ts,
        temperature=temperature,
        humidity=humidity,
    )
    db.add(telemetry)

    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "error": "Duplicate telemetry timestamp",
                "details": f"Telemetry for device ID {device.id} at ts {ts} already exists",
            },
        )

    db.refresh(telemetry)

    return {
        "message": "Telemetry successfully recorded",
        "data": telemetry_response(telemetry, device),
    }


@devices_router.get(
    "/{device_id}/telemetry",
    name="Get Device Telemetry",
    description="Mengambil seluruh telemetry milik satu device.",
    responses={
        200: {
            "description": "Success retrieving telemetry",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Success retrieving telemetry",
                        "device_id": "550e8400-e29b-41d4-a716-446655440000",
                        "deviceName": "Sensor-Suhu",
                        "deviceType": "PM2120",
                        "data": [
                            {
                                "ts": 1717488000000,
                                "temperature": 25.5,
                                "humidity": 60.0,
                            },
                            {
                                "ts": 1717488060000,
                                "temperature": 25.7,
                                "humidity": 59.5,
                            },
                        ],
                    }
                }
            },
        },
        400: bad_request_example("Device ID must be a valid UUID"),
        404: DEVICE_NOT_FOUND,
        500: SERVER_ERROR,
    },
)
async def list_telemetry(
    device_id: UUID,
    db: Session = Depends(get_db),
):
    device = get_device(db, device_id)
    telemetries = (
        db.query(DeviceTelemetry)
        .filter(DeviceTelemetry.device_id == device.id)
        .order_by(DeviceTelemetry.ts.desc())
        .all()
    )

    return {
        "message": "Success retrieving telemetry",
        "device_id": device.id,
        "deviceName": device.name,
        "deviceType": device.type,
        "data": [telemetry_item(telemetry) for telemetry in telemetries],
    }


@devices_router.get(
    "/{device_id}/telemetry/latest",
    name="Get Latest Device Telemetry",
    description="Mengambil telemetry terbaru milik satu device.",
    responses={
        200: {
            "description": "Latest telemetry found",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Latest telemetry found",
                        "data": {
                            "device_id": "550e8400-e29b-41d4-a716-446655440000",
                            "deviceName": "Sensor-Suhu",
                            "deviceType": "PM2120",
                            "data": {
                                "ts": 1717488000000,
                                "temperature": 25.5,
                                "humidity": 60.0,
                            },
                        },
                    }
                }
            },
        },
        400: bad_request_example("Device ID must be a valid UUID"),
        404: DEVICE_NOT_FOUND,
        500: SERVER_ERROR,
    },
)
async def latest_telemetry(
    device_id: UUID,
    db: Session = Depends(get_db),
):
    device = get_device(db, device_id)
    telemetry = (
        db.query(DeviceTelemetry)
        .filter(DeviceTelemetry.device_id == device.id)
        .order_by(DeviceTelemetry.ts.desc())
        .first()
    )

    if telemetry is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": f"Telemetry for device ID {device.id} not found"},
        )

    return {
        "message": "Latest telemetry found",
        "data": telemetry_response(telemetry, device),
    }


@telemetry_router.delete(
    "/{telemetry_id}",
    name="Delete Telemetry",
    description="Menghapus data telemetry berdasarkan ID transaksi.",
    status_code=status.HTTP_204_NO_CONTENT,
    responses={
        204: {"description": "Telemetry deleted"},
        400: bad_request_example("Telemetry ID must be a valid number"),
        404: TELEMETRY_NOT_FOUND,
        500: SERVER_ERROR,
    },
)
async def delete_telemetry(
    telemetry_id: int,
    db: Session = Depends(get_db),
):
    telemetry = get_telemetry(db, telemetry_id)
    db.delete(telemetry)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
