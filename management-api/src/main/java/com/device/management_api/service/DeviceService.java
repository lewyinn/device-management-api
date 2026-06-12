package com.device.management_api.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.device.management_api.entity.Device;
import com.device.management_api.dto.device.CreateDeviceRequest;
import com.device.management_api.dto.device.PatchDeviceRequest;
import com.device.management_api.dto.device.UpdateDeviceRequest;
import com.device.management_api.exception.ApiException;
import com.device.management_api.repository.DeviceRepository;

import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    private static final int MAX_LIMIT = 50;

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public Device create(CreateDeviceRequest request) {
        String deviceStatus = isBlank(request.status()) ? "active" : request.status();
        validateStatus(deviceStatus);

        Device device = new Device(
                UUID.randomUUID(),
                request.name(),
                request.type(),
                deviceStatus
        );

        return deviceRepository.save(device);
    }

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

    public Device findById(String deviceId) {
        return getDevice(deviceId);
    }

    @Transactional
    public Device update(String deviceId, UpdateDeviceRequest request) {
        UUID id = parseDeviceId(deviceId);
        validateStatus(request.status());

        getDevice(deviceId);
        return deviceRepository.save(new Device(id, request.name(), request.type(), request.status()));
    }

    @Transactional
    public Device patch(String deviceId, PatchDeviceRequest request) {
        Device device = getDevice(deviceId);
        if (request == null || request.isEmpty()) {
            validationError("At least one field must be provided");
        }

        String name = device.name();
        String type = device.type();
        String status = device.status();

        if (request != null && request.name() != null) {
            name = request.name();
            if (isBlank(name)) validationError("Attribute 'name' is required");
        }

        if (request != null && request.type() != null) {
            type = request.type();
            if (isBlank(type)) validationError("Attribute 'type' is required");
        }

        if (request != null && request.status() != null) {
            status = request.status();
            validateStatus(status);
        }

        return deviceRepository.save(new Device(device.getId(), name, type, status));
    }

    @Transactional
    public void delete(String deviceId) {
        Device device = getDevice(deviceId);
        deviceRepository.deleteById(device.getId());
    }

    private Device getDevice(String deviceId) {
        UUID id = parseDeviceId(deviceId);
        return deviceRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Device ID " + deviceId + " not found",
                        null
                ));
    }

    private UUID parseDeviceId(String deviceId) {
        try {
            return UUID.fromString(deviceId);
        } catch (RuntimeException error) {
            validationError("Device ID must be a valid UUID");
            return null;
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
