package com.device.management_api.repository.postgres;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.device.management_api.model.postgres.Device;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
}
