package com.companyos.notification;

import java.util.UUID;

public interface OutboxNotificationConsumer {
    default String consumerKey() {
        return "notification";
    }

    default boolean retryable(Exception exception) {
        return !(exception instanceof NotificationPayloadException);
    }

    void consume(UUID eventId, UUID tenantId, String eventType, String payloadJson);
}
