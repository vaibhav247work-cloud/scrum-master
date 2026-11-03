package com.companyos.backend;

import com.companyos.security.contract.SessionStore;
import com.companyos.tenantmanagement.api.TenantLifecycleService;
import com.companyos.tenantmanagement.api.TenantLifecycleStore;
import com.companyos.tenantmanagement.api.TenantLifecycleSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TenantLifecycleServiceTest {
    @Test
    void suspendingTenantRevokesEveryTenantSession() {
        TenantLifecycleStore store = mock(TenantLifecycleStore.class);
        SessionStore sessions = mock(SessionStore.class);
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(store.updateStatus(tenantId, actorId, "SUSPENDED", "maintenance"))
                .thenReturn(summary(tenantId, "SUSPENDED"));

        new TenantLifecycleService(store, sessions).changeStatus(tenantId, actorId, "suspended", "maintenance");

        verify(sessions).revokeAllForTenant(tenantId);
    }

    @Test
    void reactivationDoesNotRevokeNewSessions() {
        TenantLifecycleStore store = mock(TenantLifecycleStore.class);
        SessionStore sessions = mock(SessionStore.class);
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(store.updateStatus(tenantId, actorId, "ACTIVE", "restored"))
                .thenReturn(summary(tenantId, "ACTIVE"));

        new TenantLifecycleService(store, sessions).changeStatus(tenantId, actorId, "ACTIVE", "restored");

        verifyNoInteractions(sessions);
    }

    @Test
    void deletionScheduleKeepsCurrentAdminSessionDuringGraceWindow() {
        TenantLifecycleStore store = mock(TenantLifecycleStore.class);
        SessionStore sessions = mock(SessionStore.class);
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(store.updateStatus(tenantId, actorId, "DELETION_SCHEDULED", "customer request"))
                .thenReturn(summary(tenantId, "DELETION_SCHEDULED"));

        new TenantLifecycleService(store, sessions).changeStatus(tenantId, actorId, "DELETION_SCHEDULED", "customer request");

        verifyNoInteractions(sessions);
    }

    @Test
    void unsupportedLifecycleStatusIsRejectedBeforeWriting() {
        TenantLifecycleStore store = mock(TenantLifecycleStore.class);
        SessionStore sessions = mock(SessionStore.class);

        assertThrows(IllegalArgumentException.class,
                () -> new TenantLifecycleService(store, sessions)
                        .changeStatus(UUID.randomUUID(), UUID.randomUUID(), "DELETED", ""));

        verifyNoInteractions(store, sessions);
    }

    private static TenantLifecycleSummary summary(UUID tenantId, String status) {
        return new TenantLifecycleSummary(tenantId, "tenant", "Tenant", status, null, null, Instant.now());
    }
}
