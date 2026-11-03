package com.companyos.reporting;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryReportService {
    private final DeliveryReportStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public DeliveryReportService(DeliveryReportStore store, FeatureEntitlementService features, AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<DeliveryReportRow> delivery(UUID tenantId, LocalDate requestedFrom, LocalDate requestedTo, UUID projectId) {
        features.requireEnabled(tenantId, "REPORTING");
        authorization.requirePermission("report.read");
        LocalDate from = requestedFrom == null ? LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()) : requestedFrom;
        LocalDate to = requestedTo == null ? LocalDate.now().plusDays(1) : requestedTo;
        if (!to.isAfter(from)) throw new IllegalArgumentException("report to date must be after from date");
        if (from.plusMonths(12).isBefore(to)) throw new IllegalArgumentException("report range cannot exceed 12 months");
        return store.delivery(tenantId, from, to, projectId);
    }
}
