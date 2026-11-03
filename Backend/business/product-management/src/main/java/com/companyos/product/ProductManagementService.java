package com.companyos.product;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProductManagementService {
    private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,47}");
    private static final Set<String> ACCESS_STATUSES = Set.of("ACTIVE", "REVOKED");
    private final ProductManagementStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public ProductManagementService(ProductManagementStore store, FeatureEntitlementService features,
                                    AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<ProductSummary> list(UUID tenantId) {
        requireFeature(tenantId);
        authorization.requirePermission("product.read");
        return store.findAll(tenantId);
    }

    @Transactional
    public ProductSummary create(UUID tenantId, UUID actorId, CreateProductCommand command) {
        requireFeature(tenantId);
        authorization.requirePermission("product.manage");
        if (command == null) throw new IllegalArgumentException("request body is required");
        String key = normalizeKey(command.productKey());
        String name = required(command.name(), "name", 200);
        String description = bounded(command.description(), "description", 4000);
        return store.create(tenantId, actorId, new CreateProductCommand(key, name, description, normalizeFeatures(command.featureKeys())), Instant.now());
    }

    @Transactional
    public ProductSummary updateFeatures(UUID tenantId, UUID productId, Set<String> featureKeys) {
        requireFeature(tenantId);
        authorization.requirePermission("product.manage");
        if (productId == null) throw new IllegalArgumentException("productId is required");
        return store.updateFeatures(tenantId, productId, normalizeFeatures(featureKeys), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ProductMemberSummary> members(UUID tenantId, UUID productId) {
        requireFeature(tenantId);
        authorization.requirePermission("product.read");
        if (productId == null) throw new IllegalArgumentException("productId is required");
        return store.findMembers(tenantId, productId);
    }

    @Transactional
    public ProductMemberSummary setMember(UUID tenantId, UUID productId, UUID userId, String accessStatus,
                                         String productRole, UUID actorId) {
        requireFeature(tenantId);
        authorization.requirePermission("product.manage");
        if (productId == null) throw new IllegalArgumentException("productId is required");
        if (userId == null) throw new IllegalArgumentException("userId is required");
        String status = accessStatus == null ? "ACTIVE" : accessStatus.trim().toUpperCase(Locale.ROOT);
        if (!ACCESS_STATUSES.contains(status)) throw new IllegalArgumentException("accessStatus is invalid");
        String role = bounded(productRole, "productRole", 80);
        return store.setMember(tenantId, productId, userId, status, role, actorId, Instant.now());
    }

    private void requireFeature(UUID tenantId) { features.requireEnabled(tenantId, "PRODUCT_MANAGEMENT"); }

    private static String normalizeKey(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!KEY.matcher(normalized).matches()) throw new IllegalArgumentException("productKey is invalid");
        return normalized;
    }

    private static Set<String> normalizeFeatures(Set<String> values) {
        if (values == null) return Set.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String item = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (item.isBlank() || item.length() > 100 || !item.matches("[A-Z][A-Z0-9_]{1,99}")) {
                throw new IllegalArgumentException("featureKeys contains an invalid feature key");
            }
            normalized.add(item);
        }
        return Set.copyOf(normalized);
    }

    private static String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }

    private static String bounded(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }
}
