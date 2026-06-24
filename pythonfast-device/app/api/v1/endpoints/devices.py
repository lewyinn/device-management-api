from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, Depends, Response, status
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session

from app.api.dependencies.device_http_protection import (
    read_rate_limit,
    register_throttling,
)
from app.core.database_pg import get_db
from app.services.device_service import device_service, serialize_device
from app.services.notification_service import send_device_registered_notification
from app.websocket.websocket import websocket_manager

router = APIRouter(prefix="/devices", tags=["Devices"])


class DeviceCreate(BaseModel):
    name: str | None = None
    type: str | None = None
    status: str | None = "active"

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "name": "Sensor-Suhu",
                "type": "PM2120",
                "status": "active",
            }
        }
    )


class DeviceUpdate(BaseModel):
    name: str | None = None
    type: str | None = None
    status: str | None = None

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "name": "Sensor-Suhu",
                "type": "PM2120",
                "status": "inactive",
            }
        }
    )


class DevicePatch(DeviceUpdate):
    model_config = ConfigDict(json_schema_extra={"example": {"status": "inactive"}})


@router.post(
    "",
    dependencies=[Depends(register_throttling)],
    status_code=status.HTTP_201_CREATED,
    name="Create Device",
    description="Mendaftarkan device baru ke PostgreSQL.",
    responses={
        201: {
            "description": "OK",
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
            "description": "Request tidak valid",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Validation failed",
                        "details": "Attribute 'name' is required",
                    }
                }
            },
        },
        429: {
            "description": "Too Many Requests",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Request throttled",
                        "type": "throttling",
                        "details": (
                            "Device registration endpoint only accepts one "
                            "request every 1000 milliseconds"
                        ),
                        "retry_after_ms": 953,
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
async def create_device(
    payload: DeviceCreate,
    background_tasks: BackgroundTasks,
    database: Session = Depends(get_db),
):
    device = device_service.create(
        database,
        name=payload.name,
        device_type=payload.type,
        device_status=payload.status,
    )
    device_data = serialize_device(device)

    await websocket_manager.broadcast_device_registered(device_data)
    background_tasks.add_task(
        send_device_registered_notification,
        device_data,
    )

    return {
        "message": "Device successfully registered",
        "data": device_data,
    }


@router.get(
    "",
    dependencies=[Depends(read_rate_limit)],
    name="Read All Devices",
    description="Mengambil daftar device dari PostgreSQL dengan pagination.",
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
                            "total_data": 1,
                            "total_pages": 1,
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
                        "details": "Query parameter 'page' or 'limit' must be a valid number"
                    }
                }
            }
        },
        429: {
            "description": "Too Many Requests",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Rate limit exceeded",
                        "type": "rate_limiting",
                        "details": "Device read endpoint only allows 3 requests every 60 seconds",
                        "retry_after_seconds": 59,
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
async def list_devices(
    page: int = 1,
    limit: int = 10,
    database: Session = Depends(get_db),
):
    result = device_service.list(database, page, limit)
    return {
        "message": "Success retrieving devices",
        "meta": result["meta"],
        "data": result["data"],
    }


@router.get(
    "/{device_id}",
    name="Read Device Details",
    description="Mengambil detail satu device berdasarkan UUID.",
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
                        "details": "Device ID must be a valid UUID"
                    }
                }
            }
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
async def detail_device(
    device_id: UUID,
    database: Session = Depends(get_db),
):
    device = device_service.get(database, device_id)
    return {"message": "Device found", "data": serialize_device(device)}


@router.put(
    "/{device_id}",
    name="Update Device All",
    description="Mengganti seluruh atribut device.",
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
                        "details": "All attributes (name, type, status) are required for PUT method"
                    }
                }
            }
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
async def update_device(
    device_id: UUID,
    payload: DeviceUpdate,
    database: Session = Depends(get_db),
):
    device = device_service.update(
        database,
        device_id,
        name=payload.name,
        device_type=payload.type,
        device_status=payload.status,
    )
    return {
        "message": "Device data fully updated successfully",
        "data": serialize_device(device),
    }


@router.patch(
    "/{device_id}",
    name="Update Device Partial",
    description="Mengubah satu atau beberapa atribut device.",
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
                        "details": "At least one field must be provided"
                    }
                }
            }
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
async def patch_device(
    device_id: UUID,
    payload: DevicePatch,
    database: Session = Depends(get_db),
):
    device = device_service.patch(
        database,
        device_id,
        payload.model_dump(exclude_unset=True),
    )
    return {
        "message": "Device status updated successfully",
        "data": serialize_device(device),
    }


@router.delete(
    "/{device_id}",
    name="Delete Device",
    description="Menghapus device dari PostgreSQL.",
    status_code=status.HTTP_204_NO_CONTENT,
    responses={
        204: {"description": "No Content"},
        400: {
            "description": "Bad Request",
            "content": {
                "application/json": {
                    "example": {
                        "error": "Validation failed",
                        "details": "At least one field must be provided"
                    }
                }
            }
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
async def delete_device(
    device_id: UUID,
    database: Session = Depends(get_db),
):
    device_service.delete(database, device_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
