package com.companyos.backend.organization;

import com.companyos.organization.CreateOrganizationUnitCommand;
import com.companyos.organization.CreateTeamCommand;
import com.companyos.organization.OrganizationManagementService;
import com.companyos.organization.OrganizationUnitSummary;
import com.companyos.organization.TeamMemberSummary;
import com.companyos.organization.TeamSummary;
import com.companyos.tenantcontext.TenantContext;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/tenant/organization")
public class OrganizationController {
    private final OrganizationManagementService service;

    public OrganizationController(OrganizationManagementService service) {
        this.service = service;
    }

    @GetMapping("/units")
    public List<OrganizationUnitSummary> units() {
        return service.units(context().tenantId());
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationUnitSummary createUnit(@Valid @RequestBody CreateUnitRequest request) {
        TenantContext context = context();
        return service.createUnit(context.tenantId(), context.userId(), new CreateOrganizationUnitCommand(
                request.parentUnitId(), request.unitKey(), request.name(), request.unitType(), request.managerUserId()));
    }

    @GetMapping("/teams")
    public List<TeamSummary> teams() {
        return service.teams(context().tenantId());
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamSummary createTeam(@Valid @RequestBody CreateTeamRequest request) {
        TenantContext context = context();
        return service.createTeam(context.tenantId(), context.userId(), new CreateTeamCommand(
                request.parentUnitId(), request.teamKey(), request.name(), request.managerUserId()));
    }

    @GetMapping("/teams/{teamId}/members")
    public List<TeamMemberSummary> members(@PathVariable UUID teamId) {
        return service.members(context().tenantId(), teamId);
    }

    @PostMapping("/teams/{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberSummary addMember(
            @PathVariable UUID teamId, @Valid @RequestBody AddMemberRequest request) {
        TenantContext context = context();
        return service.addMember(context.tenantId(), context.userId(), teamId,
                request.userId(), request.membershipRole());
    }

    @DeleteMapping("/teams/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID teamId, @PathVariable UUID userId) {
        TenantContext context = context();
        service.removeMember(context.tenantId(), context.userId(), teamId, userId);
    }

    private static TenantContext context() {
        return TenantContextHolder.required();
    }

    public record CreateUnitRequest(
            UUID parentUnitId,
            @NotBlank @Size(max = 80) String unitKey,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 32) String unitType,
            UUID managerUserId) {
    }

    public record CreateTeamRequest(
            UUID parentUnitId,
            @NotBlank @Size(max = 80) String teamKey,
            @NotBlank @Size(max = 200) String name,
            UUID managerUserId) {
    }

    public record AddMemberRequest(@NotNull UUID userId, @NotBlank @Size(max = 32) String membershipRole) {
    }
}
