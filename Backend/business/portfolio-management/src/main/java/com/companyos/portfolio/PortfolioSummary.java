package com.companyos.portfolio;

import java.time.Instant;
import java.util.UUID;

public record PortfolioSummary(UUID id, String portfolioKey, String name, String description, String status,
                               UUID ownerUserId, Instant createdAt, Instant updatedAt) { }
