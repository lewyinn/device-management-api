package com.device.management_api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.device.management_api.dto.telemetry.CreateTelemetryRequest;
import com.device.management_api.dto.telemetry.TelemetryDataResponse;
import com.device.management_api.dto.telemetry.TelemetryDeviceResponse;
import com.device.management_api.dto.telemetry.TelemetryListResponse;
import com.device.management_api.dto.telemetry.TelemetryReading;
import com.device.management_api.dto.telemetry.TelemetryResponse;
import com.device.management_api.entity.Device;
import com.device.management_api.exception.ApiException;
import com.device.management_api.service.DeviceTelemetryService;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Telemetry")
@RequestMapping("/api/v1")
public class DeviceTelemetryController {

    private final DeviceTelemetryService telemetryService;

    public DeviceTelemetryController(DeviceTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping("/devices/{device_id}/telemetry")
    @Operation(summary = "Create Device Telemetry")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TelemetryResponse.class),
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Telemetry successfully recorded",
                                  "data": {
                                    "device_id": "550e8400-e29b-41d4-a716-446655440000",
                                    "deviceName": "Sensor-Suhu",
                                    "deviceType": "PM2120",
                                    "data": {
                                      "ts": 1717488000000,
                                      "temperature": 28.5,
                                      "humidity": 75.2
                                    }
                                  }
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"Attributes 'values.temperature' and 'values.humidity' must be numbers\"}"))),
        @ApiResponse(responseCode = "404", description = "Device Not Found",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Device ID 550e8400-e29b-41d4-a716-446655440000 not found\"}"))),
        @ApiResponse(responseCode = "409", description = "Duplicate Telemetry Timestamp",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Duplicate telemetry timestamp\",\"details\":\"Telemetry for device ID 550e8400-e29b-41d4-a716-446655440000 at ts 1717488000000 already exists\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<?> create(
            @PathVariable("device_id") String deviceId,
            @RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "values": {
                                        "temperature": 28.5,
                                        "humidity": 75.2
                                      }
                                    }
                                    """))
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateTelemetryRequest request,
            BindingResult validationResult
    ) {
        if (validationResult.hasErrors()) {
            return validationError(validationResult);
        }

        try {
            DeviceTelemetryService.TelemetryResult result = telemetryService.create(deviceId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new TelemetryResponse("Telemetry successfully recorded", toTelemetryResponse(result))
            );
        } catch (ApiException error) {
            return apiError(error);
        } catch (Exception error) {
            return internalError();
        }
    }

    @GetMapping("/devices/{device_id}/telemetry")
    @Operation(summary = "Get Device Telemetry")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = TelemetryListResponse.class),
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Success retrieving telemetry",
                                  "device_id": "550e8400-e29b-41d4-a716-446655440000",
                                  "deviceName": "Sensor-Suhu",
                                  "deviceType": "PM2120",
                                  "data": [
                                    {
                                      "ts": 1717488000000,
                                      "temperature": 28.5,
                                      "humidity": 75.2
                                    },
                                    {
                                      "ts": 1717488600000,
                                      "temperature": 28.7,
                                      "humidity": 74.8
                                    }
                                  ]
                                }
                                """)
                )),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"Device ID must be a valid UUID\"}"))),
        @ApiResponse(responseCode = "404", description = "Device Not Found",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Device ID 550e8400-e29b-41d4-a716-446655440000 not found\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<?> findAllByDeviceId(
            @PathVariable("device_id") String deviceId,
            @RequestParam(required = false, name = "start_month", defaultValue = "2026-01") String startMonth,
            @RequestParam(required = false, name = "end_month", defaultValue = "2026-12") String endMonth
    ) {
        try {
            DeviceTelemetryService.TelemetryListResult result = telemetryService.findAllByDeviceId(deviceId, startMonth, endMonth);
            List<TelemetryDataResponse> data = result.telemetries()
                    .stream()
                    .map(this::toTelemetryData)
                    .toList();

            return ResponseEntity.ok(new TelemetryListResponse(
                    "Success retrieving telemetry",
                    result.device().id(),
                    result.device().name(),
                    result.device().type(),
                    data
            ));
        } catch (ApiException error) {
            return apiError(error);
        } catch (Exception error) {
            return internalError();
        }
    }

    @GetMapping("/devices/{device_id}/telemetry/latest")
    @Operation(summary = "Get Latest Device Telemetry")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = TelemetryResponse.class),
                        examples = @ExampleObject(value = """
                                {
                                        "message": "Latest telemetry found",
                                        "device_id": "550e8400-e29b-41d4-a716-446655440000",
                                        "deviceName": "Sensor-Suhu",
                                        "deviceType": "PM2120",
                                        "data": {
                                                "ts": 1717488600000,
                                                "temperature": 28.7,
                                                "humidity": 74.8
                                  }
                                }
                                """)
                )),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"Device ID must be a valid UUID\"}"))),
        @ApiResponse(responseCode = "404", description = "Device or Telemetry Not Found",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Telemetry for device ID 550e8400-e29b-41d4-a716-446655440000 not found\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<?> findLatestByDeviceId(@PathVariable("device_id") String deviceId) {
        try {
            DeviceTelemetryService.TelemetryResult result = telemetryService.findLatestByDeviceId(deviceId);
            return ResponseEntity.ok(new TelemetryResponse("Latest telemetry found", toTelemetryResponse(result)));
        } catch (ApiException error) {
            return apiError(error);
        } catch (Exception error) {
            return internalError();
        }
    }

    private TelemetryDeviceResponse toTelemetryResponse(DeviceTelemetryService.TelemetryResult result) {
        Device device = result.device();
        TelemetryReading telemetry = result.telemetry();
        return new TelemetryDeviceResponse(
                device.id(),
                device.name(),
                device.type(),
                toTelemetryData(telemetry)
        );
    }

    private TelemetryDataResponse toTelemetryData(TelemetryReading telemetry) {
        return new TelemetryDataResponse(
                telemetry.ts(),
                telemetry.temperature(),
                telemetry.humidity()
        );
    }

    private ResponseEntity<ErrorResponse> validationError(BindingResult validationResult) {
        String details = validationResult.getFieldErrors().isEmpty()
                ? "Invalid request payload"
                : validationResult.getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(new ErrorResponse("Validation failed", details));
    }

    private ResponseEntity<ErrorResponse> apiError(ApiException error) {
        return ResponseEntity.status(error.getStatus()).body(new ErrorResponse(error.getError(), error.getDetails()));
    }

    private ResponseEntity<ErrorResponse> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal server error", null));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            String error,
            Object details
    ) {
    }
}
