package com.companyos.backend.tenant;

import com.companyos.feature.entitlement.FeatureEntitlement;
import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenant/features")
public class TenantFeatureController {
    private final AuthorizationService authorizationService;
    private final FeatureEntitlementService featureService;

    public TenantFeatureController(
            AuthorizationService authorizationService,
            FeatureEntitlementService featureService) {
        this.authorizationService = authorizationService;
        this.featureService = featureService;
    }

    @GetMapping
    public List<FeatureEntitlement> list() {
        authorizationService.requirePermission("feature.manage");
        return featureService.list(TenantContextHolder.required().tenantId());
    }

    @PutMapping("/{featureKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setEnabled(@PathVariable String featureKey, @Valid @RequestBody FeatureUpdateRequest request) {
        authorizationService.requirePermission("feature.manage");
        featureService.setEnabled(TenantContextHolder.required().tenantId(), featureKey, request.enabled());
    }

    public record FeatureUpdateRequest(@NotNull Boolean enabled) {
    }
}
