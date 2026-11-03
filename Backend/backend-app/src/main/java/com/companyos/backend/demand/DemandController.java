package com.companyos.backend.demand;

import com.companyos.demand.CreateDemandCommand;
import com.companyos.demand.DemandManagementService;
import com.companyos.demand.DemandSummary;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demands")
public class DemandController {
    private final DemandManagementService service;

    public DemandController(DemandManagementService service) { this.service = service; }

    @GetMapping
    public List<DemandSummary> list(@RequestParam(required = false) String status) {
        return service.list(TenantContextHolder.required().tenantId(), status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DemandSummary create(@Valid @RequestBody DemandRequest request) {
        var context = TenantContextHolder.required();
        return service.create(context.tenantId(), context.userId(), new CreateDemandCommand(
                request.demandKey(), request.title(), request.description(), request.demandType(), request.priority()));
    }

    @PostMapping("/{demandId}/decision")
    public DemandSummary decide(@PathVariable UUID demandId, @Valid @RequestBody DecisionRequest request) {
        var context = TenantContextHolder.required();
        return service.decide(context.tenantId(), context.userId(), demandId, request.status(), request.ownerUserId(), request.note());
    }

    public record DemandRequest(@NotBlank @Size(max = 32) String demandKey,
                                @NotBlank @Size(max = 240) String title,
                                @Size(max = 8000) String description,
                                @Size(max = 16) String demandType,
                                @Size(max = 16) String priority) { }

    public record DecisionRequest(@NotBlank @Size(max = 16) String status,
                                  UUID ownerUserId,
                                  @Size(max = 2000) String note) { }
}
