package com.device.management_api.dto.telemetry;

import jakarta.validation.constraints.NotNull;

public record TelemetryValuesRequest(
        @NotNull(message = "Attributes 'values.temperature' and 'values.humidity' must be numbers")
        Double temperature,

        @NotNull(message = "Attributes 'values.temperature' and 'values.humidity' must be numbers")
        Double humidity
) {
}
