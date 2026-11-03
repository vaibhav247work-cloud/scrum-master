package com.companyos.dependency;

import java.time.Instant;
import java.util.UUID;

public record DependencySummary(UUID dependencyId, UUID blockedWorkItemId, UUID dependsOnWorkItemId,
                                String dependencyType, String status, Instant createdAt) {
}
