package com.companyos.reporting;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ControlTowerService {
    private final ControlTowerStore store;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    public ControlTowerService(ControlTowerStore store, FeatureEntitlementService featureEntitlements,
                               AuthorizationService authorization) {
        this.store = store;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public ControlTowerSummary summary(UUID tenantId, LocalDate requestedWeekStart) {
        featureEntitlements.requireEnabled(tenantId, "REPORTING");
        authorization.requirePermission("control.tower.read");
        LocalDate date = requestedWeekStart == null ? LocalDate.now() : requestedWeekStart;
        return store.summarize(tenantId, date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }
}
