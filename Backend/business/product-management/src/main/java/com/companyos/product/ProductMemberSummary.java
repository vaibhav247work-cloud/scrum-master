package com.companyos.product;

import java.time.Instant;
import java.util.UUID;

public record ProductMemberSummary(UUID productId, UUID userId, String displayName, String email,
                                   String accessStatus, String productRole, Instant updatedAt) {
}
