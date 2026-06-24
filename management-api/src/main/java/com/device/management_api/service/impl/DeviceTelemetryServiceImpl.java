package com.device.management_api.service.impl;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.device.management_api.model.cassandra.TelemetryReading;
import com.device.management_api.model.postgres.Device;
import com.device.management_api.repository.cassandra.CassandraTelemetryRepository;
import com.device.management_api.repository.postgres.DeviceRepository;
import com.device.management_api.service.DeviceTelemetryService;

@Service
public class DeviceTelemetryServiceImpl implements DeviceTelemetryService {
    private static final String DEFAULT_START_MONTH = "2026-01";
    private static final String DEFAULT_END_MONTH = "2026-12";

    private final DeviceRepository deviceRepository;
    private final CassandraTelemetryRepository telemetryRepository;

    public DeviceTelemetryServiceImpl(
            DeviceRepository deviceRepository,
            CassandraTelemetryRepository telemetryRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @Override
    public TelemetryResult create(String deviceId, double temperature, double humidity) {
        Device device = getDevice(deviceId);
        long ts = System.currentTimeMillis();
        TelemetryReading telemetry = telemetryRepository.insert(
                device.getId(),
                recordMonth(ts),
                ts,
                temperature,
                humidity
        );
        return new TelemetryResult(device, telemetry);
    }

    @Override
    public TelemetryListResult findAllByDeviceId(
            String deviceId,
            String startMonthValue,
            String endMonthValue
    ) {
        Device device = getDevice(deviceId);
        List<String> months = monthsBetween(
                defaultMonth(startMonthValue, DEFAULT_START_MONTH),
                defaultMonth(endMonthValue, DEFAULT_END_MONTH)
        );
        return new TelemetryListResult(
                device,
                telemetryRepository.findByMonths(device.getId(), months)
        );
    }

    @Override
    public TelemetryResult findLatestByDeviceId(String deviceId) {
        Device device = getDevice(deviceId);
        TelemetryReading telemetry = telemetryRepository.findLatest(
                device.getId(),
                latestMonths()
        );

        if (telemetry == null) {
            throw new NoSuchElementException(
                    "Telemetry for device ID " + device.id() + " not found"
            );
        }
        return new TelemetryResult(device, telemetry);
    }

    @Override
    public List<String> monthsBetween(String startMonthValue, String endMonthValue) {
        YearMonth startMonth = parseMonth(startMonthValue);
        YearMonth endMonth = parseMonth(endMonthValue);

        if (startMonth.isAfter(endMonth)) {
            throw new IllegalArgumentException("start_month cannot be after end_month");
        }

        List<String> months = new ArrayList<>();
        YearMonth cursor = startMonth;
        while (!cursor.isAfter(endMonth)) {
            months.add(cursor.toString());
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    @Override
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

    @Override
    public String recordMonth(long ts) {
        return YearMonth.from(Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC)).toString();
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

    private String defaultMonth(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException(
                    "Query parameter 'start_month' and 'end_month' must use YYYY-MM format"
            );
        }
    }
}
