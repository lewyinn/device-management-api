package com.device.management_api.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.device.management_api.dto.device.DeviceResponse;
import com.device.management_api.dto.telemetry.TelemetryReading;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);
    private static final String TELEMETRY_CHANNEL = "telemetry";
    private static final String DEVICE_REGISTERED_CHANNEL = "deviceRegistered";
    private static final String DEVICE_TELEMETRY_PREFIX = "device_telemetry";
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();

    public TelemetryWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession concurrentSession = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT_BYTES
        );
        connections.put(session.getId(), new ClientConnection(concurrentSession));
        send(concurrentSession, Map.of(
                "type", "connected",
                "message", "WebSocket connection established"
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientConnection connection = connections.get(session.getId());
        if (connection == null) {
            return;
        }

        ClientMessage clientMessage = parseMessage(message.getPayload());
        if (clientMessage == null) {
            send(connection.session(), Map.of(
                    "type", "error",
                    "error", "Invalid WebSocket message"
            ));
            return;
        }

        if ("subscribe".equals(clientMessage.type())) {
            connection.subscriptions().addAll(clientMessage.channels());
        } else {
            connection.subscriptions().removeAll(clientMessage.channels());
        }

        send(connection.session(), Map.of(
                "type", "subscribe".equals(clientMessage.type()) ? "subscribed" : "unsubscribed",
                "channels", clientMessage.channels()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connections.remove(session.getId());
        logger.warn("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    public void broadcastTelemetry(DeviceResponse device, TelemetryReading telemetry) {
        Map<String, Object> data = Map.of(
                "device_id", device.id(),
                "device_name", device.name(),
                "device_type", device.type(),
                "ts", telemetry.ts(),
                "temperature", telemetry.temperature(),
                "humidity", telemetry.humidity()
        );

        broadcast(
                Set.of(
                        TELEMETRY_CHANNEL,
                        DEVICE_TELEMETRY_PREFIX + ":" + device.id()
                ),
                Map.of(
                        "type", "telemetry_received",
                        "data", data
                )
        );
    }

    public void broadcastDeviceRegistered(DeviceResponse device) {
        broadcast(
                Set.of(DEVICE_REGISTERED_CHANNEL),
                Map.of(
                        "type", "device_registered",
                        "data", Map.of(
                                "id", device.id(),
                                "name", device.name(),
                                "type", device.type(),
                                "status", device.status()
                        )
                )
        );
    }

    @PreDestroy
    public void shutdown() {
        List<ClientConnection> currentConnections = new ArrayList<>(connections.values());
        connections.clear();

        for (ClientConnection connection : currentConnections) {
            try {
                if (connection.session().isOpen()) {
                    connection.session().close(CloseStatus.GOING_AWAY);
                }
            } catch (IOException error) {
                logger.warn("Failed to close WebSocket session {}: {}",
                        connection.session().getId(), error.getMessage());
            }
        }
    }

    private void broadcast(Set<String> channels, Map<String, Object> event) {
        for (ClientConnection connection : connections.values()) {
            boolean subscribed = channels.stream().anyMatch(connection.subscriptions()::contains);
            if (subscribed) {
                send(connection.session(), event);
            }
        }
    }

    private ClientMessage parseMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText();
            JsonNode channelsNode = root.get("channels");

            if (channelsNode == null && root.has("channel")) {
                channelsNode = objectMapper.createArrayNode().add(root.get("channel").asText());
            }

            if (
                    (!"subscribe".equals(type) && !"unsubscribe".equals(type)) ||
                    channelsNode == null ||
                    !channelsNode.isArray() ||
                    channelsNode.isEmpty()
            ) {
                return null;
            }

            List<String> channels = new ArrayList<>();
            for (JsonNode channelNode : channelsNode) {
                if (!channelNode.isTextual() || !isValidChannel(channelNode.asText())) {
                    return null;
                }
                channels.add(channelNode.asText());
            }

            return new ClientMessage(type, channels);
        } catch (IOException error) {
            return null;
        }
    }

    private boolean isValidChannel(String channel) {
        if (TELEMETRY_CHANNEL.equals(channel) || DEVICE_REGISTERED_CHANNEL.equals(channel)) {
            return true;
        }

        String prefix = DEVICE_TELEMETRY_PREFIX + ":";
        if (!channel.startsWith(prefix)) {
            return false;
        }

        try {
            UUID.fromString(channel.substring(prefix.length()));
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private void send(WebSocketSession session, Object message) {
        if (!session.isOpen()) {
            connections.remove(session.getId());
            return;
        }

        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException | IllegalStateException error) {
            connections.remove(session.getId());
            logger.warn("Failed to send WebSocket message to session {}: {}",
                    session.getId(), error.getMessage());
        }
    }

    private record ClientConnection(
            WebSocketSession session,
            Set<String> subscriptions
    ) {
        private ClientConnection(WebSocketSession session) {
            this(session, ConcurrentHashMap.newKeySet());
        }
    }

    private record ClientMessage(
            String type,
            List<String> channels
    ) {
    }
}
