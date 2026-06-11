from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.openapi.utils import get_openapi
from fastapi.responses import JSONResponse

from app.core.cassandra import connect_cassandra, shutdown_cassandra
from app.core.database import check_sql_database, close_sql_database, sync_sql_database
from app.routers import devices_router, device_telemetry_router


API_PREFIX = "/api/v1"
SERVER_URL = "http://localhost:8000/api/v1"


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        check_sql_database()
        sync_sql_database()
        connect_cassandra()
        yield
    finally:
        shutdown_cassandra()
        close_sql_database()


app = FastAPI(
    title="Device Management API",
    description=f"API untuk mengelola perangkat IoT.",
    version="1.0.0",
    docs_url="/api-docs",
    lifespan=lifespan,
)

app.include_router(devices_router)
app.include_router(device_telemetry_router)


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
        elif field in ["page", "limit"]:
            message = "Query parameter 'page' or 'limit' must be a valid number"
        elif field in ["start_month", "end_month"]:
            message = "Query parameter 'start_month' and 'end_month' must use YYYY-MM format"
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
