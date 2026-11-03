package com.companyos.backend.tenant;

import com.companyos.security.contract.AuthorizationService;
import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.tenantmanagement.api.TenantLifecycleService;
import com.companyos.tenantmanagement.api.TenantLifecycleSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant/lifecycle")
public class TenantLifecycleController {
    private final AuthorizationService authorization;
    private final TenantLifecycleService service;

    public TenantLifecycleController(AuthorizationService authorization, TenantLifecycleService service) {
        this.authorization = authorization;
        this.service = service;
    }

    @GetMapping
    public TenantLifecycleSummary status() {
        authorization.requirePermission("tenant.lifecycle.read");
        return service.status(TenantContextHolder.required().tenantId());
    }

    @PostMapping("/status")
    public TenantLifecycleSummary changeStatus(@Valid @RequestBody StatusRequest request) {
        authorization.requirePermission("tenant.lifecycle.manage");
        var context = TenantContextHolder.required();
        return service.changeStatus(context.tenantId(), context.userId(), request.status(), request.reason());
    }

    @GetMapping("/export")
    public Map<String, Object> export() {
        authorization.requirePermission("tenant.data.export");
        return service.export(TenantContextHolder.required().tenantId());
    }

    public record StatusRequest(@NotBlank @Size(max = 32) String status, @Size(max = 500) String reason) { }
}
