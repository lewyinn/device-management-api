package com.device.management_api.dto.device;

public record DeviceDataResponse(
        String message,
        DeviceResponse data
) {
}
