package com.device.management_api.dto.device;

public record PatchDeviceRequest(
        String name,
        String type,
        String status
) {
    public boolean isEmpty() {
        return name == null && type == null && status == null;
    }
}
