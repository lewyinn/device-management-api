package com.device.management_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.device.management_api.service.DeviceTelemetryService;
import com.device.management_api.service.impl.DeviceTelemetryServiceImpl;

class DeviceTelemetryServiceTests {
    private final DeviceTelemetryService telemetryService =
            new DeviceTelemetryServiceImpl(null, null);

    @Test
    void recordMonthUsesEpochMilliseconds() {
        assertEquals("2026-06", telemetryService.recordMonth(1781136000000L));
    }

    @Test
    void monthsBetweenGeneratesInclusiveRange() {
        assertEquals(
                List.of("2026-01", "2026-02", "2026-03"),
                telemetryService.monthsBetween("2026-01", "2026-03")
        );
    }

    @Test
    void monthsBetweenRejectsInvalidRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> telemetryService.monthsBetween("2026-01-01", "2026-12")
        );
    }

    @Test
    void monthsBetweenAllowsLongRange() {
        assertEquals(24, telemetryService.monthsBetween("2025-01", "2026-12").size());
    }

    @Test
    void latestMonthsUsesDefaultYearRange() {
        assertEquals(
                List.of(
                        "2026-12",
                        "2026-11",
                        "2026-10",
                        "2026-09",
                        "2026-08",
                        "2026-07",
                        "2026-06",
                        "2026-05",
                        "2026-04",
                        "2026-03",
                        "2026-02",
                        "2026-01"
                ),
                telemetryService.latestMonths()
        );
    }
}
