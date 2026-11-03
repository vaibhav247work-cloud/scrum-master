package com.companyos.backend.product;

import com.companyos.product.CreateProductCommand;
import com.companyos.product.ProductManagementService;
import com.companyos.product.ProductMemberSummary;
import com.companyos.product.ProductSummary;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductManagementService service;

    public ProductController(ProductManagementService service) { this.service = service; }

    @GetMapping
    public List<ProductSummary> list() { return service.list(TenantContextHolder.required().tenantId()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSummary create(@Valid @RequestBody CreateProductRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), context.userId(), new CreateProductCommand(
                request.productKey(), request.name(), request.description(), request.featureKeys()));
    }

    @PutMapping("/{productId}/features")
    public ProductSummary updateFeatures(@PathVariable UUID productId, @Valid @RequestBody FeatureUpdateRequest request) {
        return service.updateFeatures(TenantContextHolder.required().tenantId(), productId, request.featureKeys());
    }

    @GetMapping("/{productId}/members")
    public List<ProductMemberSummary> members(@PathVariable UUID productId) {
        return service.members(TenantContextHolder.required().tenantId(), productId);
    }

    @PutMapping("/{productId}/members/{userId}")
    public ProductMemberSummary setMember(@PathVariable UUID productId, @PathVariable UUID userId,
                                          @Valid @RequestBody MemberUpdateRequest request) {
        var context = TenantContextHolder.required();
        return service.setMember(context.tenantId(), productId, userId, request.accessStatus(), request.productRole(), context.userId());
    }

    public record CreateProductRequest(@NotBlank @Size(max = 48) String productKey,
                                       @NotBlank @Size(max = 200) String name,
                                       @Size(max = 4000) String description,
                                       Set<String> featureKeys) {}
    public record FeatureUpdateRequest(Set<String> featureKeys) {}
    public record MemberUpdateRequest(@Size(max = 16) String accessStatus, @Size(max = 80) String productRole) {}
}
