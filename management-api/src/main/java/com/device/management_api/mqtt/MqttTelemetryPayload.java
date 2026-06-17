package com.device.management_api.mqtt;

public record MqttTelemetryPayload(
        Long ts,
        Double temperature,
        Double humidity
) {
}
