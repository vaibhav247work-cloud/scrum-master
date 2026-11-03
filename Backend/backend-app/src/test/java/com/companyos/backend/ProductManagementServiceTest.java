package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.product.CreateProductCommand;
import com.companyos.product.ProductManagementService;
import com.companyos.product.ProductManagementStore;
import com.companyos.product.ProductSummary;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductManagementServiceTest {
    private final ProductManagementStore store = mock(ProductManagementStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ProductManagementService service = new ProductManagementService(
            store, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void normalizesProductAndFeatureKeysBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductSummary product = new ProductSummary(UUID.randomUUID(), "CHECKOUT", "Checkout", "Payment flow", "ACTIVE", List.of(), 0, Instant.now(), Instant.now());
        when(entitlements.isEnabled(tenantId, "PRODUCT_MANAGEMENT")).thenReturn(true);
        when(store.create(eq(tenantId), eq(actorId), any(), any())).thenReturn(product);

        assertEquals(product, service.create(tenantId, actorId,
                new CreateProductCommand(" checkout ", " Checkout ", " Payment flow ", Set.of("projects", "SCRUM"))));

        verify(store).create(eq(tenantId), eq(actorId), eq(new CreateProductCommand(
                "CHECKOUT", "Checkout", "Payment flow", Set.of("PROJECTS", "SCRUM"))), any());
    }

    @Test
    void rejectsInvalidProductKeyBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "PRODUCT_MANAGEMENT")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateProductCommand("not valid", "Product", "", Set.of())));
        verifyNoInteractions(store);
    }
}
