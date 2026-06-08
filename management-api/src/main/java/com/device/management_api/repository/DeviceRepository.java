package com.device.management_api.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.device.management_api.entity.Device;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
}
