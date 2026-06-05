package com.device.management_api.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.device.management_api.entity.Device;
import com.device.management_api.exception.ApiException;
import com.device.management_api.repository.DeviceRepository;

@Service
public class DeviceService {
    private static final int MAX_LIMIT = 50;

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Device create(Map<String, String> request) {
        String name = value(request, "name");
        String type = value(request, "type");
        String status = value(request, "status");

        if (isBlank(name)) validationError("Attribute 'name' is required");
        if (isBlank(type)) validationError("Attribute 'type' is required");

        String deviceStatus = isBlank(status) ? "active" : status;
        validateStatus(deviceStatus);

        Device device = new Device(
                UUID.randomUUID().toString(),
                name,
                type,
                deviceStatus
        );

        return deviceRepository.save(device);
    }

    public DevicePage findAll(String pageValue, String limitValue) {
        int page = parsePositiveNumber(pageValue);
        int limit = Math.min(parsePositiveNumber(limitValue), MAX_LIMIT);
        int offset = (page - 1) * limit;

        long totalData = deviceRepository.count();
        int totalPages = totalData == 0 ? 0 : (int) Math.ceil((double) totalData / limit);
        List<Device> devices = deviceRepository.findAll(limit, offset);

        return new DevicePage(page, limit, totalData, totalPages, devices);
    }

    public Device findById(String deviceId) {
        return getDevice(deviceId);
    }

    public Device update(String deviceId, Map<String, String> request) {
        validateDeviceId(deviceId);

        String name = value(request, "name");
        String type = value(request, "type");
        String status = value(request, "status");

        if (isBlank(name) || isBlank(type) || isBlank(status)) {
            validationError("All attributes (name, type, status) are required for PUT method");
        }
        validateStatus(status);

        getDevice(deviceId);
        return deviceRepository.update(new Device(deviceId, name, type, status));
    }

    public Device patch(String deviceId, Map<String, String> request) {
        Device device = getDevice(deviceId);
        if (request == null || request.isEmpty()) {
            validationError("At least one field must be provided");
        }

        String name = device.name();
        String type = device.type();
        String status = device.status();

        if (request != null && request.containsKey("name")) {
            name = request.get("name");
            if (isBlank(name)) validationError("Attribute 'name' is required");
        }

        if (request != null && request.containsKey("type")) {
            type = request.get("type");
            if (isBlank(type)) validationError("Attribute 'type' is required");
        }

        if (request != null && request.containsKey("status")) {
            status = request.get("status");
            validateStatus(status);
        }

        return deviceRepository.update(new Device(deviceId, name, type, status));
    }

    public void delete(String deviceId) {
        getDevice(deviceId);
        deviceRepository.delete(deviceId);
    }

    private Device getDevice(String deviceId) {
        validateDeviceId(deviceId);
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Device ID " + deviceId + " not found",
                        null
                ));
    }

    private void validateDeviceId(String deviceId) {
        try {
            UUID.fromString(deviceId);
        } catch (RuntimeException error) {
            validationError("Device ID must be a valid UUID");
        }
    }

    private int parsePositiveNumber(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number < 1) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException error) {
            validationError("Query parameter 'page' or 'limit' must be a valid number");
            return 1;
        }
    }

    private void validateStatus(String status) {
        if (!"active".equals(status) && !"inactive".equals(status)) {
            validationError("Status must be 'active' or 'inactive'");
        }
    }

    private String value(Map<String, String> request, String key) {
        if (request == null) return null;
        return request.get(key);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void validationError(String details) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    public record DevicePage(
            int page,
            int limit,
            long totalData,
            int totalPages,
            List<Device> devices
    ) {
    }
}
