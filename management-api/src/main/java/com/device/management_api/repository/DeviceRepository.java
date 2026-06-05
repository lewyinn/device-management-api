package com.device.management_api.repository;

import com.device.management_api.entity.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Device> deviceMapper = (row, rowNumber) -> new Device(
            row.getString("id"),
            row.getString("name"),
            row.getString("type"),
            row.getString("status")
    );

    public DeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Device save(Device device) {
        jdbcTemplate.update(
                "INSERT INTO devices (id, name, type, status) VALUES (?, ?, ?, ?)",
                device.id(),
                device.name(),
                device.type(),
                device.status()
        );
        return device;
    }

    public List<Device> findAll(int limit, int offset) {
        return jdbcTemplate.query(
                "SELECT id, name, type, status FROM devices ORDER BY name LIMIT ? OFFSET ?",
                deviceMapper,
                limit,
                offset
        );
    }

    public long count() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM devices", Long.class);
        return total == null ? 0 : total;
    }

    public Optional<Device> findById(String id) {
        List<Device> devices = jdbcTemplate.query(
                "SELECT id, name, type, status FROM devices WHERE id = ?",
                deviceMapper,
                id
        );
        return devices.stream().findFirst();
    }

    public Device update(Device device) {
        jdbcTemplate.update(
                "UPDATE devices SET name = ?, type = ?, status = ? WHERE id = ?",
                device.name(),
                device.type(),
                device.status(),
                device.id()
        );
        return device;
    }

    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM devices WHERE id = ?", id);
    }
}
