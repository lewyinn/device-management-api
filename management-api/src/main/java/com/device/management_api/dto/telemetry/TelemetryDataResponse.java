package com.device.management_api.dto.telemetry;

import io.swagger.v3.oas.annotations.media.Schema;

public record TelemetryDataResponse(
        @Schema(example = "1781136000000")
        Long ts,

        @Schema(example = "28.5")
        Double temperature,

        @Schema(example = "75.2")
        Double humidity
) {
}
