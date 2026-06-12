package com.device.management_api.dto.device;

import java.util.List;

public record DeviceListResponse(
        String message,
        MetaResponse meta,
        List<DeviceResponse> data
) {
}
