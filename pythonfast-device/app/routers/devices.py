import asyncio
import hashlib
import json
from math import ceil
from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.dependencies.device_http_protection import read_rate_limit, register_rate_limit, register_throttling, read_throttling
from app.models import Device
from app.schemas import DeviceCreate, DevicePatch, DeviceUpdate
from app.services.telegram import send_device_registered_telegram_notification

router = APIRouter(prefix="/api/v1/devices", tags=["Devices"])
LONG_POLL_TIMEOUT_SECONDS = 25
LONG_POLL_INTERVAL_SECONDS = 1


def device_response(device: Device):
    return {
        "id": str(device.id),
        "name": device.name,
        "type": device.type,
        "status": device.status,
    }


def device_page_response(db: Session, page: int = 1, limit: int = 10):
    if page < 1:
        page = 1
    if limit < 1:
        limit = 10
    if limit > 50:
        limit = 50

    total_data = db.query(Device).count()
    offset = (page - 1) * limit
    devices = db.query(Device).order_by(Device.name.asc()).offset(offset).limit(limit).all()
    data = [device_response(device) for device in devices]

    return {
        "snapshot": hashlib.sha256(
            json.dumps({"total_data": total_data, "data": data}, sort_keys=True).encode("utf-8")
        ).hexdigest(),
        "meta": {
            "page": page,
            "limit": limit,
            "total_data": total_data,
            "total_pages": ceil(total_data / limit) if total_data else 0,
        },
        "data": data,
    }


def get_device(db: Session, device_id: UUID):
    device_id = str(device_id)
    device = db.query(Device).filter(Device.id == device_id).first()
    if device is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": f"Device ID {device_id} not found"},
        )
    return device


def check_status(device_status: str):
    if device_status not in ["active", "inactive"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error": "Validation failed",
                "details": "Status must be 'active' or 'inactive'",
            },
        )


@router.post(
    "",
    # dependencies=[Depends(register_rate_limit)],
    dependencies=[Depends(register_throttling)],
    status_code=status.HTTP_201_CREATED,
    name="Create Device",
    description="Mendaftarkan perangkat baru ke dalam sistem.",
    responses={
        201: {
            "description": "Created",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Device successfully registered",
                        "data": {
                            "id": "550e8400-e29b-41d4-a716-446655440000",
                            "name": "Sensor-Suhu",
                            "type": "PM2120",
                            "status": "active",
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
                        "details": "Attribute 'name' is required",
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
async def create_device(
    payload: DeviceCreate,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    name = payload.name
    device_type = payload.type
    device_status = payload.status or "active"

    if not name:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "Validation failed", "details": "Attribute 'name' is required"},
        )
    if not device_type:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "Validation failed", "details": "Attribute 'type' is required"},
        )

    check_status(device_status)

    device = Device(name=name, type=device_type, status=device_status)

    try:
        db.add(device)
        db.commit()
        db.refresh(device)
    except Exception:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "Internal server error"},
        )

    device_data = device_response(device)
    background_tasks.add_task(send_device_registered_telegram_notification, device_data)

    return {
        "message": "Device successfully registered",
        "data": device_data,
    }


