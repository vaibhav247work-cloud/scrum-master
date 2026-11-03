package com.companyos.backend.migration;

import com.companyos.migration.ImportJobSummary;
import com.companyos.migration.MigrationManagementService;
import com.companyos.migration.WorkItemImportRow;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/migrations")
public class MigrationController {
    private final MigrationManagementService service;

    public MigrationController(MigrationManagementService service) { this.service = service; }

    @PostMapping("/work-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportJobSummary importWorkItems(@Valid @RequestBody ImportRequest request,
                                             @RequestParam(defaultValue = "false") boolean dryRun,
                                             @RequestHeader(value = "Idempotency-Key") String idempotencyKey) {
        var context = TenantContextHolder.required();
        List<WorkItemImportRow> rows = request.rows().stream().map(row -> new WorkItemImportRow(
                row.sourceKey(), row.projectId(), row.sprintId(), row.assigneeUserId(), row.title(),
                row.description(), row.workType(), row.priority(), row.teamId(), row.estimateMinutes(),
                row.acceptanceCriteria())).toList();
        return service.importWorkItems(context.tenantId(), context.userId(), rows, dryRun, idempotencyKey);
    }

    @GetMapping("/jobs/{jobId}")
    public ImportJobSummary get(@PathVariable UUID jobId) {
        return service.get(TenantContextHolder.required().tenantId(), jobId);
    }

    public record ImportRequest(@NotEmpty @Size(max = 500) List<@Valid WorkItemRowRequest> rows) { }

    public record WorkItemRowRequest(@Size(max = 100) String sourceKey,
                                     UUID projectId,
                                     UUID sprintId,
                                     UUID assigneeUserId,
                                     @Size(max = 240) String title,
                                     @Size(max = 4000) String description,
                                     @Size(max = 32) String workType,
                                     @Size(max = 32) String priority,
                                     UUID teamId,
                                     Integer estimateMinutes,
                                     @Size(max = 8000) String acceptanceCriteria) { }
}
