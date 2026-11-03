package com.companyos.idempotency;

import java.time.Instant;

public record IdempotencyRecord(String scope, String key, String requestHash, String status,
                                String responseJson, Instant expiresAt) {
    public boolean processing() { return "PROCESSING".equals(status); }
    public boolean completed() { return "COMPLETED".equals(status); }
}
