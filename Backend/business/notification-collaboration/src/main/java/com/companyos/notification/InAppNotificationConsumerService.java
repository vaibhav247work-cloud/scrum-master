package com.companyos.notification;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class InAppNotificationConsumerService implements OutboxNotificationConsumer {
    private final InAppNotificationStore store;
    private final NotificationEventParser parser;

    public InAppNotificationConsumerService(InAppNotificationStore store, NotificationEventParser parser) {
        this.store = store;
        this.parser = parser;
    }

    @Override
    public String consumerKey() {
        return "notification.in_app";
    }

    @Override
    public void consume(UUID eventId, UUID tenantId, String eventType, String payloadJson) {
        for (NotificationEventMessage message : parser.parseMany(eventType, payloadJson)) {
            store.create(new InAppNotification(
                    UUID.randomUUID(), tenantId, message.recipientUserId(), eventId,
                    message.type(), message.title(), message.body(), null, Instant.now()));
        }
    }
}
