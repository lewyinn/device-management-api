package com.device.management_api.config;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.Row;

@Configuration
public class DatabaseCassandraConfig {
    private static final String TABLE_NAME = "device_telemetries";

    @Bean(destroyMethod = "close")
    public CqlSession cassandraSession(
            @Value("${cassandra.contact-points}") String contactPoints,
            @Value("${cassandra.local-datacenter}") String localDatacenter,
            @Value("${cassandra.keyspace}") String keyspace
    ) {
        validateIdentifier("cassandra.keyspace", keyspace);
        List<InetSocketAddress> addresses = parseContactPoints(contactPoints);

        try (CqlSession adminSession = buildSession(addresses, localDatacenter, null)) {
            adminSession.execute("""
                    CREATE KEYSPACE IF NOT EXISTS %s
                    WITH replication = {
                        'class': 'SimpleStrategy',
                        'replication_factor': 1
                    }
                    AND durable_writes = true
                    """.formatted(keyspace));
        }

        CqlSession session = buildSession(addresses, localDatacenter, keyspace);
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
        validateTimestampColumn(session, keyspace);
        System.out.println("Cassandra connection established");
        return session;
    }

    private CqlSession buildSession(
            List<InetSocketAddress> contactPoints,
            String localDatacenter,
            String keyspace
    ) {
        DriverConfigLoader configLoader = DriverConfigLoader.programmaticBuilder()
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(10))
                .build();
        CqlSessionBuilder builder = CqlSession.builder()
                .withConfigLoader(configLoader)
                .withLocalDatacenter(localDatacenter);

        contactPoints.forEach(builder::addContactPoint);
        if (keyspace != null) {
            builder.withKeyspace(keyspace);
        }
        return builder.build();
    }

    private List<InetSocketAddress> parseContactPoints(String contactPoints) {
        return Arrays.stream(contactPoints.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::parseContactPoint)
                .toList();
    }

    private InetSocketAddress parseContactPoint(String value) {
        String[] parts = value.split(":");
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9042;
        return new InetSocketAddress(parts[0], port);
    }

    private void validateTimestampColumn(CqlSession session, String keyspace) {
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
            session.close();
            throw new IllegalStateException("%s.ts must be bigint, current type is %s".formatted(
                    TABLE_NAME,
                    type == null ? "missing" : type
            ));
        }
    }

    private void validateIdentifier(String name, String value) {
        if (value == null || !value.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException(
                    name + " must start with a letter and contain only letters, numbers, and underscores"
            );
        }
    }
}
