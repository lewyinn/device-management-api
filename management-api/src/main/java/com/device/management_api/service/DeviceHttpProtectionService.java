package com.device.management_api.service;

import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

public interface DeviceHttpProtectionService {
    ResponseEntity<?> registerThrottling(HttpServletRequest request);

    ResponseEntity<?> readRateLimit(HttpServletRequest request);
}
