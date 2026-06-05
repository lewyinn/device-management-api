package com.device.management_api.entity;

public record DeviceTelemetry(
        Integer id,
        String deviceId,
        Long ts,
        Double temperature,
        Double humidity
) {
}
