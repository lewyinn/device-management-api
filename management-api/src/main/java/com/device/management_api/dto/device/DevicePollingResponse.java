package com.device.management_api.dto.device;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DevicePollingResponse(
        String message,
        String pattern,
        String details,
        String snapshot,
        MetaResponse meta,
        List<DeviceResponse> data
) {
}
