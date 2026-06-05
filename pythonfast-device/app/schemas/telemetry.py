from typing import Any

from pydantic import BaseModel, ConfigDict


class TelemetryValues(BaseModel):
    temperature: Any = None
    humidity: Any = None

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "temperature": 28.5,
                "humidity": 75.2,
            }
        }
    )


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
