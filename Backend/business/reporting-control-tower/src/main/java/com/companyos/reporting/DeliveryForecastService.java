package com.companyos.reporting;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeliveryForecastService {
    private final DeliveryForecastStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;
    private final ForecastPolicyStore policyStore;
    private final ForecastCalibrationStore calibrationStore;

    @Autowired
    public DeliveryForecastService(DeliveryForecastStore store, FeatureEntitlementService features,
                                   AuthorizationService authorization, ForecastPolicyStore policyStore,
                                   ForecastCalibrationStore calibrationStore) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
        this.policyStore = policyStore;
        this.calibrationStore = calibrationStore;
    }

    public DeliveryForecastService(DeliveryForecastStore store, FeatureEntitlementService features,
                                   AuthorizationService authorization, ForecastPolicyStore policyStore) {
        this(store, features, authorization, policyStore, null);
    }

    public DeliveryForecastService(DeliveryForecastStore store, FeatureEntitlementService features,
                                   AuthorizationService authorization) {
        this(store, features, authorization, null, null);
    }

    @Transactional(readOnly = true)
    public List<DeliveryForecastRow> forecast(UUID tenantId, LocalDate requestedFrom,
                                              LocalDate requestedTo, UUID projectId) {
        features.requireEnabled(tenantId, "REPORTING");
        authorization.requirePermission("report.read");
        LocalDate from = requestedFrom == null
                ? LocalDate.now().with(TemporalAdjusters.firstDayOfMonth())
                : requestedFrom;
        LocalDate to = requestedTo == null ? LocalDate.now().plusDays(1) : requestedTo;
        if (!to.isAfter(from)) throw new IllegalArgumentException("forecast to date must be after from date");
        if (from.plusMonths(12).isBefore(to)) throw new IllegalArgumentException("forecast range cannot exceed 12 months");
        ForecastPolicy policy = policyStore == null ? ForecastPolicy.defaults() : policyStore.find(tenantId);
        Map<UUID, ForecastCalibrationRow> calibration = calibrationStore == null ? Map.of()
                : calibrationStore.calibration(tenantId, LocalDate.now().minusDays(90), LocalDate.now().plusDays(1), projectId)
                .stream().collect(Collectors.toMap(ForecastCalibrationRow::projectId, Function.identity()));
        return store.forecast(tenantId, from, to, projectId).stream()
                .map(row -> row.withForecastStatus(ForecastPolicyEvaluator.projectStatus(row, policy)))
                .map(row -> applyProjection(row, policy, calibration.get(row.projectId())))
                .toList();
    }

    private static DeliveryForecastRow applyProjection(DeliveryForecastRow row, ForecastPolicy policy,
                                                        ForecastCalibrationRow calibration) {
        boolean enabled = policy.enabled() && policy.historicalCalibrationEnabled() && calibration != null;
        double factor = enabled ? calibration.calibrationFactor() : 1.0;
        int sampleSize = enabled ? calibration.sampleSize() : 0;
        Integer projectedDays = row.remainingEstimateMinutes() > 0 && row.velocityMinutesPerWeek() > 0
                ? (int) Math.ceil((row.remainingEstimateMinutes() * factor * 7.0) / row.velocityMinutesPerWeek()) : null;
        return row.withProjection(factor, sampleSize, projectedDays);
    }
}
