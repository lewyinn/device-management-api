package com.device.management_api.repository;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.device.management_api.dto.telemetry.TelemetryReading;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Repository
public class CassandraTelemetryRepository {
    private static final String TABLE_NAME = "device_telemetries";

    private final String contactPoints;
    private final String localDatacenter;
    private final String keyspace;

    private CqlSession adminSession;
    private CqlSession session;
    private PreparedStatement insertStatement;
    private PreparedStatement historyStatement;
    private PreparedStatement latestStatement;
    private PreparedStatement deletePartitionStatement;

    public CassandraTelemetryRepository(
            @Value("${cassandra.contact-points}") String contactPoints,
            @Value("${cassandra.local-datacenter}") String localDatacenter,
            @Value("${cassandra.keyspace}") String keyspace
    ) {
        this.contactPoints = contactPoints;
        this.localDatacenter = localDatacenter;
        this.keyspace = keyspace;
    }

    @PostConstruct
    public void connect() {
        validateIdentifier("cassandra.keyspace", keyspace);

        adminSession = buildSession(null);
        adminSession.execute("""
                CREATE KEYSPACE IF NOT EXISTS %s
                WITH replication = {
                    'class': 'SimpleStrategy',
                    'replication_factor': 1
                }
                AND durable_writes = true
                """.formatted(keyspace));

        session = buildSession(keyspace);
        session.execute("""
                CREATE TABLE IF NOT EXISTS device_telemetries (
                    device_id uuid,
                    record_month text,
                    ts bigint,
                    temperature double,
                    humidity double,
                    PRIMARY KEY ((device_id, record_month), ts)
                ) WITH CLUSTERING ORDER BY (ts DESC)
                """);
        ensureTimestampColumnIsBigint();
        prepareStatements();
    }

    @PreDestroy
    public void shutdown() {
        if (session != null) {
            session.close();
        }

        if (adminSession != null) {
            adminSession.close();
        }
    }

    public TelemetryReading insert(UUID deviceId, String recordMonth, long ts, double temperature, double humidity) {
        session.execute(insertStatement.bind(deviceId, recordMonth, ts, temperature, humidity));
        return new TelemetryReading(ts, temperature, humidity);
    }

    public List<TelemetryReading> findByMonths(UUID deviceId, List<String> months) {
        List<TelemetryReading> telemetries = new ArrayList<>();

        for (String month : months) {
            session.execute(historyStatement.bind(deviceId, month))
                    .forEach(row -> telemetries.add(toTelemetryReading(row)));
        }

        return telemetries.stream()
                .sorted(Comparator.comparing(TelemetryReading::ts).reversed())
                .toList();
    }

    public TelemetryReading findLatest(UUID deviceId, List<String> months) {
        for (String month : months) {
            Row row = session.execute(latestStatement.bind(deviceId, month)).one();

            if (row != null) {
                return toTelemetryReading(row);
            }
        }

        return null;
    }

    public void deletePartitions(UUID deviceId, List<String> months) {
        for (String month : months) {
            session.execute(deletePartitionStatement.bind(deviceId, month));
        }
    }

    private CqlSession buildSession(String keyspaceName) {
        DriverConfigLoader configLoader = DriverConfigLoader.programmaticBuilder()
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(10))
                .build();
        CqlSessionBuilder builder = CqlSession.builder()
                .withConfigLoader(configLoader)
                .withLocalDatacenter(localDatacenter);

        for (InetSocketAddress contactPoint : parseContactPoints()) {
            builder.addContactPoint(contactPoint);
        }

        if (keyspaceName != null) {
            builder.withKeyspace(keyspaceName);
        }

        return builder.build();
    }

    private List<InetSocketAddress> parseContactPoints() {
        return List.of(contactPoints.split(","))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::parseContactPoint)
                .toList();
    }

    private InetSocketAddress parseContactPoint(String value) {
        String[] parts = value.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9042;

        return new InetSocketAddress(host, port);
    }

    private void prepareStatements() {
        insertStatement = session.prepare("""
                INSERT INTO device_telemetries
                (device_id, record_month, ts, temperature, humidity)
                VALUES (?, ?, ?, ?, ?)
                """);
        historyStatement = session.prepare("""
                SELECT ts, temperature, humidity
                FROM device_telemetries
                WHERE device_id = ?
                AND record_month = ?
                """);
        latestStatement = session.prepare("""
                SELECT ts, temperature, humidity
                FROM device_telemetries
                WHERE device_id = ?
                AND record_month = ?
                LIMIT 1
                """);
        deletePartitionStatement = session.prepare("""
                DELETE FROM device_telemetries
                WHERE device_id = ?
                AND record_month = ?
                """);
    }

    private void ensureTimestampColumnIsBigint() {
        Row row = session.execute(
                """
                SELECT type
                FROM system_schema.columns
                WHERE keyspace_name = ?
                AND table_name = ?
                AND column_name = ?
                """,
                keyspace,
                TABLE_NAME,
                "ts"
        ).one();
        String type = row == null ? null : row.getString("type");

        if (!"bigint".equals(type)) {
            throw new IllegalStateException("%s.ts must be bigint, current type is %s".formatted(
                    TABLE_NAME,
                    type == null ? "missing" : type
            ));
        }
    }

    private void validateIdentifier(String name, String value) {
        if (value == null || !value.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " must start with a letter and contain only letters, numbers, and underscores");
        }
    }

    private TelemetryReading toTelemetryReading(Row row) {
        return new TelemetryReading(
                row.getLong("ts"),
                row.getDouble("temperature"),
                row.getDouble("humidity")
        );
    }
}
