package com.companyos.feature.entitlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureEntitlementCache {
    Optional<List<FeatureEntitlement>> get(UUID tenantId);

    void put(UUID tenantId, List<FeatureEntitlement> entitlements);

    void evict(UUID tenantId);

    static FeatureEntitlementCache noop() {
        return new FeatureEntitlementCache() {
            @Override
            public Optional<List<FeatureEntitlement>> get(UUID tenantId) {
                return Optional.empty();
            }

            @Override
            public void put(UUID tenantId, List<FeatureEntitlement> entitlements) { }

            @Override
            public void evict(UUID tenantId) { }
        };
    }
}
