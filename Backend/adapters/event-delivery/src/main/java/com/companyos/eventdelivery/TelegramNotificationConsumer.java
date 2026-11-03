package com.companyos.eventdelivery;

import com.companyos.notification.ExternalNotificationSender;
import com.companyos.notification.NotificationEventMessage;
import com.companyos.notification.NotificationEventParser;
import com.companyos.notification.OutboxNotificationConsumer;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TelegramNotificationConsumer implements OutboxNotificationConsumer {
    private final NotificationEventParser parser;
    private final ExternalNotificationSender sender;

    public TelegramNotificationConsumer(NotificationEventParser parser, ExternalNotificationSender sender) {
        this.parser = parser;
        this.sender = sender;
    }

    @Override
    public String consumerKey() {
        return "notification.telegram";
    }

    @Override
    public void consume(UUID eventId, UUID tenantId, String eventType, String payloadJson) {
        NotificationEventMessage message = parser.parse(eventType, payloadJson);
        sender.send(tenantId, eventType, message.title(), message.body());
    }
}
