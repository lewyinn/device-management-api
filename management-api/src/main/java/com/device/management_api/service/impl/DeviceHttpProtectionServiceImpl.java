package com.device.management_api.service.impl;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.device.management_api.service.DeviceHttpProtectionService;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class DeviceHttpProtectionServiceImpl implements DeviceHttpProtectionService {
    private final Map<String, RateLimitState> rateLimitStore = new ConcurrentHashMap<>();
    private final Map<String, Long> throttleStore = new ConcurrentHashMap<>();

    @Override
    public ResponseEntity<?> registerThrottling(HttpServletRequest request) {
        return checkThrottling(
                "Device registration endpoint",
                "device_registration:" + clientIp(request),
                1_000
        );
    }

    @Override
    public ResponseEntity<?> readRateLimit(HttpServletRequest request) {
        return checkRateLimit(
                "Device read endpoint",
                "device_read:" + clientIp(request),
                100,
                60_000
        );
    }

    private ResponseEntity<?> checkRateLimit(
            String endpointName,
            String key,
            int maxRequests,
            long windowMs
    ) {
        long now = Instant.now().toEpochMilli();
        RateLimitState state = rateLimitStore.compute(key, (storeKey, current) -> {
            if (current == null || now >= current.resetAt()) {
                return new RateLimitState(1, now + windowMs);
            }
            return new RateLimitState(current.count() + 1, current.resetAt());
        });

        if (state.count() <= maxRequests) {
            return null;
        }

        int retryAfterSeconds = Math.max(
                1,
                (int) Math.ceil((state.resetAt() - now) / 1000.0)
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(retryAfterSeconds));
        headers.add("X-RateLimit-Limit", String.valueOf(maxRequests));
        headers.add("X-RateLimit-Remaining", "0");
        headers.add("X-RateLimit-Reset", String.valueOf(state.resetAt() / 1000));

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(new ProtectionErrorResponse(
                        "Rate limit exceeded",
                        "rate_limiting",
                        endpointName + " only allows " + maxRequests
                                + " requests every " + (windowMs / 1000) + " seconds",
                        retryAfterSeconds,
                        null
                ));
    }

    private ResponseEntity<?> checkThrottling(
            String endpointName,
            String key,
            long throttleMs
    ) {
        long now = Instant.now().toEpochMilli();
        Long lastRequestAt = throttleStore.get(key);

        if (lastRequestAt != null && now - lastRequestAt < throttleMs) {
            long retryAfterMs = throttleMs - (now - lastRequestAt);
            HttpHeaders headers = new HttpHeaders();
            headers.add(
                    "Retry-After",
                    String.valueOf(Math.max(1, (int) Math.ceil(retryAfterMs / 1000.0)))
            );

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(new ProtectionErrorResponse(
                            "Request throttled",
                            "throttling",
                            endpointName + " only accepts one request every "
                                    + throttleMs + " milliseconds",
                            null,
                            retryAfterMs
                    ));
        }

        throttleStore.put(key, now);
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record RateLimitState(int count, long resetAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ProtectionErrorResponse(
            String error,
            String type,
            String details,
            Integer retry_after_seconds,
            Long retry_after_ms
    ) {
    }
}
