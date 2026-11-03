package com.companyos.backend.work;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.work.WorkManagementService;
import com.companyos.work.WorkScopeChangeRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/scope-change-requests")
public class ScopeChangeController {
    private final WorkManagementService service;

    public ScopeChangeController(WorkManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkScopeChangeRequest> pending() {
        return service.pendingScopeChangeRequests(TenantContextHolder.required().tenantId());
    }
}
