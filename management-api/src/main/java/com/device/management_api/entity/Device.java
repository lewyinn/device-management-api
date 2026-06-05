package com.device.management_api.entity;

public record Device(
        String id,
        String name,
        String type,
        String status
) {
}
