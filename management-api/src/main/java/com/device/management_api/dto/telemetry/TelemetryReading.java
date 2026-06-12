package com.device.management_api.dto.telemetry;

public record TelemetryReading(
        Long ts,
        Double temperature,
        Double humidity
) {
}
