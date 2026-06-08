package com.device.management_api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.device.management_api.entity.DeviceTelemetry;

public interface DeviceTelemetryRepository extends JpaRepository<DeviceTelemetry, Integer> {
    List<DeviceTelemetry> findAllByDevice_IdOrderByTsDesc(UUID deviceId);

    Optional<DeviceTelemetry> findFirstByDevice_IdOrderByTsDesc(UUID deviceId);

    boolean existsByDevice_IdAndTs(UUID deviceId, long ts);
}
