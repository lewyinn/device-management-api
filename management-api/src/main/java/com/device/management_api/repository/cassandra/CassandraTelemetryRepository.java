package com.device.management_api.repository.cassandra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.device.management_api.model.cassandra.TelemetryReading;

@Repository
public class CassandraTelemetryRepository {
    private final CqlSession session;
    private final PreparedStatement insertStatement;
    private final PreparedStatement historyStatement;
    private final PreparedStatement latestStatement;
    private final PreparedStatement deletePartitionStatement;

    public CassandraTelemetryRepository(CqlSession session) {
        this.session = session;
        this.insertStatement = session.prepare("""
                INSERT INTO device_telemetries
                (device_id, record_month, ts, temperature, humidity)
                VALUES (?, ?, ?, ?, ?)
                """);
        this.historyStatement = session.prepare("""
                SELECT ts, temperature, humidity
                FROM device_telemetries
                WHERE device_id = ?
                AND record_month = ?
                """);
        this.latestStatement = session.prepare("""
                SELECT ts, temperature, humidity
                FROM device_telemetries
                WHERE device_id = ?
                AND record_month = ?
                LIMIT 1
                """);
        this.deletePartitionStatement = session.prepare("""
                DELETE FROM device_telemetries
                WHERE device_id = ?
                AND record_month = ?
                """);
    }

    public TelemetryReading insert(
            UUID deviceId,
            String recordMonth,
            long ts,
            double temperature,
            double humidity
    ) {
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

    private TelemetryReading toTelemetryReading(Row row) {
        return new TelemetryReading(
                row.getLong("ts"),
                row.getDouble("temperature"),
                row.getDouble("humidity")
        );
    }
}
