package com.companyos.feature.entitlement;

public record FeatureEntitlement(
        String featureKey,
        String displayName,
        boolean enabled,
        String status) {
}
