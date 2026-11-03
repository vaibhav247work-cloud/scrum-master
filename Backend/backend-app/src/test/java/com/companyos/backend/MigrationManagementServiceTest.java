package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.migration.ImportJobSummary;
import com.companyos.migration.MigrationManagementService;
import com.companyos.migration.MigrationManagementStore;
import com.companyos.migration.WorkItemImportRow;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.idempotency.IdempotencyRecord;
import com.companyos.idempotency.IdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.companyos.work.WorkManagementService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MigrationManagementServiceTest {
    private final MigrationManagementStore store = mock(MigrationManagementStore.class);
    private final WorkManagementService workManagement = mock(WorkManagementService.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final MigrationManagementService service = new MigrationManagementService(
            store, workManagement, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void rejectsDuplicateSourceKeysBeforeAnyWorkWrite() {
        UUID tenantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "MIGRATION_IMPORT")).thenReturn(true);
        when(store.start(eq(tenantId), any(), eq("WORK_ITEMS"), eq(2), any())).thenReturn(job(jobId, "PROCESSING"));
        when(store.find(tenantId, jobId)).thenReturn(java.util.Optional.of(job(jobId, "REJECTED")));

        ImportJobSummary result = service.importWorkItems(tenantId, UUID.randomUUID(), List.of(
                new WorkItemImportRow("legacy-1", null, null, null, "First", "", "TASK", "MEDIUM", null, null, ""),
                new WorkItemImportRow("LEGACY-1", null, null, null, "Second", "", "TASK", "MEDIUM", null, null, "")), false);

        assertEquals("REJECTED", result.status());
        verify(store).reject(eq(tenantId), eq(jobId), any(), any());
        verifyNoInteractions(workManagement);
    }

    @Test
    void enforcesTheImportBatchLimitBeforeCreatingAJob() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "MIGRATION_IMPORT")).thenReturn(true);

        List<WorkItemImportRow> rows = java.util.stream.IntStream.range(0, 501)
                .mapToObj(index -> new WorkItemImportRow("legacy-" + index, null, null, null, "Work", "", "TASK", "MEDIUM", null, null, ""))
                .toList();
        assertThrows(IllegalArgumentException.class, () -> service.importWorkItems(tenantId, UUID.randomUUID(), rows, false));
        verifyNoInteractions(store);
    }

    @Test
    void replaysCompletedImportFromRedisWithoutCreatingAnotherJob() throws Exception {
        IdempotencyStore idempotency = mock(IdempotencyStore.class);
        ImportJobSummary expected = job(UUID.randomUUID(), "COMPLETED");
        List<WorkItemImportRow> rows = List.of(
                new WorkItemImportRow("legacy-1", null, null, null, "First", "", "TASK", "MEDIUM", null, null, ""));
        String response = mapper().writeValueAsString(expected);
        when(idempotency.find(any(), eq("retry-1"))).thenReturn(java.util.Optional.of(
                new IdempotencyRecord("scope", "retry-1", requestHash(rows, false), "COMPLETED", response, Instant.now().plusSeconds(60))));
        MigrationManagementService replayService = new MigrationManagementService(
                store, workManagement, new FeatureEntitlementService(entitlements), authorization,
                idempotency, mapper(), Duration.ofMinutes(10), Duration.ofHours(24));
        when(entitlements.isEnabled(any(), eq("MIGRATION_IMPORT"))).thenReturn(true);

        ImportJobSummary actual = replayService.importWorkItems(
                UUID.randomUUID(), UUID.randomUUID(), rows, false, "retry-1");
        assertEquals(expected, actual);
        verifyNoInteractions(store, workManagement);
    }

    @Test
    void rejectsImportIdempotencyKeyReuseWithDifferentPayload() {
        IdempotencyStore idempotency = mock(IdempotencyStore.class);
        when(entitlements.isEnabled(any(), eq("MIGRATION_IMPORT"))).thenReturn(true);
        when(idempotency.find(any(), eq("retry-2"))).thenReturn(java.util.Optional.of(
                new IdempotencyRecord("scope", "retry-2", "different-request", "COMPLETED", "{}", Instant.now().plusSeconds(60))));
        MigrationManagementService replayService = new MigrationManagementService(
                store, workManagement, new FeatureEntitlementService(entitlements), authorization,
                idempotency, mapper(), Duration.ofMinutes(10), Duration.ofHours(24));

        assertThrows(com.companyos.migration.MigrationIdempotencyConflictException.class, () -> replayService.importWorkItems(
                UUID.randomUUID(), UUID.randomUUID(), List.of(
                        new WorkItemImportRow("legacy-2", null, null, null, "Second", "", "TASK", "MEDIUM", null, null, "")),
                false, "retry-2"));
        verifyNoInteractions(store, workManagement);
    }

    private static String requestHash(List<WorkItemImportRow> rows, boolean dryRun) throws Exception {
        String material = mapper().writeValueAsString(Map.of("dryRun", dryRun, "rows", rows));
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8)));
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static ImportJobSummary job(UUID id, String status) {
        Instant now = Instant.now();
        return new ImportJobSummary(id, "WORK_ITEMS", status, 2, 0, 0, 0, 0, "", UUID.randomUUID(), now, now, null);
    }
}
