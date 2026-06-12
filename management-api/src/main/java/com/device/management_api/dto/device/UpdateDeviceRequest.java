package com.device.management_api.dto.device;

import jakarta.validation.constraints.NotBlank;

public record UpdateDeviceRequest(
        @NotBlank(message = "All attributes (name, type, status) are required for PUT method")
        String name,

        @NotBlank(message = "All attributes (name, type, status) are required for PUT method")
        String type,

        @NotBlank(message = "All attributes (name, type, status) are required for PUT method")
        String status
) {
}
