from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.openapi.utils import get_openapi
from fastapi.responses import JSONResponse

from app.core.database import Base, engine
from app.routers import devices_router, device_telemetry_router, telemetry_router

Base.metadata.create_all(bind=engine)

API_PREFIX = "/api/v1"
SERVER_URL = "http://localhost:8000/api/v1"

app = FastAPI(
    title="Device Management API",
    description=f"API untuk mengelola perangkat IoT.",
    version="1.0.0",
    docs_url="/api-docs",
)

app.include_router(devices_router)
app.include_router(device_telemetry_router)
app.include_router(telemetry_router)


@app.exception_handler(HTTPException)
async def handle_http_error(request: Request, error: HTTPException):
    return JSONResponse(
        status_code=error.status_code,
        content=error.detail,
        headers=error.headers,
    )


@app.exception_handler(RequestValidationError)
async def handle_validation_error(request: Request, error: RequestValidationError):
    message = "Invalid request payload"

    if error.errors():
        field = error.errors()[0]["loc"][-1]
        if field == "device_id":
            message = "Device ID must be a valid UUID"
        elif field == "telemetry_id":
            message = "Telemetry ID must be a valid number"
        elif field in ["page", "limit"]:
            message = "Query parameter 'page' or 'limit' must be a valid number"
        elif field in ["name", "type", "status"]:
            message = f"Attribute '{field}' is required"

    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"error": "Validation failed", "details": message},
    )


@app.exception_handler(Exception)
async def handle_internal_error(request: Request, error: Exception):
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"error": "Internal server error"},
    )


def custom_openapi():
    schema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
    )
    schema["servers"] = [{"url": SERVER_URL}]

    paths = {}
    for path, methods in schema["paths"].items():
        clean_path = path.removeprefix(API_PREFIX)
        paths[clean_path or path] = methods

    schema["paths"] = paths

    for path in schema["paths"].values():
        for method in path.values():
            method["responses"].pop("422", None)

    return schema


app.openapi = custom_openapi
