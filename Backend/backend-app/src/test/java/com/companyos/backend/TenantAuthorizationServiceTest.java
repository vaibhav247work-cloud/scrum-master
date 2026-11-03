package com.companyos.backend;

import com.companyos.auth.local.TenantAuthorizationService;
import com.companyos.tenantcontext.TenantContext;
import com.companyos.tenantcontext.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantAuthorizationServiceTest {
    private final TenantAuthorizationService authorization = new TenantAuthorizationService();

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void deniesWhenThereIsNoVerifiedTenantContext() {
        assertFalse(authorization.hasPermission("tenant.user.read"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorization.requirePermission("tenant.user.read"));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void deniesPermissionThatIsNotInTheVerifiedSession() {
        setContext(Set.of("PERM_project.read"));

        assertFalse(authorization.hasPermission("project.manage"));
        assertThrows(ResponseStatusException.class,
                () -> authorization.requirePermission("project.manage"));
    }

    @Test
    void grantsAnExactPermissionFromTheVerifiedSession() {
        setContext(Set.of("PERM_project.read"));

        assertTrue(authorization.hasPermission("project.read"));
        authorization.requirePermission("project.read");
    }

    @Test
    void grantsAnyPermissionToTheExplicitAdministratorWildcard() {
        setContext(Set.of("PERM_*"));

        assertTrue(authorization.hasPermission("dependency.manage"));
        authorization.requirePermission("dependency.manage");
    }

    private static void setContext(Set<String> authorities) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "authorization-test",
                "test@example.com",
                "Authorization Test",
                authorities));
    }
}
