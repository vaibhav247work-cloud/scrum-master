package com.companyos.portfolio;

import java.time.Instant;
import java.util.UUID;

public record ProgramSummary(UUID id, UUID portfolioId, String programKey, String name, String description,
                             String status, UUID ownerUserId, Instant createdAt, Instant updatedAt) { }
