package com.companyos.time;

import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TimePolicyService {
    private final TimePolicyStore store;
    private final AuthorizationService authorization;

    public TimePolicyService(TimePolicyStore store, AuthorizationService authorization) {
        this.store = store;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public TimePolicy get(UUID tenantId) {
        TimePolicy policy = store.find(tenantId);
        return policy == null ? TimePolicy.defaults() : policy.validated();
    }

    @Transactional
    public TimePolicy update(UUID tenantId, TimePolicy policy) {
        authorization.requirePermission("tenant.settings.manage");
        return store.save(tenantId, policy.validated(), Instant.now());
    }
}
