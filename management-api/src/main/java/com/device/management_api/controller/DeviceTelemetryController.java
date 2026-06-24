package com.device.management_api.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.device.management_api.model.cassandra.TelemetryReading;
import com.device.management_api.model.postgres.Device;
import com.device.management_api.service.DeviceTelemetryService;
import com.device.management_api.websocket.TelemetryWebSocketHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@Tag(name = "Telemetry")
@RequestMapping("/api/v1")
public class DeviceTelemetryController {
    private final DeviceTelemetryService telemetryService;
    private final TelemetryWebSocketHandler telemetryWebSocketHandler;

    public DeviceTelemetryController(
            DeviceTelemetryService telemetryService,
            TelemetryWebSocketHandler telemetryWebSocketHandler
    ) {
        this.telemetryService = telemetryService;
        this.telemetryWebSocketHandler = telemetryWebSocketHandler;
    }

    @PostMapping("/devices/{device_id}/telemetry")
    @Operation(
            summary = "Create Device Telemetry",
            description = "Menyimpan telemetry device ke Cassandra."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Created",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Telemetry successfully recorded",
                                  "data": {
                                    "device_id": "550e8400-e29b-41d4-a716-446655440000",
                                    "deviceName": "Sensor-Suhu",
                                    "deviceType": "PM2120",
                                    "data": {
                                      "ts": 1781754587451,
                                      "temperature": 28.5,
                                      "humidity": 75.2
                                    }
                                  }
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "Bad Request",
                content = @Content(
                        mediaType = "application/json",
                        examples = {
                                @ExampleObject(
                                        name = "invalidDeviceId",
                                        summary = "Invalid device ID",
                                        value = """
                                                {
                                                  "error": "Validation failed",
                                                  "details": "Device ID must be a valid UUID"
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "invalidTelemetryPayload",
                                        summary = "Invalid telemetry payload",
                                        value = """
                                                {
                                                  "error": "Validation failed",
                                                  "details": "Attributes 'values.temperature' and 'values.humidity' must be numbers"
                                                }
                                                """
                                )
                        }
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Device Not Found",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Internal Server Error",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Internal server error"
                                }
                                """)
                )
        )
    })
    public ResponseEntity<?> create(
            @Parameter(
                    in = ParameterIn.PATH,
                    name = "device_id",
                    description = "ID device.",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable("device_id") String deviceId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "values": {
                                        "temperature": 28.5,
                                        "humidity": 75.2
                                      }
                                    }
                                    """)
                    )
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateTelemetryRequest request,
            BindingResult validationResult
    ) {
        if (validationResult.hasErrors()) {
            return validationError(validationResult);
        }

        try {
            DeviceTelemetryService.TelemetryResult result = telemetryService.create(
                    deviceId,
                    request.values().temperature(),
                    request.values().humidity()
            );
            telemetryWebSocketHandler.broadcastTelemetry(result.device(), result.telemetry());

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new TelemetryResponse(
                            "Telemetry successfully recorded",
                            telemetryDeviceResponse(result)
                    )
            );
        } catch (IllegalArgumentException error) {
            return validationError(error.getMessage());
        } catch (NoSuchElementException error) {
            return notFound(error.getMessage());
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @GetMapping("/devices/{device_id}/telemetry")
    @Operation(
            summary = "Get Device Telemetry",
            description = "Mengambil telemetry Cassandra berdasarkan rentang bulan."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Success retrieving telemetry",
                                  "device_id": "550e8400-e29b-41d4-a716-446655440000",
                                  "deviceName": "Sensor-Suhu",
                                  "deviceType": "PM2120",
                                  "data": [
                                    {
                                      "ts": 1781490918553,
                                      "temperature": 28.5,
                                      "humidity": 75.2
                                    },
                                    {
                                      "ts": 1781490818553,
                                      "temperature": 27.3,
                                      "humidity": 80.1
                                    }
                                  ]
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "Bad Request",
                content = @Content(
                        mediaType = "application/json",
                        examples = {
                                @ExampleObject(
                                        name = "invalidDeviceId",
                                        summary = "Invalid device ID",
                                        value = """
                                                {
                                                  "error": "Validation failed",
                                                  "details": "Device ID must be a valid UUID"
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "invalidMonth",
                                        summary = "Invalid month format",
                                        value = """
                                                {
                                                  "error": "Validation failed",
                                                  "details": "Query parameter 'start_month' and 'end_month' must use YYYY-MM format"
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "invalidMonthRange",
                                        summary = "Invalid month range",
                                        value = """
                                                {
                                                  "error": "Validation failed",
                                                  "details": "start_month cannot be after end_month"
                                                }
                                                """
                                )
                        }
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Device Not Found",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Internal Server Error",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Internal server error"
                                }
                                """)
                )
        )
    })
    public ResponseEntity<?> findAllByDeviceId(
            @Parameter(
                    in = ParameterIn.PATH,
                    name = "device_id",
                    description = "ID device.",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable("device_id") String deviceId,
            @Parameter(
                    in = ParameterIn.QUERY,
                    name = "start_month",
                    description = "Bulan awal data telemetry dengan format YYYY-MM. Default 2026-01.",
                    example = "2026-01"
            )
            @RequestParam(name = "start_month", defaultValue = "2026-01") String startMonth,
            @Parameter(
                    in = ParameterIn.QUERY,
                    name = "end_month",
                    description = "Bulan akhir data telemetry dengan format YYYY-MM. Default 2026-12.",
                    example = "2026-12"
            )
            @RequestParam(name = "end_month", defaultValue = "2026-12") String endMonth
    ) {
        try {
            DeviceTelemetryService.TelemetryListResult result =
                    telemetryService.findAllByDeviceId(deviceId, startMonth, endMonth);
            List<TelemetryDataResponse> data = result.telemetries().stream()
                    .map(this::telemetryDataResponse)
                    .toList();

            return ResponseEntity.ok(new TelemetryListResponse(
                    "Success retrieving telemetry",
                    result.device().id(),
                    result.device().name(),
                    result.device().type(),
                    data
            ));
        } catch (IllegalArgumentException error) {
            return validationError(error.getMessage());
        } catch (NoSuchElementException error) {
            return notFound(error.getMessage());
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @GetMapping("/devices/{device_id}/telemetry/latest")
    @Operation(
            summary = "Get Latest Device Telemetry",
            description = "Mengambil telemetry terbaru milik satu device berdasarkan nilai ts terbesar."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Latest telemetry found",
                                  "data": {
                                    "device_id": "550e8400-e29b-41d4-a716-446655440000",
                                    "deviceName": "Sensor-Suhu",
                                    "deviceType": "PM2120",
                                    "data": {
                                      "ts": 1781490918553,
                                      "temperature": 28.5,
                                      "humidity": 75.2
                                    }
                                  }
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "Bad Request",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Validation failed",
                                  "details": "Device ID must be a valid UUID"
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Device or Telemetry Not Found",
                content = @Content(
                        mediaType = "application/json",
                        examples = {
                                @ExampleObject(
                                        name = "deviceNotFound",
                                        summary = "Device not found",
                                        value = """
                                                {
                                                  "error": "Device ID 550e8400-e29b-41d4-a716-446655440000 not found"
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "telemetryNotFound",
                                        summary = "Telemetry not found",
                                        value = """
                                                {
                                                  "error": "Telemetry for device ID 550e8400-e29b-41d4-a716-446655440000 not found"
                                                }
                                                """
                                )
                        }
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Internal Server Error",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Internal server error"
                                }
                                """)
                )
        )
    })
    public ResponseEntity<?> findLatestByDeviceId(
            @Parameter(
                    in = ParameterIn.PATH,
                    name = "device_id",
                    description = "ID device.",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable("device_id") String deviceId
    ) {
        try {
            DeviceTelemetryService.TelemetryResult result =
                    telemetryService.findLatestByDeviceId(deviceId);
            return ResponseEntity.ok(
                    new TelemetryResponse(
                            "Latest telemetry found",
                            telemetryDeviceResponse(result)
                    )
            );
        } catch (IllegalArgumentException error) {
            return validationError(error.getMessage());
        } catch (NoSuchElementException error) {
            return notFound(error.getMessage());
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    private TelemetryDeviceResponse telemetryDeviceResponse(
            DeviceTelemetryService.TelemetryResult result
    ) {
        Device device = result.device();
        return new TelemetryDeviceResponse(
                device.id(),
                device.name(),
                device.type(),
                telemetryDataResponse(result.telemetry())
        );
    }

    private TelemetryDataResponse telemetryDataResponse(TelemetryReading telemetry) {
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
        return validationError(details);
    }

    private ResponseEntity<ErrorResponse> validationError(String details) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("Validation failed", details));
    }

    private ResponseEntity<ErrorResponse> notFound(String message) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(message, null));
    }

    private ResponseEntity<ErrorResponse> internalError() {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal server error", null));
    }

    public record CreateTelemetryRequest(
            @Valid
            @NotNull(message = "Attributes 'values.temperature' and 'values.humidity' must be numbers")
            TelemetryValuesRequest values
    ) {
    }

    public record TelemetryValuesRequest(
            @NotNull(message = "Attributes 'values.temperature' and 'values.humidity' must be numbers")
            Double temperature,
            @NotNull(message = "Attributes 'values.temperature' and 'values.humidity' must be numbers")
            Double humidity
    ) {
    }

    public record TelemetryDataResponse(
            Long ts,
            Double temperature,
            Double humidity
    ) {
    }

    public record TelemetryDeviceResponse(
            @JsonProperty("device_id") String deviceId,
            String deviceName,
            String deviceType,
            TelemetryDataResponse data
    ) {
    }

    public record TelemetryResponse(
            String message,
            TelemetryDeviceResponse data
    ) {
    }

    public record TelemetryListResponse(
            String message,
            @JsonProperty("device_id") String deviceId,
            String deviceName,
            String deviceType,
            List<TelemetryDataResponse> data
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            String error,
            Object details
    ) {
    }
}
