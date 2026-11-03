package com.companyos.retention;

import java.time.Instant;
import java.util.UUID;

public record RetentionCleanupSummary(
        UUID tenantId,
        String status,
        int notificationsEligible,
        int deliveriesEligible,
        int outboxEventsEligible,
        int auditEventsEligible,
        int notificationsRemoved,
        int deliveriesRemoved,
        int outboxEventsRemoved,
        int auditEventsRemoved,
        boolean legalHoldActive,
        Instant executedAt) {
}
