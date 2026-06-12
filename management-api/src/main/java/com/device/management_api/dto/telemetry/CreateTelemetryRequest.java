package com.device.management_api.dto.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateTelemetryRequest(
        @Valid
        @NotNull(message = "Attributes 'values.temperature' and 'values.humidity' must be numbers")
        TelemetryValuesRequest values
) {
}
