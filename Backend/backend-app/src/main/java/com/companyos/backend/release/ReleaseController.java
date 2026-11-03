package com.companyos.backend.release;

import com.companyos.release.CreateReleaseCommand;
import com.companyos.release.ReleaseManagementService;
import com.companyos.release.ReleaseSummary;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/releases")
public class ReleaseController {
    private final ReleaseManagementService service;

    public ReleaseController(ReleaseManagementService service) { this.service = service; }

    @GetMapping
    public List<ReleaseSummary> list(@PathVariable UUID projectId) {
        return service.list(TenantContextHolder.required().tenantId(), projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseSummary create(@PathVariable UUID projectId, @Valid @RequestBody CreateReleaseRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), projectId, context.userId(),
                new CreateReleaseCommand(request.releaseKey(), request.name(), request.description(), request.targetDate()));
    }

    @PostMapping("/{releaseId}/work-items/{workItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addWorkItem(@PathVariable UUID releaseId, @PathVariable UUID workItemId) {
        var context = TenantContextHolder.required();
        service.addWorkItem(context.tenantId(), context.userId(), releaseId, workItemId);
    }

    public record CreateReleaseRequest(@NotBlank @Size(max = 40) String releaseKey,
                                       @NotBlank @Size(max = 240) String name,
                                       @Size(max = 4000) String description,
                                       LocalDate targetDate) { }
}
