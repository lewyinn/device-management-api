package com.device.management_api.dto.device;

import jakarta.validation.constraints.NotBlank;

public record CreateDeviceRequest(
        @NotBlank(message = "Attribute 'name' is required")
        String name,

        @NotBlank(message = "Attribute 'type' is required")
        String type,

        String status
) {
}
