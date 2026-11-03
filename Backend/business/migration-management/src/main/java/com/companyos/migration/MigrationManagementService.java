package com.companyos.migration;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.idempotency.IdempotencyRecord;
import com.companyos.idempotency.IdempotencyStore;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.work.CreateWorkItemCommand;
import com.companyos.work.WorkItemSummary;
import com.companyos.work.WorkManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MigrationManagementService {
    private static final int MAX_ROWS = 500;
    private static final Set<String> WORK_TYPES = Set.of("TASK", "BUG", "STORY", "DAILY_TASK", "SIDE_TASK");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private final MigrationManagementStore store;
    private final WorkManagementService workManagement;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final Duration processingTtl;
    private final Duration completedTtl;

    @Autowired
    public MigrationManagementService(MigrationManagementStore store, WorkManagementService workManagement,
                                      FeatureEntitlementService features, AuthorizationService authorization,
                                      IdempotencyStore idempotencyStore, ObjectMapper objectMapper,
                                      @Value("${idempotency.processing-ttl:PT10M}") Duration processingTtl,
                                      @Value("${idempotency.completed-ttl:PT24H}") Duration completedTtl) {
        this.store = store;
        this.workManagement = workManagement;
        this.features = features;
        this.authorization = authorization;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.processingTtl = processingTtl;
        this.completedTtl = completedTtl;
    }

    /** Compatibility constructor for isolated business-unit tests that do not exercise Redis. */
    public MigrationManagementService(MigrationManagementStore store, WorkManagementService workManagement,
                                      FeatureEntitlementService features, AuthorizationService authorization) {
        this(store, workManagement, features, authorization, null, new ObjectMapper(),
                Duration.ofMinutes(10), Duration.ofHours(24));
    }

    @Transactional
    public ImportJobSummary importWorkItems(UUID tenantId, UUID actorId, List<WorkItemImportRow> rows, boolean dryRun) {
        return importWorkItems(tenantId, actorId, rows, dryRun, null);
    }

    @Transactional
    public ImportJobSummary importWorkItems(UUID tenantId, UUID actorId, List<WorkItemImportRow> rows,
                                            boolean dryRun, String idempotencyKey) {
        requireImport(tenantId);
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("rows must contain at least one item");
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("rows cannot exceed " + MAX_ROWS + " items");
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (idempotencyStore != null && key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for imports");
        }
        String scope = tenantId + ":" + actorId + ":WORK_ITEM_IMPORT";
        String requestHash = requestHash(rows, dryRun);
        if (idempotencyStore != null) {
            IdempotencyRecord existing = idempotencyStore.find(scope, key).orElse(null);
            if (existing != null) return replayOrReject(existing, requestHash);
            if (!idempotencyStore.reserve(scope, key, requestHash, processingTtl)) {
                existing = idempotencyStore.find(scope, key).orElse(null);
                if (existing == null) throw new MigrationIdempotencyInProgressException();
                return replayOrReject(existing, requestHash);
            }
        }
        try {
            ImportJobSummary job = store.start(tenantId, actorId, "WORK_ITEMS", rows.size(), Instant.now());
            List<ImportRowError> errors = validate(rows);
            if (!errors.isEmpty()) {
                store.reject(tenantId, job.id(), errors, Instant.now());
                return completeIdempotency(scope, key, requestHash, store.find(tenantId, job.id()).orElseThrow());
            }
            if (dryRun) {
                store.validated(tenantId, job.id(), rows.size(), Instant.now());
                return completeIdempotency(scope, key, requestHash, store.find(tenantId, job.id()).orElseThrow());
            }
            int accepted = 0;
            for (WorkItemImportRow row : rows) {
                WorkItemSummary ignored = workManagement.create(tenantId, actorId, new CreateWorkItemCommand(
                        row.projectId(), row.sprintId(), row.assigneeUserId(), row.title().trim(),
                        text(row.description()), normalize(row.workType(), "TASK"), normalize(row.priority(), "MEDIUM"),
                        row.teamId(), row.estimateMinutes(), null, text(row.acceptanceCriteria())));
                accepted++;
                store.progress(tenantId, job.id(), accepted, accepted, Instant.now());
            }
            store.complete(tenantId, job.id(), accepted, Instant.now());
            return completeIdempotency(scope, key, requestHash, store.find(tenantId, job.id()).orElseThrow());
        } catch (RuntimeException exception) {
            releaseReservation(scope, key, requestHash);
            throw exception;
        } catch (Exception exception) {
            releaseReservation(scope, key, requestHash);
            throw new IllegalStateException("Unable to store idempotent import response", exception);
        }
    }

    private ImportJobSummary completeIdempotency(String scope, String key, String requestHash, ImportJobSummary result) {
        if (idempotencyStore == null) return result;
        try {
            String responseJson = objectMapper.writeValueAsString(result);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { idempotencyStore.complete(scope, key, requestHash, responseJson, completedTtl); }
                    @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) releaseReservation(scope, key, requestHash); }
                });
            } else {
                idempotencyStore.complete(scope, key, requestHash, responseJson, completedTtl);
            }
            return result;
        } catch (Exception exception) {
            releaseReservation(scope, key, requestHash);
            throw new IllegalStateException("Unable to store idempotent import response", exception);
        }
    }

    private ImportJobSummary replayOrReject(IdempotencyRecord existing, String requestHash) {
        if (!requestHash.equals(existing.requestHash())) throw new MigrationIdempotencyConflictException();
        if (existing.processing() || !existing.completed() || existing.responseJson() == null) {
            throw new MigrationIdempotencyInProgressException();
        }
        try { return objectMapper.readValue(existing.responseJson(), ImportJobSummary.class); }
        catch (Exception exception) { throw new IllegalStateException("Unable to replay idempotent import response", exception); }
    }

    private void releaseReservation(String scope, String key, String requestHash) {
        if (idempotencyStore == null) return;
        try { idempotencyStore.release(scope, key, requestHash); } catch (RuntimeException ignored) { }
    }

    private static String normalizeIdempotencyKey(String key) {
        String normalized = key == null ? "" : key.trim();
        if (normalized.isEmpty() || normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            if (key == null) return "";
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 128 letters, numbers, '.', '_', ':' or '-'");
        }
        return normalized;
    }

    private String requestHash(List<WorkItemImportRow> rows, boolean dryRun) {
        try {
            String material = objectMapper.writeValueAsString(Map.of("dryRun", dryRun, "rows", rows));
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("Unable to hash import request", exception); }
    }

    @Transactional(readOnly = true)
    public ImportJobSummary get(UUID tenantId, UUID jobId) {
        requireImportRead(tenantId);
        if (jobId == null) throw new IllegalArgumentException("jobId is required");
        return store.find(tenantId, jobId).orElseThrow(() -> new IllegalArgumentException("import job was not found"));
    }

    private List<ImportRowError> validate(List<WorkItemImportRow> rows) {
        List<ImportRowError> errors = new ArrayList<>();
        Set<String> sourceKeys = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            WorkItemImportRow row = rows.get(index);
            int rowNumber = index + 1;
            if (row == null) { errors.add(error(rowNumber, "", "row", "REQUIRED", "row is required")); continue; }
            String sourceKey = text(row.sourceKey());
            if (!sourceKey.isBlank() && !sourceKeys.add(sourceKey.toUpperCase(Locale.ROOT))) {
                errors.add(error(rowNumber, sourceKey, "sourceKey", "DUPLICATE", "sourceKey is duplicated in this import"));
            }
            if (text(row.title()).isBlank()) errors.add(error(rowNumber, sourceKey, "title", "REQUIRED", "title is required"));
            if (row.title() != null && row.title().trim().length() > 240) errors.add(error(rowNumber, sourceKey, "title", "MAX_LENGTH", "title must be at most 240 characters"));
            if (row.sprintId() != null && row.projectId() == null) errors.add(error(rowNumber, sourceKey, "projectId", "REQUIRED", "projectId is required when sprintId is supplied"));
            if (row.estimateMinutes() != null && (row.estimateMinutes() < 0 || row.estimateMinutes() > 10080)) errors.add(error(rowNumber, sourceKey, "estimateMinutes", "RANGE", "estimateMinutes must be between 0 and 10080"));
            String type = normalize(row.workType(), "TASK");
            if (!WORK_TYPES.contains(type)) errors.add(error(rowNumber, sourceKey, "workType", "INVALID", "workType is invalid"));
            String priority = normalize(row.priority(), "MEDIUM");
            if (!PRIORITIES.contains(priority)) errors.add(error(rowNumber, sourceKey, "priority", "INVALID", "priority is invalid"));
            if (text(row.description()).length() > 4000) errors.add(error(rowNumber, sourceKey, "description", "MAX_LENGTH", "description must be at most 4000 characters"));
            if (text(row.acceptanceCriteria()).length() > 8000) errors.add(error(rowNumber, sourceKey, "acceptanceCriteria", "MAX_LENGTH", "acceptanceCriteria must be at most 8000 characters"));
        }
        return errors;
    }

    private void requireImport(UUID tenantId) {
        features.requireEnabled(tenantId, "MIGRATION_IMPORT");
        authorization.requirePermission("migration.execute");
    }

    private void requireImportRead(UUID tenantId) {
        features.requireEnabled(tenantId, "MIGRATION_IMPORT");
        authorization.requirePermission("migration.read");
    }

    private static ImportRowError error(int row, String sourceKey, String field, String code, String message) {
        return new ImportRowError(row, sourceKey, field, code, message);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
