package com.companyos.feature.entitlement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class FeatureEntitlementService {
    private final FeatureEntitlementStore store;
    private final FeatureEntitlementCache cache;

    public FeatureEntitlementService(FeatureEntitlementStore store) {
        this(store, FeatureEntitlementCache.noop());
    }

    @Autowired
    public FeatureEntitlementService(FeatureEntitlementStore store, FeatureEntitlementCache cache) {
        this.store = store;
        this.cache = cache;
    }

    public List<FeatureEntitlement> list(UUID tenantId) {
        try {
            Optional<List<FeatureEntitlement>> cached = cache.get(tenantId);
            if (cached.isPresent()) return cached.get();
        } catch (RuntimeException ignored) {
            // A cache outage must not break a non-sensitive configuration read.
        }
        List<FeatureEntitlement> entitlements = store.findByTenantId(tenantId);
        try {
            cache.put(tenantId, entitlements);
        } catch (RuntimeException ignored) {
            // MySQL remains authoritative; the next read can repopulate the cache.
        }
        return entitlements;
    }

    public void requireEnabled(UUID tenantId, String featureKey) {
        String normalized = normalize(featureKey);
        if (!store.isEnabled(tenantId, normalized)) {
            throw new FeatureNotEnabledException(normalized);
        }
    }

    public boolean isEnabled(UUID tenantId, String featureKey) {
        return store.isEnabled(tenantId, normalize(featureKey));
    }

    public void setEnabled(UUID tenantId, String featureKey, boolean enabled) {
        store.setEnabled(tenantId, normalize(featureKey), enabled, Instant.now());
        try {
            cache.evict(tenantId);
        } catch (RuntimeException ignored) {
            // The list cache is bounded by TTL and is not used for authorization decisions.
        }
    }

    private static String normalize(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("featureKey is required");
        return key.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    public static class FeatureNotEnabledException extends RuntimeException {
        public FeatureNotEnabledException(String featureKey) {
            super("Feature is not enabled for this tenant: " + featureKey);
        }
    }
}
