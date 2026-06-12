package com.device.management_api.dto.device;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetaResponse(
        int page,
        int limit,
        @JsonProperty("total_data") long totalData,
        @JsonProperty("total_pages") int totalPages
) {
}
