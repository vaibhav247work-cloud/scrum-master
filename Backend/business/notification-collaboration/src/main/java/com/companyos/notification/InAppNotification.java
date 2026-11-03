package com.companyos.notification;

import java.time.Instant;
import java.util.UUID;

public record InAppNotification(
        UUID id,
        UUID tenantId,
        UUID recipientUserId,
        UUID sourceEventId,
        String notificationType,
        String title,
        String body,
        Instant readAt,
        Instant createdAt) {
}
