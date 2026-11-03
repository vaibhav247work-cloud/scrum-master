package com.companyos.backend.tenant;

import com.companyos.reporting.ForecastPolicy;
import com.companyos.reporting.ForecastPolicyConfigurationService;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/forecast-policy")
public class TenantForecastPolicyController {
    private final ForecastPolicyConfigurationService service;

    public TenantForecastPolicyController(ForecastPolicyConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    public ForecastPolicy get() {
        var context = TenantContextHolder.required();
        return service.get(context.tenantId());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@Valid @RequestBody ForecastPolicyRequest request) {
        var context = TenantContextHolder.required();
        service.update(context.tenantId(), context.userId(), new ForecastPolicy(
                request.enabled(), request.historicalCalibrationEnabled(), request.releaseAtRiskDays(), request.releaseAtRiskCompletionPercent(),
                request.projectOverPlanPercent()));
    }

    public record ForecastPolicyRequest(
            @NotNull Boolean enabled,
            boolean historicalCalibrationEnabled,
            @Min(0) @Max(365) int releaseAtRiskDays,
            @Min(0) @Max(100) int releaseAtRiskCompletionPercent,
            @Min(1) @Max(500) int projectOverPlanPercent) {
    }
}
