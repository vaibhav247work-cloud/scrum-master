package com.companyos.backend.project;

import com.companyos.project.CreateProjectCommand;
import com.companyos.project.ProjectManagementService;
import com.companyos.project.ProjectSummary;
import com.companyos.project.ProjectTeamSummary;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectManagementService service;

    public ProjectController(ProjectManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectSummary> list() {
        var context = TenantContextHolder.required();
        return service.list(context.tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectSummary create(@Valid @RequestBody CreateProjectRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), context.userId(),
                new CreateProjectCommand(request.projectKey(), request.name(), request.description(), request.productId()));
    }

    @GetMapping("/{projectId}/teams")
    public List<ProjectTeamSummary> listTeams(@PathVariable UUID projectId) {
        return service.listTeams(TenantContextHolder.required().tenantId(), projectId);
    }

    @PostMapping("/{projectId}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectTeamSummary addTeam(@PathVariable UUID projectId, @RequestBody ProjectTeamRequest request) {
        var context = TenantContextHolder.required();
        return service.addTeam(context.tenantId(), projectId, request.teamId(), context.userId(), request.relationshipRole());
    }

    @DeleteMapping("/{projectId}/teams/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTeam(@PathVariable UUID projectId, @PathVariable UUID teamId) {
        var context = TenantContextHolder.required();
        service.removeTeam(context.tenantId(), projectId, teamId, context.userId());
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 20) String projectKey,
            @NotBlank @Size(max = 240) String name,
            @Size(max = 4000) String description,
            UUID productId) {
    }

    public record ProjectTeamRequest(UUID teamId, String relationshipRole) { }
}
