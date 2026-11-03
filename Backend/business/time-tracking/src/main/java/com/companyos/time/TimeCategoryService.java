package com.companyos.time;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TimeCategoryService {
    private final TimeCategoryStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public TimeCategoryService(TimeCategoryStore store, FeatureEntitlementService features,
                               AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<TimeCategory> list(UUID tenantId) {
        features.requireEnabled(tenantId, "TIME_TRACKING");
        // Employees need to read enabled categories in order to create a valid
        // time entry; administration permissions remain required for mutations.
        authorization.requirePermission("time.entry.create");
        return store.find(tenantId);
    }

    @Transactional
    public TimeCategory create(UUID tenantId, String categoryKey, String name) {
        features.requireEnabled(tenantId, "TIME_TRACKING");
        authorization.requirePermission("time.category.manage");
        return store.create(tenantId, normalizeKey(categoryKey), normalizeName(name), Instant.now());
    }

    @Transactional
    public TimeCategory update(UUID tenantId, UUID categoryId, String name, boolean enabled) {
        features.requireEnabled(tenantId, "TIME_TRACKING");
        authorization.requirePermission("time.category.manage");
        return store.update(tenantId, categoryId, normalizeName(name), enabled, Instant.now());
    }

    private static String normalizeKey(String value) {
        String key = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!key.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("categoryKey must contain letters, numbers, or underscores");
        return key;
    }

    private static String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 160) throw new IllegalArgumentException("category name is required and must be at most 160 characters");
        return name;
    }
}
