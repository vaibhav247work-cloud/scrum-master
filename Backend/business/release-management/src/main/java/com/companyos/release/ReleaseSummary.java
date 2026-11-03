package com.companyos.release;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReleaseSummary(UUID releaseId, UUID projectId, String releaseKey, String name, String description,
                             LocalDate targetDate, String status, Instant createdAt) {
}
