package com.device.management_api.dto.telemetry;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelemetryListResponse(
        String message,
        @JsonProperty("device_id") String deviceId,
        String deviceName,
        String deviceType,
        List<TelemetryDataResponse> data
) {
}
