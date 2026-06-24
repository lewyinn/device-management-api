from app.api.v1.endpoints.devices import router as devices_router
from app.api.v1.endpoints.telemetry import router as telemetry_router

__all__ = ["devices_router", "telemetry_router"]
