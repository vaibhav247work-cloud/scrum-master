package com.companyos.identity.access;

import java.util.Optional;

public interface IdentityAuthenticationPort {
    Optional<TenantUserCredentials> findByTenantKeyAndEmail(String tenantKey, String email);
}
