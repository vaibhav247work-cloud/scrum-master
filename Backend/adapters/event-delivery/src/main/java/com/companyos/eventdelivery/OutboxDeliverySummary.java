package com.companyos.eventdelivery;

import java.time.Instant;
import java.util.UUID;

public record OutboxDeliverySummary(
        UUID id,
        UUID eventId,
        String eventType,
        String consumer,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant processedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {
}
