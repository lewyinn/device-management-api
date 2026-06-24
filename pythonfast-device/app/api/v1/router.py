from fastapi import APIRouter

from app.api.v1.endpoints.devices import router as devices_router
from app.api.v1.endpoints.telemetry import router as telemetry_router

api_router = APIRouter()
api_router.include_router(devices_router)
api_router.include_router(telemetry_router)
