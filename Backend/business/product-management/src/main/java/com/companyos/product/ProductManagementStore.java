package com.companyos.product;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProductManagementStore {
    List<ProductSummary> findAll(UUID tenantId);
    ProductSummary create(UUID tenantId, UUID actorId, CreateProductCommand command, Instant now);
    ProductSummary updateFeatures(UUID tenantId, UUID productId, Set<String> featureKeys, Instant now);
    List<ProductMemberSummary> findMembers(UUID tenantId, UUID productId);
    ProductMemberSummary setMember(UUID tenantId, UUID productId, UUID userId, String accessStatus,
                                   String productRole, UUID actorId, Instant now);
}
