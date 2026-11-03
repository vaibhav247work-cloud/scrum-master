package com.companyos.retention;

import java.time.Instant;
import java.util.UUID;

public record RetentionHoldSummary(UUID id, String holdKey, String reason, boolean active, Instant createdAt, Instant releasedAt) {
}
