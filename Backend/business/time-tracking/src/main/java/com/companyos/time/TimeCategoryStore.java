package com.companyos.time;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimeCategoryStore {
    List<TimeCategory> find(UUID tenantId);
    boolean hasConfiguredCategories(UUID tenantId);
    boolean isEnabled(UUID tenantId, String categoryKey);
    TimeCategory create(UUID tenantId, String categoryKey, String name, Instant now);
    TimeCategory update(UUID tenantId, UUID categoryId, String name, boolean enabled, Instant now);
}
