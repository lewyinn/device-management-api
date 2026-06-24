package com.device.management_api.model.cassandra;

public record TelemetryReading(
        Long ts,
        Double temperature,
        Double humidity
) {
}
