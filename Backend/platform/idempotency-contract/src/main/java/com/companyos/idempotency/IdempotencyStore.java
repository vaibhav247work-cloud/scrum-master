package com.companyos.idempotency;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyStore {
    Optional<IdempotencyRecord> find(String scope, String key);
    boolean reserve(String scope, String key, String requestHash, Duration ttl);
    void complete(String scope, String key, String requestHash, String responseJson, Duration ttl);
    void release(String scope, String key, String requestHash);
}
