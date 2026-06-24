from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.swagger import API_PREFIX, configure_swagger
from app.api.v1.router import api_router
from app.core.database_cassandra import cassandra_database
from app.core.database_pg import (
    check_postgres_connection,
    close_postgres_connection,
    create_postgres_tables,
)
from app.mqtt.mqtt import start_mqtt_subscriber, stop_mqtt_subscriber
from app.services.telemetry_service import telemetry_service
from app.websocket.websocket import (
    router as websocket_router,
    websocket_manager,
)


@asynccontextmanager
async def application_lifespan(app: FastAPI):
    try:
        check_postgres_connection()
        create_postgres_tables()
        cassandra_database.connect()
        telemetry_service.prepare_queries()
        await start_mqtt_subscriber()
        yield
    finally:
        await stop_mqtt_subscriber()
        await websocket_manager.shutdown()
        cassandra_database.shutdown()
        close_postgres_connection()


app = FastAPI(
    title="Device Management API",
    description="API untuk mengelola perangkat IoT dan telemetry.",
    version="1.0.0",
    docs_url="/api-docs",
    redoc_url=None,
    lifespan=application_lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix=API_PREFIX)
app.include_router(websocket_router)


@app.exception_handler(HTTPException)
async def handle_http_error(request: Request, error: HTTPException):
    return JSONResponse(
        status_code=error.status_code,
        content=error.detail,
        headers=error.headers,
    )


@app.exception_handler(RequestValidationError)
async def handle_validation_error(
    request: Request,
    error: RequestValidationError,
):
    message = "Invalid request payload"

    if error.errors():
        field = error.errors()[0]["loc"][-1]

        if field == "device_id":
            message = "Device ID must be a valid UUID"
        elif field in {"page", "limit"}:
            message = "Query parameter 'page' or 'limit' must be a valid number"
        elif field in {"start_month", "end_month"}:
            message = (
                "Query parameter 'start_month' and 'end_month' "
                "must use YYYY-MM format"
            )
        elif field in {"name", "type", "status"}:
            message = f"Attribute '{field}' is required"

    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={
            "error": "Validation failed",
            "details": message,
        },
    )


@app.exception_handler(Exception)
async def handle_internal_error(request: Request, error: Exception):
    print(f"Unhandled application error: {error}")
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"error": "Internal server error"},
    )


configure_swagger(app)
