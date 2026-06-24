package com.device.management_api.service;

import java.util.List;

import com.device.management_api.model.cassandra.TelemetryReading;
import com.device.management_api.model.postgres.Device;

public interface DeviceTelemetryService {
    TelemetryResult create(String deviceId, double temperature, double humidity);

    TelemetryListResult findAllByDeviceId(
            String deviceId,
            String startMonth,
            String endMonth
    );

    TelemetryResult findLatestByDeviceId(String deviceId);

    List<String> monthsBetween(String startMonth, String endMonth);

    List<String> latestMonths();

    String recordMonth(long ts);

    record TelemetryResult(
            Device device,
            TelemetryReading telemetry
    ) {
    }

    record TelemetryListResult(
            Device device,
            List<TelemetryReading> telemetries
    ) {
    }
}
