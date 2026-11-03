package com.companyos.migration;

import java.time.Instant;
import java.util.UUID;

public record ImportJobSummary(UUID id, String jobType, String status, int totalRows, int processedRows,
                               int acceptedRows, int rejectedRows, int errorCount, String failureReason,
                               UUID requestedBy, Instant createdAt, Instant updatedAt, Instant completedAt) { }
