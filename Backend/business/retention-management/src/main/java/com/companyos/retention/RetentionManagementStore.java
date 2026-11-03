package com.companyos.retention;

import java.util.List;
import java.util.UUID;

public interface RetentionManagementStore {
    RetentionPolicySummary findPolicy(UUID tenantId);
    RetentionPolicySummary savePolicy(UUID tenantId, UUID actorId, RetentionPolicyCommand command);
    RetentionCleanupSummary preview(UUID tenantId);
    RetentionCleanupSummary cleanup(UUID tenantId, UUID actorId);
    List<RetentionHoldSummary> findHolds(UUID tenantId);
    RetentionHoldSummary createHold(UUID tenantId, UUID actorId, String holdKey, String reason);
    RetentionHoldSummary releaseHold(UUID tenantId, UUID actorId, UUID holdId);
}
