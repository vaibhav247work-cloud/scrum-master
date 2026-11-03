package com.companyos.backend.tenant;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.time.TimePolicy;
import com.companyos.time.TimePolicyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/time-policy")
public class TenantTimePolicyController {
    private final TimePolicyService service;

    public TenantTimePolicyController(TimePolicyService service) {
        this.service = service;
    }

    @GetMapping
    public TimePolicy get() {
        var context = TenantContextHolder.required();
        return service.get(context.tenantId());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@Valid @RequestBody TimePolicyRequest request) {
        var context = TenantContextHolder.required();
        service.update(context.tenantId(), new TimePolicy(
                request.enabled(), request.expectedDailyMinutes(), request.allowOvertime(), request.allowWeekend(),
                request.approvalEnabled(), request.approvalCadence(), request.autoApprove(),
                request.roundingIncrementMinutes(), request.roundingMode()));
    }

    public record TimePolicyRequest(
            @NotNull Boolean enabled,
            @Min(1) @Max(1440) int expectedDailyMinutes,
            @NotNull Boolean allowOvertime,
            @NotNull Boolean allowWeekend,
            boolean approvalEnabled,
            @Size(max = 16) String approvalCadence,
            boolean autoApprove,
            @Min(1) @Max(60) int roundingIncrementMinutes,
            @NotNull @Size(max = 16) String roundingMode) {
    }
}
