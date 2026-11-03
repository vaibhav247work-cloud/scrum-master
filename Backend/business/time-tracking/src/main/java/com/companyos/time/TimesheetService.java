package com.companyos.time;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TimesheetService {
    private final TimesheetStore store;
    private final TimePolicyStore policyStore;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    public TimesheetService(TimesheetStore store, TimePolicyStore policyStore,
                            FeatureEntitlementService featureEntitlements,
                            AuthorizationService authorization) {
        this.store = store;
        this.policyStore = policyStore;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    @Transactional
    public TimesheetSummary current(UUID tenantId, UUID userId) {
        requireFeature(tenantId);
        authorization.requirePermission("time.timesheet.read");
        TimePolicy policy = policy(tenantId);
        return store.findOrCreateCurrent(tenantId, userId, policy.approvalCadence(), LocalDate.now(), Instant.now());
    }

    @Transactional
    public TimesheetSummary submit(UUID tenantId, UUID userId, UUID timesheetId) {
        requireFeature(tenantId);
        authorization.requirePermission("time.timesheet.submit");
        TimePolicy policy = policy(tenantId);
        boolean autoApprove = !policy.approvalEnabled() || policy.autoApprove();
        return store.submit(tenantId, userId, timesheetId, autoApprove, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TimesheetSummary> pending(UUID tenantId) {
        requireFeature(tenantId);
        authorization.requirePermission("time.timesheet.approve");
        return store.findPending(tenantId);
    }

    @Transactional
    public TimesheetSummary decide(UUID tenantId, UUID approverId, UUID timesheetId,
                                   boolean approved, String note) {
        requireFeature(tenantId);
        authorization.requirePermission("time.timesheet.approve");
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedNote.length() > 1000) {
            throw new IllegalArgumentException("decision note must be at most 1000 characters");
        }
        return store.decide(tenantId, timesheetId, approverId, approved, normalizedNote, Instant.now());
    }

    @Transactional
    public TimesheetSummary reopen(UUID tenantId, UUID actorId, UUID timesheetId, String note) {
        requireFeature(tenantId);
        authorization.requirePermission("time.timesheet.approve");
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedNote.length() > 1000) {
            throw new IllegalArgumentException("decision note must be at most 1000 characters");
        }
        return store.reopen(tenantId, timesheetId, actorId, normalizedNote, Instant.now());
    }

    private void requireFeature(UUID tenantId) {
        featureEntitlements.requireEnabled(tenantId, "TIME_TRACKING");
    }

    private TimePolicy policy(UUID tenantId) {
        TimePolicy policy = policyStore.find(tenantId);
        return (policy == null ? TimePolicy.defaults() : policy).validated();
    }
}
