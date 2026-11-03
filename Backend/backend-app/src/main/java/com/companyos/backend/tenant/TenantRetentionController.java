package com.companyos.backend.tenant;

import com.companyos.retention.RetentionCleanupSummary;
import com.companyos.retention.RetentionHoldSummary;
import com.companyos.retention.RetentionManagementService;
import com.companyos.retention.RetentionPolicyCommand;
import com.companyos.retention.RetentionPolicySummary;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/retention-policy")
public class TenantRetentionController {
    private final AuthorizationService authorization;
    private final RetentionManagementService service;

    public TenantRetentionController(AuthorizationService authorization, RetentionManagementService service) {
        this.authorization = authorization;
        this.service = service;
    }

    @GetMapping
    public RetentionPolicySummary policy() {
        authorization.requirePermission("retention.read");
        return service.policy(TenantContextHolder.required().tenantId());
    }

    @PutMapping
    public RetentionPolicySummary save(@Valid @RequestBody RetentionRequest request) {
        authorization.requirePermission("retention.manage");
        var context = TenantContextHolder.required();
        return service.savePolicy(context.tenantId(), context.userId(), new RetentionPolicyCommand(
                request.enabled(), request.operationMode(), request.activityRetentionDays(), request.notificationRetentionDays(),
                request.deliveryRetentionDays(), request.auditRetentionDays()));
    }

    @GetMapping("/preview")
    public RetentionCleanupSummary preview() {
        authorization.requirePermission("retention.read");
        return service.preview(TenantContextHolder.required().tenantId());
    }

    @PostMapping("/cleanup")
    public RetentionCleanupSummary cleanup() {
        authorization.requirePermission("retention.execute");
        var context = TenantContextHolder.required();
        return service.cleanup(context.tenantId(), context.userId());
    }

    @GetMapping("/holds")
    public List<RetentionHoldSummary> holds() {
        authorization.requirePermission("retention.read");
        return service.holds(TenantContextHolder.required().tenantId());
    }

    @PostMapping("/holds")
    public RetentionHoldSummary createHold(@Valid @RequestBody HoldRequest request) {
        authorization.requirePermission("retention.manage");
        var context = TenantContextHolder.required();
        return service.createHold(context.tenantId(), context.userId(), request.holdKey(), request.reason());
    }

    @PostMapping("/holds/{holdId}/release")
    public RetentionHoldSummary releaseHold(@PathVariable UUID holdId) {
        authorization.requirePermission("retention.manage");
        var context = TenantContextHolder.required();
        return service.releaseHold(context.tenantId(), context.userId(), holdId);
    }

    public record RetentionRequest(
            boolean enabled,
            @NotBlank @Size(max = 32) String operationMode,
            @Min(120) @Max(3650) int activityRetentionDays,
            @Min(30) @Max(3650) int notificationRetentionDays,
            @Min(30) @Max(3650) int deliveryRetentionDays,
            @Min(365) @Max(3650) int auditRetentionDays) { }

    public record HoldRequest(@NotBlank @Size(max = 120) String holdKey,
                              @NotBlank @Size(max = 1000) String reason) { }
}
