package com.companyos.reporting;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ForecastPolicyConfigurationService {
    private final ForecastPolicyStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public ForecastPolicyConfigurationService(ForecastPolicyStore store, FeatureEntitlementService features,
                                              AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public ForecastPolicy get(UUID tenantId) {
        features.requireEnabled(tenantId, "REPORTING");
        authorization.requirePermission("tenant.settings.manage");
        ForecastPolicy policy = store.find(tenantId);
        return policy == null ? ForecastPolicy.defaults() : policy.validated();
    }

    @Transactional
    public ForecastPolicy update(UUID tenantId, UUID actorId, ForecastPolicy policy) {
        features.requireEnabled(tenantId, "REPORTING");
        authorization.requirePermission("tenant.settings.manage");
        return store.save(tenantId, actorId, policy.validated(), Instant.now());
    }
}
