package com.device.management_api.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

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

    @Column(nullable = false)
    private String status;

    public Device() {
    }

    public Device(UUID id, String name, String type, String status) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
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
        return status;
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
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
