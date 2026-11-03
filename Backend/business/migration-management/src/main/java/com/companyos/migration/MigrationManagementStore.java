package com.companyos.migration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationManagementStore {
    ImportJobSummary start(UUID tenantId, UUID actorId, String jobType, int totalRows, Instant now);
    void reject(UUID tenantId, UUID jobId, List<ImportRowError> errors, Instant now);
    void progress(UUID tenantId, UUID jobId, int processedRows, int acceptedRows, Instant now);
    void validated(UUID tenantId, UUID jobId, int totalRows, Instant now);
    void complete(UUID tenantId, UUID jobId, int acceptedRows, Instant now);
    Optional<ImportJobSummary> find(UUID tenantId, UUID jobId);
}
