package com.companyos.backend.resource;

import com.companyos.resource.ResourceManagementService;
import com.companyos.resource.ResourceWorkloadMember;
import com.companyos.resource.ResourceAllocationSummary;
import com.companyos.resource.CreateResourceAllocationCommand;
import com.companyos.tenantcontext.TenantContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manager/workload")
public class ResourceWorkloadController {
    private final ResourceManagementService service;

    public ResourceWorkloadController(ResourceManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<ResourceWorkloadMember> workload(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart) {
        return service.workload(TenantContextHolder.required().tenantId(), weekStart);
    }

    @GetMapping("/allocations")
    public List<ResourceAllocationSummary> allocations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart) {
        return service.allocations(TenantContextHolder.required().tenantId(), weekStart);
    }

    @PostMapping("/allocations")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceAllocationSummary createAllocation(@RequestBody CreateAllocationRequest request) {
        var context = TenantContextHolder.required();
        return service.createAllocation(context.tenantId(), context.userId(), new CreateResourceAllocationCommand(
                request.userId(), request.projectId(), request.sprintId(), request.teamId(), request.startDate(),
                request.endDate(), request.plannedMinutesPerWeek(), request.note()));
    }

    public record CreateAllocationRequest(UUID userId, UUID projectId, UUID sprintId, UUID teamId,
                                          LocalDate startDate, LocalDate endDate, int plannedMinutesPerWeek,
                                          String note) { }
}
