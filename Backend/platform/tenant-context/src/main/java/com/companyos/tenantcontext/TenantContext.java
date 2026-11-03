package com.companyos.tenantcontext;

import java.util.Set;
import java.util.UUID;

public record TenantContext(
        UUID tenantId,
        UUID userId,
        String tenantKey,
        String email,
        String displayName,
        Set<String> authorities) {
}
