package com.companyos.security.contract;

import java.time.Duration;

public record RateLimitRule(String key, int limit, Duration window) {
    public RateLimitRule {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Rate-limit key is required");
        if (limit <= 0) throw new IllegalArgumentException("Rate-limit limit must be positive");
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate-limit window must be positive");
        }
    }
}