@router.get(
    "",
    dependencies=[Depends(read_rate_limit)],
    # dependencies=[Depends(read_throttling)],
    name="Read All Devices",
    description="Mengambil daftar seluruh perangkat dengan pagination.",
    responses={
        200: {
            "description": "OK",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Success retrieving devices",
                        "meta": {
                            "page": 1,
                            "limit": 10,
                            "total_data": 50,
                            "total_pages": 5,
                        },
                        "data": [
                            {
                                "id": "550e8400-e29b-41d4-a716-446655440000",
                                "name": "Sensor-Suhu",
                                "type": "PM2120",
                                "status": "active",
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
                        "details": "Query parameter 'page' or 'limit' must be a valid number",
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
async def list_devices(
    page: int = 1,
    limit: int = 10,
    db: Session = Depends(get_db),
):
    device_page = device_page_response(db, page, limit)

    return {
        "message": "Success retrieving devices",
        "meta": device_page["meta"],
        "data": device_page["data"],
    }


@router.get(
    "/short-poll",
    dependencies=[Depends(read_rate_limit)],
    name="Short Poll Devices",
    description="Mengambil daftar device untuk pola HTTP short polling.",
)
async def short_poll_devices(
    page: int = 1,
    limit: int = 10,
    db: Session = Depends(get_db),
):
    device_page = device_page_response(db, page, limit)

    return {
        "message": "Short polling response: devices retrieved",
        "pattern": "short_polling",
        "details": "Client requests this endpoint repeatedly at a fixed interval to refresh device data",
        "snapshot": device_page["snapshot"],
        "meta": device_page["meta"],
        "data": device_page["data"],
    }


@router.get(
    "/long-poll",
    dependencies=[Depends(read_rate_limit)],
    name="Long Poll Devices",
    description="Menahan request sampai data device berubah atau timeout.",
)
async def long_poll_devices(
    snapshot: str | None = None,
    page: int = 1,
    limit: int = 10,
    db: Session = Depends(get_db),
):
    deadline = asyncio.get_running_loop().time() + LONG_POLL_TIMEOUT_SECONDS

    while asyncio.get_running_loop().time() < deadline:
        device_page = device_page_response(db, page, limit)

        if not snapshot or device_page["snapshot"] != snapshot:
            return {
                "message": "Long polling response: device data changed",
                "pattern": "long_polling",
                "details": "Server held the HTTP request until the device list snapshot changed",
                "snapshot": device_page["snapshot"],
                "meta": device_page["meta"],
                "data": device_page["data"],
            }

        await asyncio.sleep(LONG_POLL_INTERVAL_SECONDS)

    return {
        "message": "Long polling timeout: device data did not change",
        "pattern": "long_polling",
        "details": f"No device data change was detected within {LONG_POLL_TIMEOUT_SECONDS} seconds",
        "snapshot": snapshot,
        "data": None,
    }


@router.get(
    "/{device_id}",
    name="Read Device Details",
    description="Mengambil detail satu perangkat berdasarkan ID.",
    responses={
        200: {
            "description": "OK",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Device found",
                        "data": {
                            "id": "550e8400-e29b-41d4-a716-446655440000",
                            "name": "Sensor-Suhu",
                            "type": "PM2120",
                            "status": "active",
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
                    "example": {"error": "Internal server error"}
                }
            },
        },
    },
)
async def detail_device(
    device_id: UUID,
    db: Session = Depends(get_db),
):
    device = get_device(db, device_id)
    return {
        "message": "Device found",
        "data": device_response(device),
    }


@router.put(
    "/{device_id}",
    name="Update Device All",
    description="Mengubah seluruh data atribut perangkat sekaligus. Semua atribut name, type, dan status wajib dikirimkan.",
    responses={
        200: {
            "description": "OK",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Device data fully updated successfully",
                        "data": {
                            "id": "550e8400-e29b-41d4-a716-446655440000",
                            "name": "Sensor-Suhu",
                            "type": "PM2120",
                            "status": "inactive",
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
                        "details": "All attributes (name, type, status) are required for PUT method",
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
                    "example": {"error": "Internal server error"}
                }
            },
        },
    },
)
async def update_device(
    device_id: UUID,
    payload: DeviceUpdate,
    db: Session = Depends(get_db),
):
    name = payload.name
    device_type = payload.type
    device_status = payload.status

    if not name or not device_type or not device_status:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error": "Validation failed",
                "details": "All attributes (name, type, status) are required for PUT method",
            },
        )

    check_status(device_status)
    device = get_device(db, device_id)

    try:
        device.name = name
        device.type = device_type
        device.status = device_status
        db.commit()
        db.refresh(device)
    except Exception:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "Internal server error"},
        )

    return {
        "message": "Device data fully updated successfully",
        "data": device_response(device),
    }


@router.patch(
    "/{device_id}",
    name="Update Device Partial",
    description="Mengubah status atau sebagian data perangkat.",
    responses={
        200: {
            "description": "OK",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Device status updated successfully",
                        "data": {
                            "id": "550e8400-e29b-41d4-a716-446655440000",
                            "name": "Sensor-Suhu",
                            "type": "PM2120",
                            "status": "inactive",
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
                        "details": "At least one field must be provided",
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
                    "example": {"error": "Internal server error"}
                }
            },
        },
    },
)
async def patch_device(
    device_id: UUID,
    payload: DevicePatch,
    db: Session = Depends(get_db),
):
    data = payload.model_dump(exclude_unset=True)

    if not data:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "Validation failed", "details": "At least one field must be provided"},
        )

    device = get_device(db, device_id)

    if "name" in data:
        if not data["name"]:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"error": "Validation failed", "details": "Attribute 'name' is required"},
            )
        device.name = data["name"]

    if "type" in data:
        if not data["type"]:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail={"error": "Validation failed", "details": "Attribute 'type' is required"},
            )
        device.type = data["type"]

    if "status" in data:
        check_status(data["status"])
        device.status = data["status"]

    try:
        db.commit()
        db.refresh(device)
    except Exception:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "Internal server error"},
        )

    return {
        "message": "Device status updated successfully",
        "data": device_response(device),
    }


@router.delete(
    "/{device_id}",
    name="Delete Device",
    description="Menghapus perangkat dari sistem.",
    status_code=status.HTTP_204_NO_CONTENT,
    responses={
        204: {"description": "No Content"},
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
                    "example": {"error": "Internal server error"}
                }
            },
        },
    },
)
async def delete_device(
    device_id: UUID,
    db: Session = Depends(get_db),
):
    device = get_device(db, device_id)

    try:
        db.delete(device)
        db.commit()
    except Exception:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "Internal server error"},
        )

    return Response(status_code=status.HTTP_204_NO_CONTENT)
