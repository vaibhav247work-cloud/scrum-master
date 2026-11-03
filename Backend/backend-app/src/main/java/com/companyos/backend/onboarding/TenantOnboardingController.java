package com.companyos.backend.onboarding;

import com.companyos.backend.security.PublicEndpointRateLimiter;
import com.companyos.tenantmanagement.api.TenantOnboardingCommand;
import com.companyos.tenantmanagement.api.TenantOnboardingResult;
import com.companyos.tenantmanagement.api.TenantOnboardingService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/onboarding")
public class TenantOnboardingController {
    private final TenantOnboardingService onboardingService;
    private final PublicEndpointRateLimiter rateLimiter;

    public TenantOnboardingController(
            TenantOnboardingService onboardingService, PublicEndpointRateLimiter rateLimiter) {
        this.onboardingService = onboardingService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantOnboardingResponse onboard(
            @Valid @RequestBody TenantOnboardingRequest request, HttpServletRequest httpRequest) {
        rateLimiter.checkOnboarding(httpRequest);
        TenantOnboardingResult result = onboardingService.onboard(new TenantOnboardingCommand(
                request.tenantKey(),
                request.legalName(),
                request.displayName(),
                request.timezone(),
                request.ownerEmail(),
                request.ownerDisplayName(),
                request.ownerPassword(),
                request.enabledFeatures()));
        return TenantOnboardingResponse.from(result);
    }

    public record TenantOnboardingRequest(
            @NotBlank @Size(max = 80) String tenantKey,
            @NotBlank @Size(max = 240) String legalName,
            @NotBlank @Size(max = 240) String displayName,
            @NotBlank @Size(max = 80) String timezone,
            @NotBlank @Email @Size(max = 320) String ownerEmail,
            @NotBlank @Size(max = 200) String ownerDisplayName,
            @NotBlank @Size(min = 12, max = 128) String ownerPassword,
            Set<@NotBlank @Size(max = 100) String> enabledFeatures) {
    }

    public record TenantOnboardingResponse(
            String tenantId,
            String ownerId,
            String tenantKey,
            String ownerEmail,
            Set<String> enabledFeatures,
            String tenantStatus,
            String ownerStatus) {
        static TenantOnboardingResponse from(TenantOnboardingResult result) {
            return new TenantOnboardingResponse(
                    result.tenantId().toString(),
                    result.ownerId().toString(),
                    result.tenantKey(),
                    result.ownerEmail(),
                    result.enabledFeatures(),
                    result.tenantStatus(),
                    result.ownerStatus());
        }
    }
}
