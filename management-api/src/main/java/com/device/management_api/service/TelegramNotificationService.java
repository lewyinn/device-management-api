package com.device.management_api.service;

import com.device.management_api.model.postgres.Device;

public interface TelegramNotificationService {
    void sendDeviceRegisteredNotification(Device device);
}
