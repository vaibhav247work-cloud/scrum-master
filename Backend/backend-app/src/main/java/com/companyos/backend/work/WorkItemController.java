package com.companyos.backend.work;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.work.CreateWorkItemCommand;
import com.companyos.work.WorkItemFilter;
import com.companyos.work.WorkItemSummary;
import com.companyos.work.WorkItemAssignmentHistory;
import com.companyos.work.WorkScopeChangeRequest;
import com.companyos.work.CreateWorkScopeChangeCommand;
import com.companyos.work.WorkManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/work-items")
public class WorkItemController {
    private final WorkManagementService service;
    private final AuthorizationService authorization;

    public WorkItemController(WorkManagementService service, AuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping
    public List<WorkItemSummary> find(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false) UUID teamId) {
        var context = TenantContextHolder.required();
        return service.find(context.tenantId(), new WorkItemFilter(projectId, sprintId, assigneeUserId, teamId));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) UUID assigneeUserId,
            @RequestParam(required = false) UUID teamId) {
        authorization.requirePermission("work.export");
        var context = TenantContextHolder.required();
        List<WorkItemSummary> rows = service.find(context.tenantId(), new WorkItemFilter(projectId, sprintId, assigneeUserId, teamId));
        StringBuilder csv = new StringBuilder("work_key,project_id,sprint_id,title,work_type,priority,status,estimate_minutes,created_at\n");
        rows.forEach(row -> csv.append(csv(row.workKey())).append(',').append(csv(row.projectId())).append(',')
                .append(csv(row.sprintId())).append(',').append(csv(row.title())).append(',').append(csv(row.workType())).append(',')
                .append(csv(row.priority())).append(',').append(csv(row.status())).append(',').append(csv(row.estimateMinutes())).append(',')
                .append(csv(row.createdAt())).append('\n'));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=work-items.csv")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItemSummary create(@Valid @RequestBody CreateWorkItemRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), context.userId(), new CreateWorkItemCommand(
                request.projectId(), request.sprintId(), request.assigneeUserId(), request.title(),
                request.description(), request.workType(), request.priority(), request.teamId(), request.estimateMinutes(),
                request.parentWorkItemId(), request.acceptanceCriteria()));
    }

    @PatchMapping("/{workItemId}")
    public WorkItemSummary update(
            @PathVariable UUID workItemId,
            @Valid @RequestBody UpdateWorkItemRequest request) {
        var context = TenantContextHolder.required();
        return service.update(context.tenantId(), context.userId(), workItemId,
                new com.companyos.work.UpdateWorkItemCommand(request.title(), request.description(), request.assigneeUserId(),
                        request.workType(), request.priority(), request.status(), request.teamId(), request.assignmentReason(),
                        request.estimateMinutes(), request.parentWorkItemId(), request.acceptanceCriteria()));
    }

    @PatchMapping("/{workItemId}/rank")
    public WorkItemSummary reorder(@PathVariable UUID workItemId,
                                   @Valid @RequestBody ReorderWorkItemRequest request) {
        var context = TenantContextHolder.required();
        return service.reorder(context.tenantId(), context.userId(), workItemId, request.targetPosition());
    }

    @GetMapping("/{workItemId}/assignment-history")
    public List<WorkItemAssignmentHistory> assignmentHistory(@PathVariable UUID workItemId) {
        return service.assignmentHistory(TenantContextHolder.required().tenantId(), workItemId);
    }

    @GetMapping("/{workItemId}/scope-change-requests")
    public List<WorkScopeChangeRequest> scopeChangeRequests(@PathVariable UUID workItemId) {
        return service.scopeChangeRequests(TenantContextHolder.required().tenantId(), workItemId);
    }

    @PostMapping("/{workItemId}/scope-change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkScopeChangeRequest requestScopeChange(@PathVariable UUID workItemId,
                                                     @RequestBody ScopeChangeRequest request) {
        var context = TenantContextHolder.required();
        return service.requestScopeChange(context.tenantId(), context.userId(), new CreateWorkScopeChangeCommand(
                workItemId, request.requestedPriority(), request.targetSprintId(), request.emergency(), request.reason()));
    }

    @PostMapping("/scope-change-requests/{requestId}/decision")
    public WorkScopeChangeRequest decideScopeChange(@PathVariable UUID requestId,
                                                    @RequestBody ScopeChangeDecision request) {
        var context = TenantContextHolder.required();
        return service.decideScopeChange(context.tenantId(), context.userId(), requestId, request.approve(), request.decisionNote());
    }

    public record CreateWorkItemRequest(
            UUID projectId,
            UUID sprintId,
            UUID parentWorkItemId,
            UUID assigneeUserId,
            UUID teamId,
            @NotBlank @Size(max = 240) String title,
            @Size(max = 4000) String description,
            @Size(max = 32) String workType,
            @Size(max = 32) String priority,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(10080) Integer estimateMinutes,
            @Size(max = 8000) String acceptanceCriteria) {
    }

    public record UpdateWorkItemRequest(
            @Size(max = 240) String title,
            @Size(max = 4000) String description,
            UUID assigneeUserId,
            @Size(max = 32) String workType,
            @Size(max = 32) String priority,
            @Size(max = 32) String status,
            UUID teamId,
            @Size(max = 1000) String assignmentReason,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(10080) Integer estimateMinutes,
            UUID parentWorkItemId,
            @Size(max = 8000) String acceptanceCriteria) {
    }

    public record ReorderWorkItemRequest(
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(10000) int targetPosition) {
    }

    public record ScopeChangeRequest(String requestedPriority, UUID targetSprintId, boolean emergency,
                                     @NotBlank @Size(max = 4000) String reason) { }

    public record ScopeChangeDecision(boolean approve, @Size(max = 1000) String decisionNote) { }

    private static String csv(Object value) { return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\""; }
}
