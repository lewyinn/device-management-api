package com.device.management_api.dto.telemetry;

public record TelemetryResponse(
        String message,
        TelemetryDeviceResponse data
) {
}
