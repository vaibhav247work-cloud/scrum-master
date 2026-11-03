package com.companyos.backend.tenant;

import com.companyos.identity.access.TenantUserDirectory;
import com.companyos.identity.access.TenantUserSummary;
import com.companyos.identity.access.InviteUserCommand;
import com.companyos.identity.access.TenantRoleSummary;
import com.companyos.identity.access.TenantUserAdministrationService;
import com.companyos.identity.access.TenantUserInvitationResult;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.tenantcontext.TenantContext;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/users")
public class TenantUserController {
    private final AuthorizationService authorizationService;
    private final TenantUserDirectory userDirectory;
    private final TenantUserAdministrationService administrationService;
    private final boolean exposeInvitationToken;

    public TenantUserController(
            AuthorizationService authorizationService,
            TenantUserDirectory userDirectory,
            TenantUserAdministrationService administrationService,
            @Value("${security.invitation.expose-token:false}") boolean exposeInvitationToken) {
        this.authorizationService = authorizationService;
        this.userDirectory = userDirectory;
        this.administrationService = administrationService;
        this.exposeInvitationToken = exposeInvitationToken;
    }

    @GetMapping
    public List<UserResponse> listUsers() {
        authorizationService.requirePermission("tenant.user.read");
        return userDirectory.findByTenantId(TenantContextHolder.required().tenantId())
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/roles")
    public List<RoleResponse> listRoles() {
        authorizationService.requirePermission("tenant.user.manage");
        return administrationService.roles(TenantContextHolder.required().tenantId()).stream()
                .map(RoleResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@Valid @RequestBody InviteRequest request) {
        authorizationService.requirePermission("tenant.user.manage");
        TenantContext context = TenantContextHolder.required();
        TenantUserInvitationResult result = administrationService.invite(
                context.tenantId(), context.userId(),
                new InviteUserCommand(request.email(), request.displayName(), request.roleKeys()));
        return InvitationResponse.from(result, exposeInvitationToken);
    }

    @PutMapping("/{userId}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceRoles(@PathVariable UUID userId, @Valid @RequestBody RoleUpdateRequest request) {
        authorizationService.requirePermission("tenant.user.manage");
        administrationService.replaceRoles(TenantContextHolder.required().tenantId(), userId, request.roleKeys());
    }

    @PatchMapping("/{userId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@PathVariable UUID userId, @Valid @RequestBody StatusUpdateRequest request) {
        authorizationService.requirePermission("tenant.user.manage");
        TenantContext context = TenantContextHolder.required();
        administrationService.changeStatus(context.tenantId(), context.userId(), userId, request.status());
    }

    public record UserResponse(String userId, String email, String displayName, String status) {
        static UserResponse from(TenantUserSummary user) {
            return new UserResponse(
                    user.userId().toString(), user.email(), user.displayName(), user.status());
        }
    }

    public record RoleResponse(String roleId, String roleKey, String displayName, boolean systemRole) {
        static RoleResponse from(TenantRoleSummary role) {
            return new RoleResponse(role.roleId().toString(), role.roleKey(), role.displayName(), role.systemRole());
        }
    }

    public record InviteRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 20) Set<@NotBlank @Size(max = 100) String> roleKeys) {
    }

    public record RoleUpdateRequest(@Size(min = 1, max = 20) Set<@NotBlank @Size(max = 100) String> roleKeys) {
    }

    public record StatusUpdateRequest(@NotBlank @Size(max = 32) String status) {
    }

    public record InvitationResponse(
            String invitationId,
            String userId,
            String email,
            String status,
            java.time.Instant expiresAt,
            Set<String> roleKeys,
            String activationToken) {
        static InvitationResponse from(TenantUserInvitationResult result, boolean exposeToken) {
            return new InvitationResponse(result.invitationId().toString(), result.userId().toString(),
                    result.email(), "INVITED", result.expiresAt(), result.roleKeys(),
                    exposeToken ? result.rawToken() : null);
        }
    }
}
