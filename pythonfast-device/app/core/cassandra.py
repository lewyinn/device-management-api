import os
from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID

from dotenv import load_dotenv

load_dotenv()

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

CREATE_KEYSPACE = """
CREATE KEYSPACE IF NOT EXISTS {keyspace}
WITH replication = {{
    'class': 'NetworkTopologyStrategy',
    'datacenter1': 1
}}
AND durable_writes = true;
"""


@dataclass(frozen=True)
class TelemetryReading:
    ts: int
    temperature: float
    humidity: float


def record_month(ts: int) -> str:
    value = datetime.fromtimestamp(ts / 1000, timezone.utc)
    return f"{value.year:04d}-{value.month:02d}"


class CassandraTelemetryRepository:
    def __init__(self):
        self.cluster = None
        self.admin_session = None
        self.session = None
        self.insert_statement = None
        self.history_statement = None
        self.latest_statement = None

    def connect(self):
        if self.session:
            return

        from cassandra.cluster import Cluster
        from cassandra.policies import DCAwareRoundRobinPolicy

        contact_points = [
            value.strip()
            for value in os.getenv("CASSANDRA_CONTACT_POINTS", "127.0.0.1").split(",")
            if value.strip()
        ]
        keyspace = os.getenv("CASSANDRA_KEYSPACE", "device_management")

        self.cluster = Cluster(
            contact_points=contact_points,
            load_balancing_policy=DCAwareRoundRobinPolicy(
                local_dc=os.getenv("CASSANDRA_LOCAL_DATACENTER", "datacenter1")
            ),
        )
        self.admin_session = self.cluster.connect()
        self.admin_session.execute(CREATE_KEYSPACE.format(keyspace=keyspace))
        self.session = self.cluster.connect(keyspace)
        self.session.execute(CREATE_TELEMETRY_TABLE)
        self.ensure_ts_bigint(keyspace)
        self.insert_statement = self.session.prepare(
            """
            INSERT INTO device_telemetries
            (device_id, record_month, ts, temperature, humidity)
            VALUES (?, ?, ?, ?, ?)
            """
        )
        self.history_statement = self.session.prepare(
            """
            SELECT ts, temperature, humidity
            FROM device_telemetries
            WHERE device_id = ?
            AND record_month = ?
            """
        )
        self.latest_statement = self.session.prepare(
            """
            SELECT ts, temperature, humidity
            FROM device_telemetries
            WHERE device_id = ?
            AND record_month = ?
            LIMIT 1
            """
        )
        print("Cassandra connection established")

    def shutdown(self):
        if self.cluster:
            self.cluster.shutdown()

        self.cluster = None
        self.admin_session = None
        self.session = None
        self.insert_statement = None
        self.history_statement = None
        self.latest_statement = None
        print("Cassandra connection closed")

    def ensure_ts_bigint(self, keyspace: str):
        result = self.session.execute(
            """
            SELECT type
            FROM system_schema.columns
            WHERE keyspace_name = %s
            AND table_name = %s
            AND column_name = %s
            """,
            (keyspace, "device_telemetries", "ts"),
        )
        row = result.one()
        column_type = row.type if row else None

        if column_type != "bigint":
            raise RuntimeError(
                f"device_telemetries.ts must be bigint, current type is {column_type or 'missing'}"
            )

    def insert(self, *, device_id: UUID, ts: int, temperature: float, humidity: float) -> TelemetryReading:
        self.session.execute(
            self.insert_statement,
            (device_id, record_month(ts), ts, temperature, humidity),
        )

        return TelemetryReading(ts=ts, temperature=temperature, humidity=humidity)

    def find_by_months(self, *, device_id: UUID, months: list[str]) -> list[TelemetryReading]:
        telemetries = []

        for month in months:
            result = self.session.execute(self.history_statement, (device_id, month))
            telemetries.extend(self.to_telemetry(row) for row in result)

        return sorted(telemetries, key=lambda telemetry: telemetry.ts, reverse=True)

    def find_latest(self, *, device_id: UUID, months: list[str]) -> TelemetryReading | None:
        for month in months:
            result = self.session.execute(self.latest_statement, (device_id, month))
            row = result.one()
            if row:
                return self.to_telemetry(row)

        return None

    @staticmethod
    def to_telemetry(row) -> TelemetryReading:
        return TelemetryReading(
            ts=row.ts,
            temperature=row.temperature,
            humidity=row.humidity,
        )


telemetry_repository = CassandraTelemetryRepository()


def connect_cassandra():
    telemetry_repository.connect()


def shutdown_cassandra():
    telemetry_repository.shutdown()


def get_telemetry_repository() -> CassandraTelemetryRepository:
    return telemetry_repository
