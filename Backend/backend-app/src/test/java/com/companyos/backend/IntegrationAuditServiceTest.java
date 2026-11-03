package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.integration.CreateIntegrationConnectionCommand;
import com.companyos.integration.ExternalIntegrationEventSummary;
import com.companyos.integration.IngestIntegrationEventCommand;
import com.companyos.integration.IntegrationAuditService;
import com.companyos.integration.IntegrationAuditStore;
import com.companyos.integration.IntegrationConnectionSummary;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IntegrationAuditServiceTest {
    private final IntegrationAuditStore store = mock(IntegrationAuditStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final IntegrationAuditService service = new IntegrationAuditService(
            store, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void normalizesConnectionTypeBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        IntegrationConnectionSummary summary = new IntegrationConnectionSummary(UUID.randomUUID(), "GITHUB", "GitHub", "ACTIVE", "https://github.com", "acme", true, null, "", Instant.now(), Instant.now());
        when(entitlements.isEnabled(tenantId, "INTEGRATIONS")).thenReturn(true);
        when(store.createConnection(eq(tenantId), eq(actorId), any(), any())).thenReturn(summary);

        assertEquals(summary, service.createConnection(tenantId, actorId,
                new CreateIntegrationConnectionCommand(" github ", "GitHub", "https://github.com", "acme", "secret")));
        verify(store).createConnection(eq(tenantId), eq(actorId), eq(new CreateIntegrationConnectionCommand(
                "GITHUB", "GitHub", "https://github.com", "acme", "secret")), any());
    }

    @Test
    void rejectsUnsupportedCiEventBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "INTEGRATIONS")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.ingestEvent(tenantId, UUID.randomUUID(), "secret",
                new com.companyos.integration.IngestIntegrationEventCommand("CI_CD", "UNKNOWN", "build-1", "repo", "main", "prod", "https://ci", Instant.now(), "{}")));
        verifyNoInteractions(store);
    }

    @Test
    void signedWebhookUsesConnectionTenantAndDoesNotRequireSessionPermission() {
        UUID connectionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        IngestIntegrationEventCommand command = new IngestIntegrationEventCommand(
                "WEBHOOK", "TASK_COMPLETED", "external-1", "repo", "main", "prod", "",
                Instant.now(), "{}");
        ExternalIntegrationEventSummary summary = org.mockito.Mockito.mock(ExternalIntegrationEventSummary.class);
        when(store.tenantIdForConnection(connectionId)).thenReturn(tenantId);
        when(entitlements.isEnabled(tenantId, "INTEGRATIONS")).thenReturn(true);
        when(store.ingestSignedEvent(eq(connectionId), eq("sha256=abc"), eq("1700000000"), eq("{}"), any(), any()))
                .thenReturn(summary);

        assertEquals(summary, service.ingestSignedEvent(connectionId, "sha256=abc", "1700000000", "{}", command));
        verify(store).ingestSignedEvent(eq(connectionId), eq("sha256=abc"), eq("1700000000"), eq("{}"), any(), any());
    }
}
