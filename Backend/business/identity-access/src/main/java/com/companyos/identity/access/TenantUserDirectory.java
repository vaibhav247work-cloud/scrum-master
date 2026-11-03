package com.companyos.identity.access;

import java.util.List;
import java.util.UUID;

public interface TenantUserDirectory {
    List<TenantUserSummary> findByTenantId(UUID tenantId);
}
