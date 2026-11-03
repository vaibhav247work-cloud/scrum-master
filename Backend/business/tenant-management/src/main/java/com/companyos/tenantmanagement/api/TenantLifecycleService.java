package com.companyos.tenantmanagement.api;

import com.companyos.security.contract.SessionStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantLifecycleService {
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "SUSPENDED", "DELETION_SCHEDULED");
    private static final int DELETION_GRACE_DAYS = 30;

    private final TenantLifecycleStore store;
    private final SessionStore sessionStore;

    public TenantLifecycleService(TenantLifecycleStore store, SessionStore sessionStore) {
        this.store = store;
        this.sessionStore = sessionStore;
    }

    @Transactional(readOnly = true)
    public TenantLifecycleSummary status(UUID tenantId) {
        return store.find(tenantId);
    }

    @Transactional
    public TenantLifecycleSummary changeStatus(UUID tenantId, UUID actorId, String requestedStatus, String reason) {
        String status = required(requestedStatus, "status").toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be ACTIVE, SUSPENDED, or DELETION_SCHEDULED");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() > 500) {
            throw new IllegalArgumentException("reason must be at most 500 characters");
        }
        TenantLifecycleSummary updated = store.updateStatus(tenantId, actorId, status, normalizedReason);
        if ("SUSPENDED".equals(status)) {
            // Suspension is an access stop and must invalidate every cached session immediately.
            // A scheduled deletion keeps the current Company Admin session alive so the
            // tenant can cancel the request during its grace window; platform-level
            // suspension remains the irreversible access boundary.
            sessionStore.revokeAllForTenant(tenantId);
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID tenantId) {
        return store.export(tenantId);
    }

    public static Instant deletionDateFromNow() {
        return Instant.now().plus(DELETION_GRACE_DAYS, ChronoUnit.DAYS);
    }

    private static String required(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
