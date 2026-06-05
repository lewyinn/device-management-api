package com.device.management_api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM device_telemetries");
        jdbcTemplate.update("DELETE FROM devices");
    }

    @Test
    void crudDeviceWorks() throws Exception {
        mockMvc.perform(get("/api/v1/devices?page=abc&limit=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("Query parameter 'page' or 'limit' must be a valid number"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sensor-Suhu",
                                  "type": "DHT22",
                                  "status": "active"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Device successfully registered"))
                .andReturn();

        JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String deviceId = createBody.path("data").path("id").asText();
        UUID.fromString(deviceId);

        mockMvc.perform(get("/api/v1/devices?page=1&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(10))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/devices/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("Device ID must be a valid UUID"));

        mockMvc.perform(put("/api/v1/devices/{device_id}", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sensor-Baru",
                                  "type": "DHT22",
                                  "status": "inactive"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("inactive"));

        mockMvc.perform(patch("/api/v1/devices/{device_id}", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("At least one field must be provided"));

        mockMvc.perform(patch("/api/v1/devices/{device_id}", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "active"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        mockMvc.perform(delete("/api/v1/devices/{device_id}", deviceId))
                .andExpect(status().isNoContent());
    }

    @Test
    void telemetryEndpointsWork() throws Exception {
        MvcResult createDeviceResult = mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sensor-Suhu",
                                  "type": "DHT22",
                                  "status": "active"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createDeviceBody = objectMapper.readTree(createDeviceResult.getResponse().getContentAsString());
        String deviceId = createDeviceBody.path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/devices/not-a-uuid/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "values": {
                                    "temperature": 28.5,
                                    "humidity": 75.2
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("Device ID must be a valid UUID"));

        MvcResult createTelemetryResult = mockMvc.perform(post("/api/v1/devices/{device_id}/telemetry", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "values": {
                                    "temperature": 28.5,
                                    "humidity": 75.2
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Telemetry successfully recorded"))
                .andExpect(jsonPath("$.data.device_id").value(deviceId))
                .andReturn();

        JsonNode createTelemetryBody = objectMapper.readTree(createTelemetryResult.getResponse().getContentAsString());
        long telemetryTs = createTelemetryBody.path("data").path("data").path("ts").asLong();
        Integer telemetryId = jdbcTemplate.queryForObject(
                "SELECT id FROM device_telemetries WHERE device_id = ? AND ts = ?",
                Integer.class,
                deviceId,
                telemetryTs
        );

        mockMvc.perform(get("/api/v1/devices/{device_id}/telemetry", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success retrieving telemetry"))
                .andExpect(jsonPath("$.device_id").value(deviceId))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(get("/api/v1/devices/{device_id}/telemetry/latest", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Latest telemetry found"))
                .andExpect(jsonPath("$.data.device_id").value(deviceId));

        mockMvc.perform(delete("/api/v1/telemetry/not-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").value("Telemetry ID must be a valid number"));

        mockMvc.perform(delete("/api/v1/telemetry/{telemetry_id}", telemetryId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/devices/{device_id}/telemetry/latest", deviceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Telemetry for device ID " + deviceId + " not found"));
    }
}
