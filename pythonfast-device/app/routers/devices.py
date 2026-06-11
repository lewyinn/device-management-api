from math import ceil
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models import Device
from app.schemas import DeviceCreate, DevicePatch, DeviceUpdate

router = APIRouter(prefix="/api/v1/devices", tags=["Devices"])


def device_response(device: Device):
    return {
        "id": device.id,
        "name": device.name,
        "type": device.type,
        "status": device.status,
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

    return {
        "message": "Device successfully registered",
        "data": device_response(device),
    }


@router.get(
    "",
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
    if page < 1:
        page = 1
    if limit < 1:
        limit = 10
    if limit > 50:
        limit = 50

    total_data = db.query(Device).count()
    offset = (page - 1) * limit
    devices = db.query(Device).order_by(Device.name.asc()).offset(offset).limit(limit).all()

    return {
        "message": "Success retrieving devices",
        "meta": {
            "page": page,
            "limit": limit,
            "total_data": total_data,
            "total_pages": ceil(total_data / limit) if total_data else 0,
        },
        "data": [device_response(device) for device in devices],
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
