package com.companyos.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummary(
        UUID id,
        String projectKey,
        String name,
        String description,
        String status,
        Instant createdAt,
        UUID productId) {
    public ProjectSummary(UUID id, String projectKey, String name, String description, String status, Instant createdAt) {
        this(id, projectKey, name, description, status, createdAt, null);
    }
}
