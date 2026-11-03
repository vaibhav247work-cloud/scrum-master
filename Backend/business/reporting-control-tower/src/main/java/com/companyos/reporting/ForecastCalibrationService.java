package com.companyos.reporting;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ForecastCalibrationService {
    private final ForecastCalibrationStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public ForecastCalibrationService(ForecastCalibrationStore store, FeatureEntitlementService features,
                                      AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<ForecastCalibrationRow> calibration(UUID tenantId, LocalDate requestedFrom,
                                                    LocalDate requestedTo, UUID projectId) {
        features.requireEnabled(tenantId, "REPORTING");
        authorization.requirePermission("report.read");
        LocalDate to = requestedTo == null ? LocalDate.now().plusDays(1) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(90) : requestedFrom;
        if (!to.isAfter(from)) throw new IllegalArgumentException("calibration to date must be after from date");
        if (from.plusMonths(12).isBefore(to)) throw new IllegalArgumentException("calibration range cannot exceed 12 months");
        return store.calibration(tenantId, from, to, projectId);
    }
}
