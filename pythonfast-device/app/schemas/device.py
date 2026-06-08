from typing import Optional

from pydantic import BaseModel


class DeviceCreate(BaseModel):
    name: Optional[str] = None
    type: Optional[str] = None
    status: Optional[str] = "active"


class DeviceUpdate(BaseModel):
    name: Optional[str] = None
    type: Optional[str] = None
    status: Optional[str] = None

