from math import ceil
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models import Device
from app.schemas import DeviceCreate, DevicePartialUpdate, DeviceUpdate

router = APIRouter(prefix="/api/v1/devices", tags=["Devices"])

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


BAD_REQUEST = bad_request_example("Attribute 'name' is required")

NOT_FOUND = {
    "description": "Not Found",
    "content": {
        "application/json": {"example": {"error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"}}
    },
}

SERVER_ERROR = {
    "description": "Internal Server Error",
    "content": {
        "application/json": {"example": {"error": "Internal server error"}}
    },
}

COMMON_ERRORS = {
    400: BAD_REQUEST,
    500: SERVER_ERROR,
}

DETAIL_ERRORS = {
    **COMMON_ERRORS,
    404: NOT_FOUND,
}

def device_response(device: Device):
    return {
        "id": device.id,
        "name": device.name,
        "type": device.type,
        "status": device.status,
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


def check_status(device_status: str):
    if device_status not in ["active", "inactive"]:
        validation_error("Status must be 'active' or 'inactive'")

@router.post(
    "",
    status_code=status.HTTP_201_CREATED,
    description="Mendaftarkan perangkat baru ke dalam sistem.",
    responses={
        201: {
            "description": "Device berhasil terdaftar",
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
        **COMMON_ERRORS,
    }
)
async def create_device(
    payload: DeviceCreate,
    db: Session = Depends(get_db),
):
    if not payload.name:
        validation_error("Attribute 'name' is required")
    if not payload.type:
        validation_error("Attribute 'type' is required")

    device_status = payload.status or "active"
    check_status(device_status)

    device = Device(
        name=payload.name,
        type=payload.type,
        status=device_status,
    )
    db.add(device)
    db.commit()
    db.refresh(device)

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
            "description": "Success retrieving devices",
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
        **COMMON_ERRORS,
        400: bad_request_example(
            "Query parameter 'page' or 'limit' must be a valid number"
        ),
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
    devices = db.query(Device).offset(offset).limit(limit).all()

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
            "description": "Device found",
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
        **DETAIL_ERRORS,
        400: bad_request_example("Device ID must be a valid UUID"),
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
            "description": "Device data fully updated successfully",
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
        **DETAIL_ERRORS,
        400: bad_request_example(
            "All attributes (name, type, status) are required for PUT method"
        ),
    },
)
async def update_device(
    device_id: UUID,
    payload: DeviceUpdate,
    db: Session = Depends(get_db),
):
    if not payload.name or not payload.type or not payload.status:
        validation_error(
            "All attributes (name, type, status) are required for PUT method"
        )

    check_status(payload.status)

    device = get_device(db, device_id)
    device.name = payload.name
    device.type = payload.type
    device.status = payload.status

    db.commit()
    db.refresh(device)

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
            "description": "Device status updated successfully",
            "content": {
                "application/json": {
                    "example": {
                        "message": "Device partially updated successfully",
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
        **DETAIL_ERRORS,
        400: bad_request_example("At least one field must be provided"),
    },
)
async def patch_device(
    device_id: UUID,
    payload: DevicePartialUpdate,
    db: Session = Depends(get_db),
):
    data = payload.model_dump(exclude_unset=True)
    if not data:
        validation_error("At least one field must be provided")

    device = get_device(db, device_id)

    if "name" in data:
        if not payload.name:
            validation_error("Attribute 'name' is required")
        device.name = payload.name

    if "type" in data:
        if not payload.type:
            validation_error("Attribute 'type' is required")
        device.type = payload.type

    if "status" in data:
        check_status(payload.status)
        device.status = payload.status

    db.commit()
    db.refresh(device)

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
        204: {"description": "Device deleted"},
        **DETAIL_ERRORS,
        400: bad_request_example("Device ID must be a valid UUID"),
    },
)
async def delete_device(
    device_id: UUID,
    db: Session = Depends(get_db),
):
    device = get_device(db, device_id)
    db.delete(device)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
