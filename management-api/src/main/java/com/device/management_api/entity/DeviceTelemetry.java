package com.device.management_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "device_telemetries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "ts"})
)
public class DeviceTelemetry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(nullable = false)
    private Long ts;

    @Column(nullable = false)
    private Double temperature;

    @Column(nullable = false)
    private Double humidity;

    public DeviceTelemetry() {
    }

    public DeviceTelemetry(Integer id, Device device, Long ts, Double temperature, Double humidity) {
        this.id = id;
        this.device = device;
        this.ts = ts;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    public Integer id() {
        return id;
    }

    public String deviceId() {
        return device == null ? null : device.id();
    }

    public Long ts() {
        return ts;
    }

    public Double temperature() {
        return temperature;
    }

    public Double humidity() {
        return humidity;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public Long getTs() {
        return ts;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }
}
