import os

from dotenv import load_dotenv

from app.db.models_cassandra import CREATE_TELEMETRY_TABLE

load_dotenv()

CASSANDRA_CONTACT_POINTS = os.getenv(
    "CASSANDRA_CONTACT_POINTS",
    "127.0.0.1",
)
CASSANDRA_LOCAL_DATACENTER = os.getenv(
    "CASSANDRA_LOCAL_DATACENTER",
    "datacenter1",
)
CASSANDRA_KEYSPACE = os.getenv(
    "CASSANDRA_KEYSPACE",
    "device_management",
)


class CassandraDatabase:
    def __init__(self):
        self.cluster = None
        self.admin_session = None
        self.session = None

    def connect(self):
        if self.session is not None:
            return

        from cassandra.cluster import Cluster
        from cassandra.policies import DCAwareRoundRobinPolicy

        contact_points = [
            value.strip()
            for value in CASSANDRA_CONTACT_POINTS.split(",")
            if value.strip()
        ]
        self.cluster = Cluster(
            contact_points=contact_points,
            load_balancing_policy=DCAwareRoundRobinPolicy(
                local_dc=CASSANDRA_LOCAL_DATACENTER
            ),
        )
        self.admin_session = self.cluster.connect()
        self.admin_session.execute(
            f"""
            CREATE KEYSPACE IF NOT EXISTS {CASSANDRA_KEYSPACE}
            WITH replication = {{
                'class': 'NetworkTopologyStrategy',
                '{CASSANDRA_LOCAL_DATACENTER}': 1
            }}
            AND durable_writes = true
            """
        )
        self.session = self.cluster.connect(CASSANDRA_KEYSPACE)
        self.session.execute(CREATE_TELEMETRY_TABLE)
        self._validate_timestamp_column()
        print("Cassandra connection established")

    def shutdown(self):
        if self.cluster is not None:
            self.cluster.shutdown()

        self.cluster = None
        self.admin_session = None
        self.session = None
        print("Cassandra connection closed")

    def _validate_timestamp_column(self):
        result = self.session.execute(
            """
            SELECT type
            FROM system_schema.columns
            WHERE keyspace_name = %s
            AND table_name = %s
            AND column_name = %s
            """,
            (
                CASSANDRA_KEYSPACE,
                "device_telemetries",
                "ts",
            ),
        )
        row = result.one()
        column_type = row.type if row else None

        if column_type != "bigint":
            raise RuntimeError(
                f"device_telemetries.ts must be bigint, current type is "
                f"{column_type or 'missing'}"
            )


cassandra_database = CassandraDatabase()
