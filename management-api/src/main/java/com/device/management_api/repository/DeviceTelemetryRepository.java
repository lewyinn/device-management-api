package com.device.management_api.repository;

import com.device.management_api.entity.DeviceTelemetry;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceTelemetryRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DeviceTelemetry> telemetryMapper = (row, rowNumber) -> new DeviceTelemetry(
            row.getInt("id"),
            row.getString("device_id"),
            row.getLong("ts"),
            row.getDouble("temperature"),
            row.getDouble("humidity")
    );

    public DeviceTelemetryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeviceTelemetry save(DeviceTelemetry telemetry) {
        jdbcTemplate.update(
                "INSERT INTO device_telemetries (device_id, ts, temperature, humidity) VALUES (?, ?, ?, ?)",
                telemetry.deviceId(),
                telemetry.ts(),
                telemetry.temperature(),
                telemetry.humidity()
        );
        Integer id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Integer.class);
        return new DeviceTelemetry(
                id,
                telemetry.deviceId(),
                telemetry.ts(),
                telemetry.temperature(),
                telemetry.humidity()
        );
    }

    public List<DeviceTelemetry> findAllByDeviceId(String deviceId) {
        return jdbcTemplate.query(
                "SELECT id, device_id, ts, temperature, humidity FROM device_telemetries WHERE device_id = ? ORDER BY ts DESC",
                telemetryMapper,
                deviceId
        );
    }

    public Optional<DeviceTelemetry> findLatestByDeviceId(String deviceId) {
        List<DeviceTelemetry> telemetries = jdbcTemplate.query(
                "SELECT id, device_id, ts, temperature, humidity FROM device_telemetries WHERE device_id = ? ORDER BY ts DESC LIMIT 1",
                telemetryMapper,
                deviceId
        );
        return telemetries.stream().findFirst();
    }

    public Optional<DeviceTelemetry> findById(int id) {
        List<DeviceTelemetry> telemetries = jdbcTemplate.query(
                "SELECT id, device_id, ts, temperature, humidity FROM device_telemetries WHERE id = ?",
                telemetryMapper,
                id
        );
        return telemetries.stream().findFirst();
    }

    public boolean existsByDeviceIdAndTs(String deviceId, long ts) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_telemetries WHERE device_id = ? AND ts = ?",
                Integer.class,
                deviceId,
                ts
        );
        return total != null && total > 0;
    }

    public void deleteById(int id) {
        jdbcTemplate.update("DELETE FROM device_telemetries WHERE id = ?", id);
    }
}
