package com.companyos.eventdelivery;

import com.companyos.notification.ExternalNotificationSender;
import com.companyos.notification.NotificationChannelStore;
import com.companyos.notification.TelegramChannelConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class TelegramNotificationSender implements ExternalNotificationSender {
    private final NotificationChannelStore channelStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TelegramNotificationSender(NotificationChannelStore channelStore, ObjectMapper objectMapper) {
        this.channelStore = channelStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(UUID tenantId, String eventType, String title, String body) {
        TelegramChannelConfig config = channelStore.findTelegram(tenantId).filter(TelegramChannelConfig::enabled).orElse(null);
        if (config == null) return;
        if (config.botToken() == null || config.botToken().isBlank() || config.chatId() == null || config.chatId().isBlank()) {
            throw new IllegalStateException("Telegram channel is enabled but not fully configured");
        }
        try {
            String text = ("[" + eventType + "] " + title + "\n" + body);
            if (text.length() > 4096) text = text.substring(0, 4093) + "...";
            String json = objectMapper.writeValueAsString(Map.of("chat_id", config.chatId(), "text", text));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + config.botToken() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode result = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || !result.path("ok").asBoolean(false)) {
                throw new IllegalStateException("Telegram delivery failed: HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram delivery interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Telegram delivery failed", exception);
        }
    }
}
