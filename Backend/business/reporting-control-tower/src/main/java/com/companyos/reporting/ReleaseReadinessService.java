package com.companyos.reporting;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReleaseReadinessService {
    private final ReleaseReadinessStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;
    private final ForecastPolicyStore policyStore;
    private final ForecastCalibrationStore calibrationStore;

    @Autowired
    public ReleaseReadinessService(ReleaseReadinessStore store, FeatureEntitlementService features,
                                   AuthorizationService authorization, ForecastPolicyStore policyStore,
                                   ForecastCalibrationStore calibrationStore) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
        this.policyStore = policyStore;
        this.calibrationStore = calibrationStore;
    }

    public ReleaseReadinessService(ReleaseReadinessStore store, FeatureEntitlementService features,
                                   AuthorizationService authorization, ForecastPolicyStore policyStore) {
        this(store, features, authorization, policyStore, null);
    }

    public ReleaseReadinessService(ReleaseReadinessStore store, FeatureEntitlementService features,
                                   AuthorizationService authorization) {
        this(store, features, authorization, null, null);
    }

    @Transactional(readOnly = true)
    public List<ReleaseReadinessRow> readiness(UUID tenantId, UUID projectId) {
        features.requireEnabled(tenantId, "REPORTING");
        features.requireEnabled(tenantId, "RELEASE_MANAGEMENT");
        authorization.requirePermission("report.read");
        ForecastPolicy policy = policyStore == null ? ForecastPolicy.defaults() : policyStore.find(tenantId);
        Map<UUID, ForecastCalibrationRow> calibration = calibrationStore == null ? Map.of()
                : calibrationStore.calibration(tenantId, java.time.LocalDate.now().minusDays(90), java.time.LocalDate.now().plusDays(1), projectId)
                .stream().collect(Collectors.toMap(ForecastCalibrationRow::projectId, Function.identity()));
        return store.readiness(tenantId, projectId).stream()
                .map(row -> row.withReadinessStatus(ForecastPolicyEvaluator.releaseStatus(row, policy)))
                .map(row -> applyProjection(row, policy, calibration.get(row.projectId())))
                .toList();
    }

    private static ReleaseReadinessRow applyProjection(ReleaseReadinessRow row, ForecastPolicy policy,
                                                       ForecastCalibrationRow calibration) {
        boolean enabled = policy.enabled() && policy.historicalCalibrationEnabled() && calibration != null;
        double factor = enabled ? calibration.calibrationFactor() : 1.0;
        int sampleSize = enabled ? calibration.sampleSize() : 0;
        Integer projectedDays = row.remainingEstimateMinutes() > 0 && row.velocityMinutesPerWeek() > 0
                ? (int) Math.ceil((row.remainingEstimateMinutes() * factor * 7.0) / row.velocityMinutesPerWeek()) : null;
        return row.withProjection(factor, sampleSize, projectedDays);
    }
}
