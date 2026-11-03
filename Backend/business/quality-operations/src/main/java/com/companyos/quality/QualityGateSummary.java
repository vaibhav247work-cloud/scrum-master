package com.companyos.quality;

import java.time.Instant;
import java.util.UUID;

public record QualityGateSummary(UUID id, UUID projectId, UUID releaseId, String gateKey, String gateType,
                                 String status, boolean required, String evidence, UUID decidedBy,
                                 Instant decidedAt, Instant createdAt, Instant updatedAt) { }
