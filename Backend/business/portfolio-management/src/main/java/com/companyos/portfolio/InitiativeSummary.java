package com.companyos.portfolio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InitiativeSummary(UUID id, UUID portfolioId, UUID programId, UUID productId, String initiativeKey,
                                String title, String description, String priority, String status,
                                LocalDate targetDate, UUID ownerUserId, Instant createdAt, Instant updatedAt) { }
