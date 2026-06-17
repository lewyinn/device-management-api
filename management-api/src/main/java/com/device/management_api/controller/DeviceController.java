package com.device.management_api.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

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

import com.device.management_api.dto.device.CreateDeviceRequest;
import com.device.management_api.dto.device.DeviceDataResponse;
import com.device.management_api.dto.device.DeviceListResponse;
import com.device.management_api.dto.device.DevicePollingResponse;
import com.device.management_api.dto.device.DeviceResponse;
import com.device.management_api.dto.device.MetaResponse;
import com.device.management_api.dto.device.PatchDeviceRequest;
import com.device.management_api.dto.device.UpdateDeviceRequest;
import com.device.management_api.entity.Device;
import com.device.management_api.exception.ApiException;
import com.device.management_api.service.DeviceHttpProtectionService;
import com.device.management_api.service.DeviceService;
import com.device.management_api.service.TelegramNotificationService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Device")
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private static final int LONG_POLL_TIMEOUT_SECONDS = 25;
    private static final int LONG_POLL_INTERVAL_SECONDS = 1;

    private final DeviceService deviceService;
    private final DeviceHttpProtectionService deviceHttpProtectionService;
    private final ObjectMapper objectMapper;
    private final TelegramNotificationService telegramNotificationService;

    public DeviceController(
            DeviceService deviceService,
            DeviceHttpProtectionService deviceHttpProtectionService,
            ObjectMapper objectMapper,
            TelegramNotificationService telegramNotificationService
    ) {
        this.deviceService = deviceService;
        this.deviceHttpProtectionService = deviceHttpProtectionService;
        this.objectMapper = objectMapper;
        this.telegramNotificationService = telegramNotificationService;
    }

    @PostMapping
    @Operation(summary = "Create Device")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = DeviceDataResponse.class),
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
                                """))),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"Attribute 'name' is required\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
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
            BindingResult validationResult) 
        {
        if (validationResult.hasErrors()) {
            return validationError(validationResult);
        }

        ResponseEntity<?> rateLimitError = deviceHttpProtectionService.registerRateLimit(httpRequest);
        if (rateLimitError != null) {
            return rateLimitError;
        }

        // ResponseEntity<?> throttlingError = deviceHttpProtectionService.registerThrottling(httpRequest);
        // if (throttlingError != null) {
        //     return throttlingError;
        // }

        try {
            Device device = deviceService.create(request);
            DeviceResponse deviceResponse = toResponse(device);
            telegramNotificationService.sendDeviceRegisteredNotification(deviceResponse);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new DeviceDataResponse("Device successfully registered", deviceResponse)
            );
        } catch (ApiException error) {
            return apiError(error);
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @GetMapping
    @Operation(summary = "Read All Devices")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = DeviceListResponse.class),
                        examples = @ExampleObject(value = """
                                {
                                    "message": "Success retrieving devices",
                                    "meta": {
                                        "page": 1,
                                        "limit": 10,
                                        "total_data": 1,
                                        "total_pages": 1
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
                                """))),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"Query parameter 'page' or 'limit' must be a valid number\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<?> findAll(
            HttpServletRequest httpRequest,
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "10") String limit
    ) {
        ResponseEntity<?> rateLimitError = deviceHttpProtectionService.readRateLimit(httpRequest);
        if (rateLimitError != null) {
            return rateLimitError;
        }

        // ResponseEntity<?> throttlingError = deviceHttpProtectionService.readThrottling(httpRequest);
        // if (throttlingError != null) {
        //     return throttlingError;
        // }

        try {
            DeviceService.DevicePage result = deviceService.findAll(page, limit);
            List<DeviceResponse> devices = result.devices()
                    .stream()
                    .map(this::toResponse)
                    .toList();

            return ResponseEntity.ok(new DeviceListResponse(
                    "Success retrieving devices",
                    new MetaResponse(result.page(), result.limit(), result.totalData(), result.totalPages()),
                    devices
            ));
        } catch (ApiException error) {
            return apiError(error);
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    // @GetMapping("/short-poll")
    // @Operation(summary = "Short Poll Devices")
    // public ResponseEntity<?> shortPoll(
    //         HttpServletRequest httpRequest,
    //         @RequestParam(defaultValue = "1") String page,
    //         @RequestParam(defaultValue = "10") String limit) 
    // {
    //     try {
    //         DevicePageView result = devicePageView(page, limit);

    //         return ResponseEntity.ok(new DevicePollingResponse(
    //                 "Short polling response: devices retrieved",
    //                 "short_polling",
    //                 "Client requests this endpoint repeatedly at a fixed interval to refresh device data",
    //                 result.snapshot(),
    //                 result.meta(),
    //                 result.devices()
    //         ));
    //     } catch (ApiException error) {
    //         return apiError(error);
    //     } catch (JsonProcessingException | IllegalStateException error) {
    //         return internalError();
    //     }
    // }

    // @GetMapping("/long-poll")
    // @Operation(summary = "Long Poll Devices")
    // public ResponseEntity<?> longPoll(
    //         HttpServletRequest httpRequest,
    //         @RequestParam(required = false) String snapshot,
    //         @RequestParam(defaultValue = "1") String page,
    //         @RequestParam(defaultValue = "10") String limit
    // ) {
    //     try {
    //         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LONG_POLL_TIMEOUT_SECONDS);

    //         while (System.nanoTime() < deadline) {
    //             DevicePageView result = devicePageView(page, limit);

    //             if (snapshot == null || snapshot.isBlank() || !snapshot.equals(result.snapshot())) {
    //                 return ResponseEntity.ok(new DevicePollingResponse(
    //                         "Long polling response: device data changed",
    //                         "long_polling",
    //                         "Server held the HTTP request until the device list snapshot changed",
    //                         result.snapshot(),
    //                         result.meta(),
    //                         result.devices()
    //                 ));
    //             }

    //             LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(LONG_POLL_INTERVAL_SECONDS));
    //         }

    //         return ResponseEntity.ok(new DevicePollingResponse(
    //                 "Long polling timeout: device data did not change",
    //                 "long_polling",
    //                 "No device data change was detected within " + LONG_POLL_TIMEOUT_SECONDS + " seconds",
    //                 snapshot,
    //                 null,
    //                 null
    //         ));
    //     } catch (ApiException error) {
    //         return apiError(error);
    //     } catch (JsonProcessingException | IllegalStateException error) {
    //         return internalError();
    //     }
    // }

    @GetMapping("/{device_id}")
    @Operation(summary = "Read Device Detail")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = DeviceDataResponse.class),
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
                                """))),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"Device ID must be a valid UUID\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Device ID 550e8400-e29b-41d4-a716-446655440000 not found\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<?> findById(
            HttpServletRequest httpRequest,
            @PathVariable("device_id") String deviceId
    ) {
        try {
            Device device = deviceService.findById(deviceId);
            return ResponseEntity.ok(new DeviceDataResponse("Device found", toResponse(device)));
        } catch (ApiException error) {
            return apiError(error);
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @PutMapping("/{device_id}")
    @Operation(summary = "Update Device All")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = DeviceDataResponse.class),
                        examples = @ExampleObject(value = """
                                {
                                    "message": "Device data fully updated successfully",
                                    "data": {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "name": "Sensor-Suhu",
                                        "type": "PM2120",
                                        "status": "active"
                                    }
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"All attributes (name, type, status) are required for PUT method\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Device ID 550e8400-e29b-41d4-a716-446655440000 not found\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<?> update(
            @PathVariable("device_id") String deviceId,
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
            @Valid @RequestBody UpdateDeviceRequest request,
            BindingResult validationResult
    ) {
        if (validationResult.hasErrors()) {
            return validationError(validationResult);
        }

        try {
            Device device = deviceService.update(deviceId, request);
            return ResponseEntity.ok(new DeviceDataResponse("Device data fully updated successfully", toResponse(device)));
        } catch (ApiException error) {
            return apiError(error);
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @PatchMapping("/{device_id}")
    @Operation(summary = "Update Device Partial")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = DeviceDataResponse.class),
                        examples = @ExampleObject(value = """
                                {
                                    "message": "Device status updated successfully",
                                    "data": {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "name": "Sensor-Suhu",
                                        "type": "PM2120",
                                        "status": "active"
                                    }
                                }
                                """))),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"At least one field must be provided\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Device ID 550e8400-e29b-41d4-a716-446655440000 not found\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<?> patch(
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
            Device device = deviceService.patch(deviceId, request);
            return ResponseEntity.ok(new DeviceDataResponse("Device status updated successfully", toResponse(device)));
        } catch (ApiException error) {
            return apiError(error);
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    @DeleteMapping("/{device_id}")
    @Operation(summary = "Delete Device")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "No Content"),
        @ApiResponse(responseCode = "400", description = "Bad Request",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Validation failed\",\"details\":\"Device ID must be a valid UUID\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Device ID 550e8400-e29b-41d4-a716-446655440000 not found\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(value = "{\"error\":\"Internal server error\"}")))
    })
    public ResponseEntity<?> delete(@PathVariable("device_id") String deviceId) {
        try {
            deviceService.delete(deviceId);
            return ResponseEntity.noContent().build();
        } catch (ApiException error) {
            return apiError(error);
        } catch (RuntimeException error) {
            return internalError();
        }
    }

    private DeviceResponse toResponse(Device device) {
        return new DeviceResponse(device.id(), device.name(), device.type(), device.status());
    }

    private DevicePageView devicePageView(String page, String limit) throws JsonProcessingException {
        DeviceService.DevicePage result = deviceService.findAll(page, limit);
        List<DeviceResponse> devices = result.devices()
                .stream()
                .map(this::toResponse)
                .toList();
        MetaResponse meta = new MetaResponse(result.page(), result.limit(), result.totalData(), result.totalPages());

        return new DevicePageView(meta, devices, snapshot(result.totalData(), devices));
    }

    private String snapshot(long totalData, List<DeviceResponse> devices) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(new SnapshotPayload(totalData, devices));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", error);
        }
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

    private record DevicePageView(
            MetaResponse meta,
            List<DeviceResponse> devices,
            String snapshot
    ) {
    }

    private record SnapshotPayload(
            long totalData,
            List<DeviceResponse> devices
    ) {
    }
}
