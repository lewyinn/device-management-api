package com.device.management_api.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.device.management_api.entity.Device;
import com.device.management_api.entity.DeviceTelemetry;
import com.device.management_api.exception.ApiException;
import com.device.management_api.repository.DeviceRepository;
import com.device.management_api.repository.DeviceTelemetryRepository;

@Service
public class DeviceTelemetryService {
    private final DeviceRepository deviceRepository;
    private final DeviceTelemetryRepository telemetryRepository;

    public DeviceTelemetryService(
            DeviceRepository deviceRepository,
            DeviceTelemetryRepository telemetryRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
    }

    public TelemetryResult create(String deviceId, Map<String, Object> request) {
        Device device = getDevice(deviceId);
        Values values = getValues(request);

        long ts = System.currentTimeMillis();
        if (telemetryRepository.existsByDeviceIdAndTs(device.id(), ts)) {
            duplicateTelemetry(device.id(), ts);
        }

        try {
            DeviceTelemetry telemetry = telemetryRepository.save(
                    new DeviceTelemetry(null, device.id(), ts, values.temperature(), values.humidity())
            );
            return new TelemetryResult(device, telemetry);
        } catch (DuplicateKeyException error) {
            duplicateTelemetry(device.id(), ts);
            return null;
        }
    }

    public TelemetryListResult findAllByDeviceId(String deviceId) {
        Device device = getDevice(deviceId);
        List<DeviceTelemetry> telemetries = telemetryRepository.findAllByDeviceId(device.id());
        return new TelemetryListResult(device, telemetries);
    }

    public TelemetryResult findLatestByDeviceId(String deviceId) {
        Device device = getDevice(deviceId);
        DeviceTelemetry telemetry = telemetryRepository.findLatestByDeviceId(device.id())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Telemetry for device ID " + device.id() + " not found",
                        null
                ));
        return new TelemetryResult(device, telemetry);
    }

    public void deleteById(String telemetryId) {
        int id = parseTelemetryId(telemetryId);
        DeviceTelemetry telemetry = telemetryRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Telemetry ID " + telemetryId + " not found",
                        null
                ));
        telemetryRepository.deleteById(telemetry.id());
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

    @SuppressWarnings("unchecked")
    private Values getValues(Map<String, Object> request) {
        Object rawValues = request == null ? null : request.get("values");
        if (!(rawValues instanceof Map<?, ?>)) {
            validationError("Attributes 'values.temperature' and 'values.humidity' must be numbers");
        }
        Map<String, Object> valuesMap = (Map<String, Object>) rawValues;

        Object temperature = valuesMap.get("temperature");
        Object humidity = valuesMap.get("humidity");

        if (!(temperature instanceof Number) || !(humidity instanceof Number)) {
            validationError("Attributes 'values.temperature' and 'values.humidity' must be numbers");
        }
        Number temperatureNumber = (Number) temperature;
        Number humidityNumber = (Number) humidity;

        return new Values(temperatureNumber.doubleValue(), humidityNumber.doubleValue());
    }

    private int parseTelemetryId(String telemetryId) {
        try {
            int id = Integer.parseInt(telemetryId);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException error) {
            validationError("Telemetry ID must be a valid number");
            return 1;
        }
    }

    private void duplicateTelemetry(String deviceId, long ts) {
        throw new ApiException(
                HttpStatus.CONFLICT,
                "Duplicate telemetry timestamp",
                "Telemetry for device ID " + deviceId + " at ts " + ts + " already exists"
        );
    }

    private void validationError(String details) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    private record Values(
            Double temperature,
            Double humidity
    ) {
    }

    public record TelemetryResult(
            Device device,
            DeviceTelemetry telemetry
    ) {
    }

    public record TelemetryListResult(
            Device device,
            List<DeviceTelemetry> telemetries
    ) {
    }
}
