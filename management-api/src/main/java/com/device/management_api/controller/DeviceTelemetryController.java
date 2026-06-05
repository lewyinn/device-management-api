package com.device.management_api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.device.management_api.entity.Device;
import com.device.management_api.entity.DeviceTelemetry;
import com.device.management_api.exception.GlobalExceptionHandler.ErrorResponse;
import com.device.management_api.service.DeviceTelemetryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
    public ResponseEntity<TelemetryResponse> create(
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
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request
    ) {
        DeviceTelemetryService.TelemetryResult result = telemetryService.create(deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TelemetryResponse("Telemetry successfully recorded", toTelemetryResponse(result))
        );
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
    public TelemetryListResponse findAllByDeviceId(@PathVariable("device_id") String deviceId) {
        DeviceTelemetryService.TelemetryListResult result = telemetryService.findAllByDeviceId(deviceId);
        List<TelemetryDataResponse> data = result.telemetries()
                .stream()
                .map(this::toTelemetryData)
                .toList();

        return new TelemetryListResponse(
                "Success retrieving telemetry",
                result.device().id(),
                result.device().name(),
                result.device().type(),
                data
        );
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
    public TelemetryResponse findLatestByDeviceId(@PathVariable("device_id") String deviceId) {
        DeviceTelemetryService.TelemetryResult result = telemetryService.findLatestByDeviceId(deviceId);
        return new TelemetryResponse("Latest telemetry found", toTelemetryResponse(result));
    }

    @DeleteMapping("/telemetry/{telemetry_id}")
    @Operation(summary = "Delete Telemetry")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content"),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"Telemetry ID must be a valid number\"}"))),
        @ApiResponse(responseCode = "404", description = "Telemetry Not Found",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Telemetry ID 1 not found\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<Void> deleteById(@PathVariable("telemetry_id") String telemetryId) {
        telemetryService.deleteById(telemetryId);
        return ResponseEntity.noContent().build();
    }

    private TelemetryDeviceResponse toTelemetryResponse(DeviceTelemetryService.TelemetryResult result) {
        Device device = result.device();
        DeviceTelemetry telemetry = result.telemetry();
        return new TelemetryDeviceResponse(
                device.id(),
                device.name(),
                device.type(),
                toTelemetryData(telemetry)
        );
    }

    private TelemetryDataResponse toTelemetryData(DeviceTelemetry telemetry) {
        return new TelemetryDataResponse(
                telemetry.ts(),
                telemetry.temperature(),
                telemetry.humidity()
        );
    }

    public record TelemetryResponse(
            String message,
            TelemetryDeviceResponse data
            ) {

    }

    public record TelemetryDeviceResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("device_id") String deviceId,
            String deviceName,
            String deviceType,
            TelemetryDataResponse data
            ) {

    }

    public record TelemetryDataResponse(
            @Schema(example = "1717488000000")
            Long ts,
            @Schema(example = "28.5")
            Double temperature,
            @Schema(example = "75.2")
            Double humidity
            ) {

    }

    public record TelemetryListResponse(
            String message,
            @com.fasterxml.jackson.annotation.JsonProperty("device_id") String deviceId,
            String deviceName,
            String deviceType,
            List<TelemetryDataResponse> data
            ) {

    }
}
