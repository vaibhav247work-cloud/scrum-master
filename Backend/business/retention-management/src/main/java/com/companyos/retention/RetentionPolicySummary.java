package com.companyos.retention;

import java.time.Instant;
import java.util.UUID;

public record RetentionPolicySummary(
        UUID tenantId,
        boolean enabled,
        String operationMode,
        int activityRetentionDays,
        int notificationRetentionDays,
        int deliveryRetentionDays,
        int auditRetentionDays,
        int policyVersion,
        Instant updatedAt) {
}
