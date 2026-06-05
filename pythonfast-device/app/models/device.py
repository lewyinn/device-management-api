from uuid import uuid4

from sqlalchemy import Column, String
from sqlalchemy.orm import relationship

from app.core.database import Base


class Device(Base):
    __tablename__ = "devices"

    id = Column(String, primary_key=True, default=lambda: str(uuid4()))
    name = Column(String, nullable=False)
    type = Column(String, nullable=False)
    status = Column(String, nullable=False, default="active")
    telemetries = relationship(
        "DeviceTelemetry",
        back_populates="device",
        cascade="all, delete-orphan",
    )

    def __repr__(self):
        return f"<Device {self.id}: {self.name}>"