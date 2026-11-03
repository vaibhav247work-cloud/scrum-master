package com.companyos.product;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductSummary(UUID id, String productKey, String name, String description, String status,
                             List<String> featureKeys, int memberCount, Instant createdAt, Instant updatedAt) {
}
