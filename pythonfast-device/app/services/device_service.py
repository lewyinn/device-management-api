import hashlib
import json
from math import ceil
from uuid import UUID

from fastapi import HTTPException, status
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.db.models_pg import Device


def serialize_device(device: Device) -> dict:
    return {
        "id": str(device.id),
        "name": device.name,
        "type": device.type,
        "status": device.status,
    }


class DeviceService:
    def create(
        self,
        database: Session,
        *,
        name: str | None,
        device_type: str | None,
        device_status: str | None,
    ) -> Device:
        if not name:
            self._validation_error("Attribute 'name' is required")
        if not device_type:
            self._validation_error("Attribute 'type' is required")

        normalized_status = device_status or "active"
        self._validate_status(normalized_status)
        device = Device(
            name=name,
            type=device_type,
            status=normalized_status,
        )

        try:
            database.add(device)
            database.commit()
            database.refresh(device)
            return device
        except SQLAlchemyError:
            database.rollback()
            self._database_error()

    def list(self, database: Session, page: int, limit: int) -> dict:
        normalized_page = max(page, 1)
        normalized_limit = limit if 1 <= limit <= 50 else 10 if limit < 1 else 50
        total_data = database.query(Device).count()
        devices = (
            database.query(Device)
            .order_by(Device.name.asc())
            .offset((normalized_page - 1) * normalized_limit)
            .limit(normalized_limit)
            .all()
        )
        data = [serialize_device(device) for device in devices]

        return {
            "snapshot": hashlib.sha256(
                json.dumps(
                    {"total_data": total_data, "data": data},
                    sort_keys=True,
                ).encode("utf-8")
            ).hexdigest(),
            "meta": {
                "page": normalized_page,
                "limit": normalized_limit,
                "total_data": total_data,
                "total_pages": ceil(total_data / normalized_limit) if total_data else 0,
            },
            "data": data,
        }

    def get(self, database: Session, device_id: UUID | str) -> Device:
        normalized_device_id = str(device_id)
        device = (
            database.query(Device)
            .filter(Device.id == normalized_device_id)
            .first()
        )
        if device is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail={"error": f"Device ID {normalized_device_id} not found"},
            )
        return device

    def update(
        self,
        database: Session,
        device_id: UUID,
        *,
        name: str | None,
        device_type: str | None,
        device_status: str | None,
    ) -> Device:
        if not name or not device_type or not device_status:
            self._validation_error(
                "All attributes (name, type, status) are required for PUT method"
            )

        self._validate_status(device_status)
        device = self.get(database, device_id)
        device.name = name
        device.type = device_type
        device.status = device_status
        return self._save(database, device)

    def patch(self, database: Session, device_id: UUID, changes: dict) -> Device:
        if not changes:
            self._validation_error("At least one field must be provided")

        device = self.get(database, device_id)

        if "name" in changes:
            if not changes["name"]:
                self._validation_error("Attribute 'name' is required")
            device.name = changes["name"]

        if "type" in changes:
            if not changes["type"]:
                self._validation_error("Attribute 'type' is required")
            device.type = changes["type"]

        if "status" in changes:
            self._validate_status(changes["status"])
            device.status = changes["status"]

        return self._save(database, device)

    def delete(self, database: Session, device_id: UUID):
        device = self.get(database, device_id)

        try:
            database.delete(device)
            database.commit()
        except SQLAlchemyError:
            database.rollback()
            self._database_error()

    def _save(self, database: Session, device: Device) -> Device:
        try:
            database.commit()
            database.refresh(device)
            return device
        except SQLAlchemyError:
            database.rollback()
            self._database_error()

    @staticmethod
    def _validate_status(device_status: str):
        if device_status not in {"active", "inactive"}:
            DeviceService._validation_error("Status must be 'active' or 'inactive'")

    @staticmethod
    def _validation_error(message: str):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "Validation failed", "details": message},
        )

    @staticmethod
    def _database_error():
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "Internal server error"},
        )


device_service = DeviceService()
