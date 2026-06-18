package com.device.management_api.mqtt;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.device.management_api.dto.device.DeviceResponse;
import com.device.management_api.dto.telemetry.TelemetryReading;
import com.device.management_api.entity.Device;
import com.device.management_api.repository.CassandraTelemetryRepository;
import com.device.management_api.repository.DeviceRepository;
import com.device.management_api.websocket.TelemetryWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class MqttTelemetrySubscriber {
    private static final String CLIENT_ID_PREFIX = "management-api-telemetry";
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final int RECONNECT_PERIOD_MS = 2000;

    private final MqttProperties properties;
    private final CassandraTelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;
    private final TelemetryWebSocketHandler telemetryWebSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private Mqtt3AsyncClient client;
    private ScheduledFuture<?> reconnectTask;

    public MqttTelemetrySubscriber(
            MqttProperties properties,
            CassandraTelemetryRepository telemetryRepository,
            DeviceRepository deviceRepository,
            TelemetryWebSocketHandler telemetryWebSocketHandler
    ) {
        this.properties = properties;
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository = deviceRepository;
        this.telemetryWebSocketHandler = telemetryWebSocketHandler;
    }

    @PostConstruct
    public void start() {
        BrokerEndpoint broker = parseBrokerUrl(properties.brokerUrl());
        client = Mqtt3Client.builder()
                .identifier(CLIENT_ID_PREFIX + "-" + UUID.randomUUID())
                .serverHost(broker.host())
                .serverPort(broker.port())
                .addConnectedListener(context -> {
                    connected.set(true);
                    cancelReconnect();
                    subscribe();
                    System.out.println("MQTT connected to " + properties.brokerUrl());
                })
                .addDisconnectedListener(context -> {
                    connected.set(false);
                    System.out.println("MQTT disconnected: " + context.getCause().getMessage());
                    scheduleReconnect();
                })
                .buildAsync();

        connect();
    }

    @PreDestroy
    public void stop() {
        cancelReconnect();
        reconnectExecutor.shutdownNow();

        if (client == null || !connected.get()) {
            return;
        }

        client.disconnect()
                .whenComplete((result, error) -> {
                    if (error != null) {
                        System.out.println("MQTT disconnect failed: " + error.getMessage());
                        return;
                    }

                    System.out.println("MQTT subscriber stopped");
                });
    }

    private void connect() {
        if (client == null || connected.get() || !connecting.compareAndSet(false, true)) {
            return;
        }

        client.connectWith()
                .cleanSession(true)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .send()
                .whenComplete((connAck, error) -> {
                    connecting.set(false);

                    if (error != null) {
                        System.out.println("MQTT connection failed: " + error.getMessage());
                        scheduleReconnect();
                    }
                });
    }

    private void subscribe() {
        client.subscribeWith()
                .topicFilter(properties.telemetryTopic())
                .qos(MqttQos.AT_MOST_ONCE)
                .callback(this::handleMessage)
                .send()
                .whenComplete((subAck, error) -> {
                    if (error != null) {
                        System.out.println("MQTT subscribe failed: " + error.getMessage());
                        return;
                    }

                    System.out.println("MQTT subscribed to " + properties.telemetryTopic());
                });
    }

    private void scheduleReconnect() {
        if (reconnectTask != null && !reconnectTask.isDone()) {
            return;
        }

        reconnectTask = reconnectExecutor.scheduleWithFixedDelay(
                this::connect,
                RECONNECT_PERIOD_MS,
                RECONNECT_PERIOD_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void cancelReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    private void handleMessage(Mqtt3Publish publish) {
        String topic = publish.getTopic().toString();
        UUID deviceId = extractDeviceId(topic);
        if (deviceId == null) {
            System.out.println("MQTT telemetry ignored because topic is invalid: " + topic);
            return;
        }

        MqttTelemetryPayload payload = parsePayload(publish.getPayloadAsBytes());
        if (payload == null) {
            System.out.println("MQTT telemetry ignored because payload is invalid for topic: " + topic);
            return;
        }

        try {
            TelemetryReading telemetry = telemetryRepository.insert(
                    deviceId,
                    recordMonth(payload.ts()),
                    payload.ts(),
                    payload.temperature(),
                    payload.humidity()
            );
            System.out.println("MQTT telemetry persisted for device " + deviceId + " at " + payload.ts());

            Device device = deviceRepository.findById(deviceId).orElse(null);
            if (device == null) {
                System.out.println("WebSocket telemetry ignored because device ID " + deviceId + " not found");
                return;
            }

            telemetryWebSocketHandler.broadcastTelemetry(
                    new DeviceResponse(
                            device.id(),
                            device.name(),
                            device.type(),
                            device.status()
                    ),
                    telemetry
            );
        } catch (RuntimeException error) {
            System.out.println("Failed to persist MQTT telemetry: " + error.getMessage());
        }
    }

    private UUID extractDeviceId(String topic) {
        String[] parts = topic.split("/");

        if (
                parts.length != 6 ||
                !parts[0].equals("gedung-solu") ||
                !parts[1].equals("monitoring") ||
                !parts[2].equals("lantai-1") ||
                !parts[3].equals("devices") ||
                !parts[5].equals("telemetry")
        ) {
            return null;
        }

        try {
            return UUID.fromString(parts[4]);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private MqttTelemetryPayload parsePayload(byte[] payload) {
        try {
            MqttTelemetryPayload telemetry = objectMapper.readValue(payload, MqttTelemetryPayload.class);

            if (
                    telemetry.ts() == null ||
                    telemetry.ts() <= 0 ||
                    telemetry.temperature() == null ||
                    telemetry.humidity() == null
            ) {
                return null;
            }

            return telemetry;
        } catch (IOException error) {
            return null;
        }
    }

    private String recordMonth(long ts) {
        return YearMonth.from(
                Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC)
        ).toString();
    }

    private BrokerEndpoint parseBrokerUrl(String brokerUrl) {
        URI uri = URI.create(brokerUrl == null || brokerUrl.isBlank()
                ? "mqtt://127.0.0.1:1883"
                : brokerUrl);

        if (!"mqtt".equals(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("MQTT_BROKER_URL must use mqtt://host:port format");
        }

        return new BrokerEndpoint(uri.getHost(), uri.getPort() == -1 ? 1883 : uri.getPort());
    }

    private record BrokerEndpoint(String host, int port) {
    }
}
