import re
from datetime import datetime, timezone
from time import time
from uuid import UUID

from fastapi import HTTPException, status
from fastapi.concurrency import run_in_threadpool
from sqlalchemy.orm import Session

from app.core.database_cassandra import cassandra_database
from app.db.models_cassandra import TelemetryReading
from app.db.models_pg import Device
from app.services.device_service import device_service, serialize_device

RECORD_MONTH_PATTERN = re.compile(r"^\d{4}-(0[1-9]|1[0-2])$")
DEFAULT_START_MONTH = "2026-01"
DEFAULT_END_MONTH = "2026-12"


def serialize_telemetry(telemetry: TelemetryReading) -> dict:
    return {
        "ts": telemetry.ts,
        "temperature": telemetry.temperature,
        "humidity": telemetry.humidity,
    }


def get_record_month(ts: int) -> str:
    value = datetime.fromtimestamp(ts / 1000, timezone.utc)
    return f"{value.year:04d}-{value.month:02d}"


def build_month_range(start_month: str, end_month: str) -> list[str]:
    start_year, start_month_number = [int(value) for value in start_month.split("-")]
    end_year, end_month_number = [int(value) for value in end_month.split("-")]
    months = []
    year = start_year
    month = start_month_number

    while (year, month) <= (end_year, end_month_number):
        months.append(f"{year:04d}-{month:02d}")
        month += 1
        if month > 12:
            year += 1
            month = 1

    return months


class TelemetryService:
    def __init__(self):
        self.insert_statement = None
        self.history_statement = None
        self.latest_statement = None

    def prepare_queries(self):
        session = self._session()
        self.insert_statement = session.prepare(
            """
            INSERT INTO device_telemetries
            (device_id, record_month, ts, temperature, humidity)
            VALUES (?, ?, ?, ?, ?)
            """
        )
        self.history_statement = session.prepare(
            """
            SELECT ts, temperature, humidity
            FROM device_telemetries
            WHERE device_id = ?
            AND record_month = ?
            """
        )
        self.latest_statement = session.prepare(
            """
            SELECT ts, temperature, humidity
            FROM device_telemetries
            WHERE device_id = ?
            AND record_month = ?
            LIMIT 1
            """
        )

    async def create(
        self,
        database: Session,
        *,
        device_id: UUID,
        temperature,
        humidity,
    ) -> tuple[Device, TelemetryReading]:
        if not self._is_number(temperature) or not self._is_number(humidity):
            self._validation_error(
                "Attributes 'values.temperature' and 'values.humidity' must be numbers"
            )

        device = device_service.get(database, device_id)
        telemetry = await run_in_threadpool(
            self.insert,
            device_id=UUID(str(device.id)),
            ts=int(time() * 1000),
            temperature=float(temperature),
            humidity=float(humidity),
        )
        return device, telemetry

    async def history(
        self,
        database: Session,
        *,
        device_id: UUID,
        start_month: str | None,
        end_month: str | None,
    ) -> tuple[Device, list[TelemetryReading]]:
        device = device_service.get(database, device_id)
        normalized_start = start_month or DEFAULT_START_MONTH
        normalized_end = end_month or DEFAULT_END_MONTH
        self._validate_month_range(normalized_start, normalized_end)

        telemetries = await run_in_threadpool(
            self.find_by_months,
            device_id=UUID(str(device.id)),
            months=build_month_range(normalized_start, normalized_end),
        )
        return device, telemetries

    async def latest(
        self,
        database: Session,
        *,
        device_id: UUID,
    ) -> tuple[Device, TelemetryReading]:
        device = device_service.get(database, device_id)
        months = list(reversed(build_month_range(DEFAULT_START_MONTH, DEFAULT_END_MONTH)))
        telemetry = await run_in_threadpool(
            self.find_latest,
            device_id=UUID(str(device.id)),
            months=months,
        )

        if telemetry is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"error": f"Telemetry for device ID {device.id} not found"},
            )

        return device, telemetry

    def insert(
        self,
        *,
        device_id: UUID,
        ts: int,
        temperature: float,
        humidity: float,
    ) -> TelemetryReading:
        self._session().execute(
            self.insert_statement,
            (device_id, get_record_month(ts), ts, temperature, humidity),
        )
        return TelemetryReading(ts, temperature, humidity)

    def find_by_months(
        self,
        *,
        device_id: UUID,
        months: list[str],
    ) -> list[TelemetryReading]:
        telemetries = []

        for month in months:
            rows = self._session().execute(
                self.history_statement,
                (device_id, month),
            )
            telemetries.extend(self._to_reading(row) for row in rows)

        return sorted(telemetries, key=lambda item: item.ts, reverse=True)

    def find_latest(
        self,
        *,
        device_id: UUID,
        months: list[str],
    ) -> TelemetryReading | None:
        for month in months:
            row = self._session().execute(
                self.latest_statement,
                (device_id, month),
            ).one()
            if row:
                return self._to_reading(row)

        return None

    @staticmethod
    def response(device: Device, telemetry: TelemetryReading) -> dict:
        return {
            "device_id": str(device.id),
            "deviceName": device.name,
            "deviceType": device.type,
            "data": serialize_telemetry(telemetry),
        }

    @staticmethod
    def websocket_data(device: Device, telemetry: TelemetryReading) -> tuple[dict, dict]:
        return serialize_device(device), serialize_telemetry(telemetry)

    @staticmethod
    def _to_reading(row) -> TelemetryReading:
        return TelemetryReading(
            ts=row.ts,
            temperature=row.temperature,
            humidity=row.humidity,
        )

    @staticmethod
    def _validate_month_range(start_month: str, end_month: str):
        if (
            not RECORD_MONTH_PATTERN.fullmatch(start_month)
            or not RECORD_MONTH_PATTERN.fullmatch(end_month)
            or start_month > end_month
        ):
            TelemetryService._validation_error(
                "Query parameter 'start_month' and 'end_month' must use YYYY-MM format"
            )

    @staticmethod
    def _is_number(value):
        return isinstance(value, (int, float)) and not isinstance(value, bool)

    @staticmethod
    def _validation_error(message: str):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "Validation failed", "details": message},
        )

    @staticmethod
    def _session():
        if cassandra_database.session is None:
            raise RuntimeError("Cassandra is not connected")
        return cassandra_database.session


telemetry_service = TelemetryService()
