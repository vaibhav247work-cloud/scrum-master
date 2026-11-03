package com.companyos.backend.requirements;

import com.companyos.requirements.CreateRequirementCommand;
import com.companyos.requirements.RequirementSummary;
import com.companyos.requirements.RequirementsReadinessService;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requirements")
public class RequirementsController {
    private final RequirementsReadinessService service;
    public RequirementsController(RequirementsReadinessService service) { this.service = service; }

    @GetMapping
    public List<RequirementSummary> list(@RequestParam(required = false) String status) {
        return service.list(TenantContextHolder.required().tenantId(), status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementSummary create(@Valid @RequestBody RequirementRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), context.userId(), new CreateRequirementCommand(
                request.requirementKey(), request.demandId(), request.title(), request.description(), request.businessContext(),
                request.uxContext(), request.technicalContext(), request.acceptanceCriteria()));
    }

    @PostMapping("/{requirementId}/checks")
    public RequirementSummary check(@PathVariable UUID requirementId, @Valid @RequestBody CheckRequest request) {
        var context = TenantContextHolder.required();
        return service.updateCheck(context.tenantId(), requirementId, request.checkKey(), request.label(), request.required(),
                request.completed(), request.evidence(), context.userId());
    }

    @PostMapping("/{requirementId}/decision")
    public RequirementSummary decision(@PathVariable UUID requirementId, @Valid @RequestBody DecisionRequest request) {
        var context = TenantContextHolder.required();
        return service.decide(context.tenantId(), requirementId, context.userId(), request.status(), request.note());
    }

    public record RequirementRequest(@NotBlank @Size(max = 32) String requirementKey, UUID demandId,
                                     @NotBlank @Size(max = 240) String title, @Size(max = 8000) String description,
                                     @Size(max = 8000) String businessContext, @Size(max = 8000) String uxContext,
                                     @Size(max = 8000) String technicalContext, @Size(max = 8000) String acceptanceCriteria) { }
    public record CheckRequest(@NotBlank @Size(max = 64) String checkKey, @NotBlank @Size(max = 240) String label,
                               boolean required, boolean completed, @Size(max = 2000) String evidence) { }
    public record DecisionRequest(@NotBlank @Size(max = 24) String status, @Size(max = 2000) String note) { }
}
