from typing import Optional

from pydantic import BaseModel, ConfigDict


class DeviceCreate(BaseModel):
    name: Optional[str] = None
    type: Optional[str] = None
    status: Optional[str] = "active"

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
    name: Optional[str] = None
    type: Optional[str] = None
    status: Optional[str] = None

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
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "status": "inactive",
            }
        }
    )
