package com.companyos.identity.access;

import java.util.UUID;

public record TenantRoleSummary(UUID roleId, String roleKey, String displayName, boolean systemRole) {
}
