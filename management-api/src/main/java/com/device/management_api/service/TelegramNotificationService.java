package com.device.management_api.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.device.management_api.dto.device.DeviceResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TelegramNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramNotificationService.class);
    private static final String TELEGRAM_API_BASE_URL = "https://api.telegram.org";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String botToken;
    private final String chatId;

    public TelegramNotificationService(
            ObjectMapper objectMapper,
            @Value("${telegram.bot-token:}") String botToken,
            @Value("${telegram.chat-id:}") String chatId
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.botToken = botToken;
        this.chatId = chatId;
    }

    @Async
    public void sendDeviceRegisteredNotification(DeviceResponse device) {
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            logger.warn("Telegram notifications are not configured");
            return;
        }

        try {
            String requestBody = objectMapper.writeValueAsString(new TelegramMessageRequest(
                    chatId,
                    formatDeviceRegisteredMessage(device),
                    "HTML",
                    true
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TELEGRAM_API_BASE_URL + "/bot" + botToken + "/sendMessage"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.error("Telegram device registration notification failed with status {}: {}",
                        response.statusCode(),
                        response.body());
            }
        } catch (IOException error) {
            logger.error("Telegram device registration notification failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            logger.error("Telegram device registration notification was interrupted", error);
        }
    }

    private String formatDeviceRegisteredMessage(DeviceResponse device) {
        return String.join("\n",
                "<b>Device registered</b>",
                "",
                "<b>ID:</b> <code>" + escapeHtml(device.id()) + "</code>",
                "<b>Name:</b> " + escapeHtml(device.name()),
                "<b>Type:</b> " + escapeHtml(device.type()),
                "<b>Status:</b> " + escapeHtml(device.status())
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private record TelegramMessageRequest(
            String chat_id,
            String text,
            String parse_mode,
            boolean disable_web_page_preview
    ) {
    }
}
