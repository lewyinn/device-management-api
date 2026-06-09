from sqlalchemy import Column, Float, ForeignKey, Index, Integer, String, UniqueConstraint
from sqlalchemy.orm import relationship

from app.core.database import Base


class DeviceTelemetry(Base):
    __tablename__ = "device_telemetries"
    __table_args__ = (
        UniqueConstraint("device_id", "ts", name="uq_device_telemetry_ts"),
        Index("idx_device_telemetry_device_id_ts", "device_id", "ts")
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    device_id = Column(String, ForeignKey("devices.id"), nullable=False)
    ts = Column(Integer, nullable=False)
    temperature = Column(Float, nullable=False)
    humidity = Column(Float, nullable=False)

    device = relationship("Device", back_populates="telemetries")
