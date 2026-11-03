package com.companyos.backend.sprint;

import com.companyos.sprint.CreateSprintCommand;
import com.companyos.sprint.SprintManagementService;
import com.companyos.sprint.SprintSummary;
import com.companyos.sprint.SprintTeamSummary;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/sprints")
public class SprintController {
    private final SprintManagementService service;

    public SprintController(SprintManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<SprintSummary> list(@PathVariable UUID projectId) {
        return service.list(TenantContextHolder.required().tenantId(), projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SprintSummary create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSprintRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), projectId, context.userId(),
                new CreateSprintCommand(request.sprintKey(), request.name(), request.goal(),
                        request.startDate(), request.endDate()));
    }

    @GetMapping("/{sprintId}/teams")
    public List<SprintTeamSummary> listTeams(@PathVariable UUID sprintId) {
        return service.listTeams(TenantContextHolder.required().tenantId(), sprintId);
    }

    @PostMapping("/{sprintId}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public SprintTeamSummary addTeam(@PathVariable UUID sprintId, @RequestBody SprintTeamRequest request) {
        var context = TenantContextHolder.required();
        return service.addTeam(context.tenantId(), sprintId, request.teamId(), context.userId(), request.relationshipRole());
    }

    @DeleteMapping("/{sprintId}/teams/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTeam(@PathVariable UUID sprintId, @PathVariable UUID teamId) {
        var context = TenantContextHolder.required();
        service.removeTeam(context.tenantId(), sprintId, teamId, context.userId());
    }

    @GetMapping("/{sprintId}/summary")
    public com.companyos.sprint.SprintProgressSummary progress(@PathVariable UUID sprintId) {
        return service.progress(TenantContextHolder.required().tenantId(), sprintId);
    }

    @PatchMapping("/{sprintId}")
    public SprintSummary changeStatus(@PathVariable UUID sprintId, @RequestBody SprintStatusRequest request) {
        var context = TenantContextHolder.required();
        return service.changeStatus(context.tenantId(), sprintId, context.userId(), request.status(), request.completionNote());
    }

    @PostMapping("/{sprintId}/carry-over")
    public com.companyos.sprint.SprintCarryOverResult carryOver(
            @PathVariable UUID sprintId, @RequestBody SprintCarryOverRequest request) {
        var context = TenantContextHolder.required();
        return service.carryOver(context.tenantId(), sprintId, request.targetSprintId(), context.userId(), request.workItemIds());
    }

    public record CreateSprintRequest(
            @NotBlank @Size(max = 20) String sprintKey,
            @NotBlank @Size(max = 240) String name,
            @Size(max = 4000) String goal,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record SprintTeamRequest(UUID teamId, String relationshipRole) { }
    public record SprintStatusRequest(String status, String completionNote) { }
    public record SprintCarryOverRequest(UUID targetSprintId, Set<UUID> workItemIds) { }
}
