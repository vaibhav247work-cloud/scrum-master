package com.companyos.backend.quality;

import com.companyos.quality.CreateIncidentCommand;
import com.companyos.quality.CreateQualityGateCommand;
import com.companyos.quality.IncidentSummary;
import com.companyos.quality.QualityGateSummary;
import com.companyos.quality.QualityOperationsService;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quality")
public class QualityOperationsController {
    private final QualityOperationsService service;
    public QualityOperationsController(QualityOperationsService service) { this.service = service; }

    @GetMapping("/gates")
    public List<QualityGateSummary> gates(@RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID releaseId) {
        return service.listGates(TenantContextHolder.required().tenantId(), projectId, releaseId);
    }

    @PostMapping("/gates")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityGateSummary createGate(@Valid @RequestBody GateRequest request) {
        var context = TenantContextHolder.required();
        return service.createGate(context.tenantId(), context.userId(), new CreateQualityGateCommand(request.projectId(), request.releaseId(), request.gateKey(), request.gateType(), request.required()));
    }

    @PostMapping("/gates/{gateId}/decision")
    public QualityGateSummary decideGate(@PathVariable UUID gateId, @Valid @RequestBody GateDecision request) {
        var context = TenantContextHolder.required();
        return service.decideGate(context.tenantId(), context.userId(), gateId, request.status(), request.evidence());
    }

    @GetMapping("/incidents")
    public List<IncidentSummary> incidents(@RequestParam(required = false) String status) {
        return service.listIncidents(TenantContextHolder.required().tenantId(), status);
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentSummary createIncident(@Valid @RequestBody IncidentRequest request) {
        var context = TenantContextHolder.required();
        return service.createIncident(context.tenantId(), context.userId(), new CreateIncidentCommand(request.projectId(), request.releaseId(), request.incidentKey(), request.title(), request.description(), request.severity(), request.ownerUserId()));
    }

    @PatchMapping("/incidents/{incidentId}")
    public IncidentSummary updateIncident(@PathVariable UUID incidentId, @Valid @RequestBody IncidentUpdate request) {
        var context = TenantContextHolder.required();
        return service.updateIncident(context.tenantId(), context.userId(), incidentId, request.status(), request.mitigation(), request.rootCause());
    }

    public record GateRequest(UUID projectId, UUID releaseId, @NotBlank @Size(max = 32) String gateKey, @Size(max = 16) String gateType, boolean required) { }
    public record GateDecision(@NotBlank @Size(max = 16) String status, @Size(max = 4000) String evidence) { }
    public record IncidentRequest(UUID projectId, UUID releaseId, @NotBlank @Size(max = 32) String incidentKey, @NotBlank @Size(max = 240) String title, @Size(max = 8000) String description, @Size(max = 2) String severity, UUID ownerUserId) { }
    public record IncidentUpdate(@NotBlank @Size(max = 16) String status, @Size(max = 8000) String mitigation, @Size(max = 8000) String rootCause) { }
}
