package com.device.management_api.dto.device;

public record DeviceResponse(
        String id,
        String name,
        String type,
        String status
) {
}
