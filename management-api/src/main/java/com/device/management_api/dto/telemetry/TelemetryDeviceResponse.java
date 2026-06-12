package com.device.management_api.dto.telemetry;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelemetryDeviceResponse(
        @JsonProperty("device_id") String deviceId,
        String deviceName,
        String deviceType,
        TelemetryDataResponse data
) {
}
