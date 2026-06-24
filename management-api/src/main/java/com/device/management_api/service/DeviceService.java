package com.device.management_api.service;

import java.util.List;

import com.device.management_api.model.postgres.Device;

public interface DeviceService {
    Device create(String name, String type, String status);

    DevicePage findAll(String pageValue, String limitValue);

    Device findById(String deviceId);

    Device update(String deviceId, String name, String type, String status);

    Device patch(String deviceId, String name, String type, String status);

    void delete(String deviceId);

    record DevicePage(
            int page,
            int limit,
            long totalData,
            int totalPages,
            List<Device> devices
    ) {
    }
}
