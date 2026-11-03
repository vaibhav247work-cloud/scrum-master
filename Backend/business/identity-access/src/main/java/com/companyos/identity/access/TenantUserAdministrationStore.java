package com.companyos.identity.access;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TenantUserAdministrationStore {
    List<TenantRoleSummary> findRoles(UUID tenantId);

    void createInvitation(TenantUserInvitationData data);

    boolean activateInvitation(UUID invitationId, String tokenHash, String passwordHash, Instant activatedAt);

    void replaceRoles(UUID tenantId, UUID userId, Set<String> roleKeys);

    void updateStatus(UUID tenantId, UUID userId, String status, Instant offboardedAt);
}
