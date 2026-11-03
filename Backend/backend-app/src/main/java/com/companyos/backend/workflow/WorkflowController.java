package com.companyos.backend.workflow;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.workflow.CreateWorkflowCommand;
import com.companyos.workflow.WorkflowManagementService;
import com.companyos.workflow.WorkflowStatusDefinition;
import com.companyos.workflow.WorkflowSummary;
import com.companyos.workflow.WorkflowTransitionDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowManagementService service;

    public WorkflowController(WorkflowManagementService service) { this.service = service; }

    @GetMapping
    public List<WorkflowSummary> list() {
        return service.list(TenantContextHolder.required().tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowSummary create(@Valid @RequestBody WorkflowRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), context.userId(), new CreateWorkflowCommand(
                request.workflowKey(), request.name(), request.methodology(),
                request.statuses().stream().map(status -> new WorkflowStatusDefinition(
                        status.key(), status.label(), status.terminal(), status.position())).toList(),
                request.transitions() == null ? List.of() : request.transitions().stream()
                        .map(transition -> new WorkflowTransitionDefinition(transition.fromStatus(), transition.toStatus())).toList()));
    }

    @PutMapping("/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable UUID projectId, @Valid @RequestBody AssignWorkflowRequest request) {
        service.assignToProject(TenantContextHolder.required().tenantId(), projectId, request.workflowId());
    }

    public record WorkflowRequest(
            @NotBlank @Size(max = 32) String workflowKey,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 16) String methodology,
            @NotNull @Size(min = 2, max = 24) List<StatusRequest> statuses,
            @Size(max = 200) List<TransitionRequest> transitions) { }

    public record StatusRequest(@NotBlank @Size(max = 32) String key,
                                @NotBlank @Size(max = 120) String label,
                                boolean terminal,
                                int position) { }

    public record TransitionRequest(@NotBlank @Size(max = 32) String fromStatus,
                                    @NotBlank @Size(max = 32) String toStatus) { }

    public record AssignWorkflowRequest(@NotNull UUID workflowId) { }
}
