package com.device.management_api.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.device.management_api.model.postgres.Device;
import com.device.management_api.service.DeviceHttpProtectionService;
import com.device.management_api.service.DeviceService;
import com.device.management_api.service.TelegramNotificationService;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@Tag(name = "Devices")
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService deviceService;
    private final DeviceHttpProtectionService deviceHttpProtectionService;
    private final TelegramNotificationService telegramNotificationService;
    private final TelemetryWebSocketHandler telemetryWebSocketHandler;

    public DeviceController(
            DeviceService deviceService,
            DeviceHttpProtectionService deviceHttpProtectionService,
            TelegramNotificationService telegramNotificationService,
            TelemetryWebSocketHandler telemetryWebSocketHandler
    ) {
        this.deviceService = deviceService;
        this.deviceHttpProtectionService = deviceHttpProtectionService;
        this.telegramNotificationService = telegramNotificationService;
        this.telemetryWebSocketHandler = telemetryWebSocketHandler;
    }

    @PostMapping
    @Operation(
            summary = "Create Device",
            description = "Mendaftarkan perangkat baru ke dalam sistem."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Created",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Device successfully registered",
                                  "data": {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "Sensor-Suhu",
                                    "type": "PM2120",
                                    "status": "active"
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
                                  "details": "Attribute 'name' is required"
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "429",
                description = "Too Many Requests",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Request throttled",
                                  "type": "throttling",
                                  "details": "Device registration endpoint only accepts one request every 1000 milliseconds",
                                  "retry_after_ms": 953
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
            HttpServletRequest httpRequest,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "name": "Sensor-Suhu",
                                      "type": "PM2120",
                                      "status": "active"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody CreateDeviceRequest request,
            BindingResult validationResult
    ) {
        if (validationResult.hasErrors()) {
            return validationError(validationResult);
        }

        ResponseEntity<?> protectionError = deviceHttpProtectionService.registerThrottling(httpRequest);
        if (protectionError != null) {
            return protectionError;
        }

        try {
            Device device = deviceService.create(request.name(), request.type(), request.status());
            telemetryWebSocketHandler.broadcastDeviceRegistered(device);
            telegramNotificationService.sendDeviceRegisteredNotification(device);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new DeviceDataResponse("Device successfully registered", deviceResponse(device))
            );
        } catch (IllegalArgumentException error) {
            return validationError(error.getMessage());
        } catch (NoSuchElementException error) {
            return notFound(error.getMessage());
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @GetMapping
    @Operation(
            summary = "Read All Devices",
            description = "Mengambil daftar seluruh perangkat dengan pagination."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Success retrieving devices",
                                  "meta": {
                                    "page": 1,
                                    "limit": 10,
                                    "total_data": 50,
                                    "total_pages": 5
                                  },
                                  "data": [
                                    {
                                      "id": "550e8400-e29b-41d4-a716-446655440000",
                                      "name": "Sensor-Suhu",
                                      "type": "PM2120",
                                      "status": "active"
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
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Validation failed",
                                  "details": "Query parameter 'page' or 'limit' must be a valid number"
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "429",
                description = "Too Many Requests",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "error": "Rate limit exceeded",
                                  "type": "rate_limiting",
                                  "details": "Device read endpoint only allows 100 requests every 60 seconds",
                                  "retry_after_seconds": 59
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
    public ResponseEntity<?> findAll(
            HttpServletRequest httpRequest,
            @Parameter(
                    in = ParameterIn.QUERY,
                    name = "page",
                    description = "Nomor halaman.",
                    example = "1"
            )
            @RequestParam(defaultValue = "1") String page,
            @Parameter(
                    in = ParameterIn.QUERY,
                    name = "limit",
                    description = "Jumlah data per halaman, maksimal 50.",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") String limit
    ) {
        ResponseEntity<?> protectionError = deviceHttpProtectionService.readRateLimit(httpRequest);
        if (protectionError != null) {
            return protectionError;
        }

        try {
            DeviceService.DevicePage result = deviceService.findAll(page, limit);
            List<DeviceResponse> devices = result.devices().stream()
                    .map(this::deviceResponse)
                    .toList();

            return ResponseEntity.ok(new DeviceListResponse(
                    "Success retrieving devices",
                    new MetaResponse(
                            result.page(),
                            result.limit(),
                            result.totalData(),
                            result.totalPages()
                    ),
                    devices
            ));
        } catch (IllegalArgumentException error) {
            return validationError(error.getMessage());
        } catch (NoSuchElementException error) {
            return notFound(error.getMessage());
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @GetMapping("/{device_id}")
    @Operation(
            summary = "Read Device Detail",
            description = "Mengambil detail satu perangkat berdasarkan ID."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Device found",
                                  "data": {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "Sensor-Suhu",
                                    "type": "PM2120",
                                    "status": "active"
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
                description = "Not Found",
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
    public ResponseEntity<?> findById(
            @Parameter(
                    in = ParameterIn.PATH,
                    name = "device_id",
                    description = "ID perangkat.",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable("device_id") String deviceId
    ) {
        try {
            Device device = deviceService.findById(deviceId);
            return ResponseEntity.ok(new DeviceDataResponse("Device found", deviceResponse(device)));
        } catch (IllegalArgumentException error) {
            return validationError(error.getMessage());
        } catch (NoSuchElementException error) {
            return notFound(error.getMessage());
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @PutMapping("/{device_id}")
    @Operation(
            summary = "Update Device All",
            description = "Mengubah seluruh data atribut perangkat sekaligus. Semua atribut name, type, dan status wajib dikirimkan."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Device data fully updated successfully",
                                  "data": {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "Sensor-Suhu",
                                    "type": "PM2120",
                                    "status": "inactive"
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
                                  "details": "All attributes (name, type, status) are required for PUT method"
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Not Found",
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
    public ResponseEntity<?> update(
            @Parameter(
                    in = ParameterIn.PATH,
                    name = "device_id",
                    description = "ID perangkat.",
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
                                      "name": "Sensor-Suhu",
                                      "type": "PM2120",
                                      "status": "inactive"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody UpdateDeviceRequest request,
            BindingResult validationResult
    ) {
        if (validationResult.hasErrors()) {
            return validationError(validationResult);
        }

        try {
            Device device = deviceService.update(
                    deviceId,
                    request.name(),
                    request.type(),
                    request.status()
            );
            return ResponseEntity.ok(
                    new DeviceDataResponse(
                            "Device data fully updated successfully",
                            deviceResponse(device)
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

    @PatchMapping("/{device_id}")
    @Operation(
            summary = "Update Device Partial",
            description = "Mengubah status atau sebagian data perangkat."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(value = """
                                {
                                  "message": "Device status updated successfully",
                                  "data": {
                                    "id": "550e8400-e29b-41d4-a716-446655440000",
                                    "name": "Sensor-Suhu",
                                    "type": "PM2120",
                                    "status": "inactive"
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
                                  "details": "At least one field must be provided"
                                }
                                """)
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Not Found",
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
    public ResponseEntity<?> patch(
            @Parameter(
                    in = ParameterIn.PATH,
                    name = "device_id",
                    description = "ID perangkat.",
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
                                      "status": "active"
                                    }
                                    """)
                    )
            )
            @RequestBody PatchDeviceRequest request
    ) {
        try {
            Device device = deviceService.patch(
                    deviceId,
                    request == null ? null : request.name(),
                    request == null ? null : request.type(),
                    request == null ? null : request.status()
            );
            return ResponseEntity.ok(
                    new DeviceDataResponse(
                            "Device status updated successfully",
                            deviceResponse(device)
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

    @DeleteMapping("/{device_id}")
    @Operation(
            summary = "Delete Device",
            description = "Menghapus perangkat dari sistem."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content"),
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
                description = "Not Found",
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
    public ResponseEntity<?> delete(
            @Parameter(
                    in = ParameterIn.PATH,
                    name = "device_id",
                    description = "ID perangkat.",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @PathVariable("device_id") String deviceId
    ) {
        try {
            deviceService.delete(deviceId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException error) {
            return validationError(error.getMessage());
        } catch (NoSuchElementException error) {
            return notFound(error.getMessage());
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    private DeviceResponse deviceResponse(Device device) {
        return new DeviceResponse(
                device.id(),
                device.name(),
                device.type(),
                device.status()
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

    public record CreateDeviceRequest(
            @NotBlank(message = "Attribute 'name' is required") String name,
            @NotBlank(message = "Attribute 'type' is required") String type,
            String status
    ) {
    }

    public record UpdateDeviceRequest(
            @NotBlank(message = "All attributes (name, type, status) are required for PUT method") String name,
            @NotBlank(message = "All attributes (name, type, status) are required for PUT method") String type,
            @NotBlank(message = "All attributes (name, type, status) are required for PUT method") String status
    ) {
    }

    public record PatchDeviceRequest(
            String name,
            String type,
            String status
    ) {
    }

    public record DeviceResponse(
            String id,
            String name,
            String type,
            String status
    ) {
    }

    public record DeviceDataResponse(
            String message,
            DeviceResponse data
    ) {
    }

    public record DeviceListResponse(
            String message,
            MetaResponse meta,
            List<DeviceResponse> data
    ) {
    }

    public record MetaResponse(
            int page,
            int limit,
            @JsonProperty("total_data") long totalData,
            @JsonProperty("total_pages") int totalPages
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            String error,
            Object details
    ) {
    }
}
