package com.device.management_api.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.device.management_api.dto.telemetry.CreateTelemetryRequest;
import com.device.management_api.dto.telemetry.TelemetryReading;
import com.device.management_api.entity.Device;
import com.device.management_api.exception.ApiException;
import com.device.management_api.repository.CassandraTelemetryRepository;
import com.device.management_api.repository.DeviceRepository;

@Service
public class DeviceTelemetryService {
    private static final String DEFAULT_START_MONTH = "2026-01";
    private static final String DEFAULT_END_MONTH = "2026-12";

    private final DeviceRepository deviceRepository;
    private final CassandraTelemetryRepository telemetryRepository;

    public DeviceTelemetryService(
            DeviceRepository deviceRepository,
            CassandraTelemetryRepository telemetryRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
    }

    public TelemetryResult create(String deviceId, CreateTelemetryRequest request) {
        Device device = getDevice(deviceId);
        long ts = System.currentTimeMillis();
        TelemetryReading telemetry = telemetryRepository.insert(
                device.getId(),
                recordMonth(ts),
                ts,
                request.values().temperature(),
                request.values().humidity()
        );

        return new TelemetryResult(device, telemetry);
    }

    public TelemetryListResult findAllByDeviceId(String deviceId, String startMonthValue, String endMonthValue) {
        Device device = getDevice(deviceId);
        List<String> months = monthsBetween(
                defaultMonth(startMonthValue, DEFAULT_START_MONTH),
                defaultMonth(endMonthValue, DEFAULT_END_MONTH)
        );
        List<TelemetryReading> telemetries = telemetryRepository.findByMonths(device.getId(), months);

        return new TelemetryListResult(device, telemetries);
    }

    public TelemetryResult findLatestByDeviceId(String deviceId) {
        Device device = getDevice(deviceId);
        TelemetryReading telemetry = telemetryRepository.findLatest(device.getId(), latestMonths());

        if (telemetry == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Telemetry for device ID " + device.id() + " not found",
                    null
            );
        }

        return new TelemetryResult(device, telemetry);
    }

    public List<String> monthsBetween(String startMonthValue, String endMonthValue) {
        YearMonth startMonth = parseMonth(startMonthValue);
        YearMonth endMonth = parseMonth(endMonthValue);

        if (startMonth.isAfter(endMonth)) {
            validationError("start_month cannot be after end_month");
        }

        List<String> months = new ArrayList<>();
        YearMonth cursor = startMonth;

        while (!cursor.isAfter(endMonth)) {
            months.add(cursor.toString());
            cursor = cursor.plusMonths(1);
        }

        return months;
    }

    public List<String> latestMonths() {
        List<String> months = new ArrayList<>();
        YearMonth startMonth = parseMonth(DEFAULT_START_MONTH);
        YearMonth cursor = parseMonth(DEFAULT_END_MONTH);

        while (!cursor.isBefore(startMonth)) {
            months.add(cursor.toString());
            cursor = cursor.minusMonths(1);
        }

        return months;
    }

    public String recordMonth(long ts) {
        return YearMonth.from(Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC)).toString();
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

    private String defaultMonth(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException error) {
            validationError("Query parameter 'start_month' and 'end_month' must use YYYY-MM format");
            return null;
        }
    }

    private void validationError(String details) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    public record TelemetryResult(
            Device device,
            TelemetryReading telemetry
    ) {
    }

    public record TelemetryListResult(
            Device device,
            List<TelemetryReading> telemetries
    ) {
    }
}
