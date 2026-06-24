from dataclasses import dataclass

CREATE_TELEMETRY_TABLE = """
CREATE TABLE IF NOT EXISTS device_telemetries (
    device_id uuid,
    record_month text,
    ts bigint,
    temperature double,
    humidity double,
    PRIMARY KEY ((device_id, record_month), ts)
) WITH CLUSTERING ORDER BY (ts DESC);
"""


@dataclass(frozen=True)
class TelemetryReading:
    ts: int
    temperature: float
    humidity: float
