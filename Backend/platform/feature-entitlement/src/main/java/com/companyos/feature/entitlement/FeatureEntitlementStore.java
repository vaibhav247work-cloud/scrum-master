package com.companyos.feature.entitlement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeatureEntitlementStore {
    List<FeatureEntitlement> findByTenantId(UUID tenantId);

    boolean isEnabled(UUID tenantId, String featureKey);

    void setEnabled(UUID tenantId, String featureKey, boolean enabled, Instant updatedAt);
}
