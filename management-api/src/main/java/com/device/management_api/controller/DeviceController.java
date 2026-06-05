package com.device.management_api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.device.management_api.entity.Device;
import com.device.management_api.exception.GlobalExceptionHandler.ErrorResponse;
import com.device.management_api.service.DeviceService;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@Tag(name = "Device")
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
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
    public ResponseEntity<DeviceDataResponse> create(
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
            @RequestBody Map<String, String> request) {
        Device device = deviceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new DeviceDataResponse("Device successfully registered", toResponse(device))
        );
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
    public DeviceListResponse findAll(
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "10") String limit
    ) {
        DeviceService.DevicePage result = deviceService.findAll(page, limit);
        List<DeviceResponse> devices = result.devices()
                .stream()
                .map(this::toResponse)
                .toList();

        return new DeviceListResponse(
                "Success retrieving devices",
                new MetaResponse(result.page(), result.limit(), result.totalData(), result.totalPages()),
                devices
        );
    }

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
    public DeviceDataResponse findById(@PathVariable("device_id") String deviceId) {
        Device device = deviceService.findById(deviceId);
        return new DeviceDataResponse("Device found", toResponse(device));
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
    public DeviceDataResponse update(
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
            @RequestBody Map<String, String> request
    ) {
        Device device = deviceService.update(deviceId, request);
        return new DeviceDataResponse("Device data fully updated successfully", toResponse(device));
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
    public DeviceDataResponse patch(
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
            @RequestBody Map<String, String> request
    ) {
        Device device = deviceService.patch(deviceId, request);
        return new DeviceDataResponse("Device status updated successfully", toResponse(device));
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
    public ResponseEntity<Void> delete(@PathVariable("device_id") String deviceId) {
        deviceService.delete(deviceId);
        return ResponseEntity.noContent().build();
    }

    private DeviceResponse toResponse(Device device) {
        return new DeviceResponse(device.id(), device.name(), device.type(), device.status());
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
}
