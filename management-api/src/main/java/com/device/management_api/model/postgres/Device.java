package com.device.management_api.model.postgres;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "devices",
        indexes = @Index(name = "idx_device_name", columnList = "name")
)
public class Device {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "enum_devices_status")
    private DeviceStatus status;

    public Device() {
    }

    public Device(UUID id, String name, String type, String status) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = DeviceStatus.from(status);
    }

    public String id() {
        return id == null ? null : id.toString();
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String status() {
        return status == null ? null : status.name();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status();
    }

    public void setStatus(String status) {
        this.status = DeviceStatus.from(status);
    }

    public enum DeviceStatus {
        active,
        inactive;

        public static DeviceStatus from(String value) {
            return DeviceStatus.valueOf(value);
        }
    }
}
