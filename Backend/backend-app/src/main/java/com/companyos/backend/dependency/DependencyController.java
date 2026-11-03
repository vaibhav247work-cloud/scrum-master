package com.companyos.backend.dependency;

import com.companyos.dependency.CreateDependencyCommand;
import com.companyos.dependency.DependencyRiskService;
import com.companyos.dependency.DependencySummary;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-items/{workItemId}/dependencies")
public class DependencyController {
    private final DependencyRiskService service;

    public DependencyController(DependencyRiskService service) { this.service = service; }

    @GetMapping
    public List<DependencySummary> list(@PathVariable UUID workItemId) {
        return service.list(TenantContextHolder.required().tenantId(), workItemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DependencySummary create(@PathVariable UUID workItemId, @Valid @RequestBody CreateDependencyRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), context.userId(), workItemId,
                new CreateDependencyCommand(request.dependsOnWorkItemId(), request.dependencyType()));
    }

    public record CreateDependencyRequest(UUID dependsOnWorkItemId, @Size(max = 32) String dependencyType) { }
}
