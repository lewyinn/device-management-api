package com.device.management_api.service.impl;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.device.management_api.model.postgres.Device;
import com.device.management_api.repository.postgres.DeviceRepository;
import com.device.management_api.service.DeviceService;

@Service
public class DeviceServiceImpl implements DeviceService {
    private static final int MAX_LIMIT = 50;

    private final DeviceRepository deviceRepository;

    public DeviceServiceImpl(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    @Transactional
    public Device create(String name, String type, String status) {
        String deviceStatus = isBlank(status) ? "active" : status;
        validateStatus(deviceStatus);
        return deviceRepository.save(new Device(
                UUID.randomUUID(),
                name,
                type,
                deviceStatus
        ));
    }

    @Override
    public DevicePage findAll(String pageValue, String limitValue) {
        int page = parsePositiveNumber(pageValue);
        int limit = Math.min(parsePositiveNumber(limitValue), MAX_LIMIT);
        long totalData = deviceRepository.count();
        int totalPages = totalData == 0 ? 0 : (int) Math.ceil((double) totalData / limit);
        List<Device> devices = deviceRepository
                .findAll(PageRequest.of(page - 1, limit, Sort.by("name")))
                .getContent();

        return new DevicePage(page, limit, totalData, totalPages, devices);
    }

    @Override
    public Device findById(String deviceId) {
        return getDevice(deviceId);
    }

    @Override
    @Transactional
    public Device update(String deviceId, String name, String type, String status) {
        UUID id = parseDeviceId(deviceId);
        validateStatus(status);
        getDevice(deviceId);
        return deviceRepository.save(new Device(id, name, type, status));
    }

    @Override
    @Transactional
    public Device patch(
            String deviceId,
            String requestedName,
            String requestedType,
            String requestedStatus
    ) {
        Device device = getDevice(deviceId);
        if (requestedName == null && requestedType == null && requestedStatus == null) {
            throw new IllegalArgumentException("At least one field must be provided");
        }

        String name = device.name();
        String type = device.type();
        String status = device.status();

        if (requestedName != null) {
            if (isBlank(requestedName)) {
                throw new IllegalArgumentException("Attribute 'name' is required");
            }
            name = requestedName;
        }

        if (requestedType != null) {
            if (isBlank(requestedType)) {
                throw new IllegalArgumentException("Attribute 'type' is required");
            }
            type = requestedType;
        }

        if (requestedStatus != null) {
            validateStatus(requestedStatus);
            status = requestedStatus;
        }

        return deviceRepository.save(new Device(device.getId(), name, type, status));
    }

    @Override
    @Transactional
    public void delete(String deviceId) {
        Device device = getDevice(deviceId);
        deviceRepository.deleteById(device.getId());
    }

    private Device getDevice(String deviceId) {
        UUID id = parseDeviceId(deviceId);
        return deviceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Device ID " + deviceId + " not found"
                ));
    }

    private UUID parseDeviceId(String deviceId) {
        try {
            return UUID.fromString(deviceId);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Device ID must be a valid UUID");
        }
    }

    private int parsePositiveNumber(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number < 1) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Query parameter 'page' or 'limit' must be a valid number"
            );
        }
    }

    private void validateStatus(String status) {
        if (!"active".equals(status) && !"inactive".equals(status)) {
            throw new IllegalArgumentException("Status must be 'active' or 'inactive'");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
