package com.companyos.retention;

public record RetentionPolicyCommand(
        boolean enabled,
        String operationMode,
        int activityRetentionDays,
        int notificationRetentionDays,
        int deliveryRetentionDays,
        int auditRetentionDays) {
}
