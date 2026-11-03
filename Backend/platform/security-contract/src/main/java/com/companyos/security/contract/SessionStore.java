package com.companyos.security.contract;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface SessionStore {
    void save(String rawToken, SessionData session, Duration ttl);

    Optional<SessionData> find(String rawToken);

    void revoke(String rawToken);

    void revokeAll(UUID tenantId, UUID userId);

    void revokeAllForTenant(UUID tenantId);
}
