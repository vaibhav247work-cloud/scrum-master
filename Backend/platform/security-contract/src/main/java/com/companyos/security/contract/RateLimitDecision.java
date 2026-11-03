package com.companyos.security.contract;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}
