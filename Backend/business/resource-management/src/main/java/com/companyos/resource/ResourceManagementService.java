package com.companyos.resource;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ResourceManagementService {
    private final ResourceWorkloadStore store;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;

    public ResourceManagementService(
            ResourceWorkloadStore store,
            FeatureEntitlementService featureEntitlements,
            AuthorizationService authorization) {
        this.store = store;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<ResourceWorkloadMember> workload(UUID tenantId, LocalDate requestedWeekStart) {
        featureEntitlements.requireEnabled(tenantId, "RESOURCE_MANAGEMENT");
        authorization.requirePermission("resource.workload.read");
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart == null ? LocalDate.now() : requestedWeekStart);
        return store.findWorkload(tenantId, weekStart, 30);
    }

    @Transactional(readOnly = true)
    public List<ResourceAllocationSummary> allocations(UUID tenantId, LocalDate requestedWeekStart) {
        featureEntitlements.requireEnabled(tenantId, "RESOURCE_MANAGEMENT");
        authorization.requirePermission("resource.allocation.read");
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart == null ? LocalDate.now() : requestedWeekStart);
        return store.findAllocations(tenantId, weekStart, weekStart.plusDays(6));
    }

    @Transactional
    public ResourceAllocationSummary createAllocation(UUID tenantId, UUID actorId, CreateResourceAllocationCommand command) {
        featureEntitlements.requireEnabled(tenantId, "RESOURCE_MANAGEMENT");
        featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        authorization.requirePermission("resource.allocation.manage");
        if (command == null) throw new IllegalArgumentException("request body is required");
        if (command.userId() == null) throw new IllegalArgumentException("userId is required");
        if (command.projectId() == null) throw new IllegalArgumentException("projectId is required");
        if (command.startDate() == null || command.endDate() == null) throw new IllegalArgumentException("startDate and endDate are required");
        if (command.endDate().isBefore(command.startDate())) throw new IllegalArgumentException("endDate cannot be before startDate");
        if (command.plannedMinutesPerWeek() < 1 || command.plannedMinutesPerWeek() > 10080) {
            throw new IllegalArgumentException("plannedMinutesPerWeek must be between 1 and 10080");
        }
        if (command.sprintId() != null) featureEntitlements.requireEnabled(tenantId, "SCRUM");
        String note = command.note() == null ? "" : command.note().trim();
        if (note.length() > 1000) throw new IllegalArgumentException("note must be at most 1000 characters");
        return store.createAllocation(tenantId, actorId, new CreateResourceAllocationCommand(
                command.userId(), command.projectId(), command.sprintId(), command.teamId(), command.startDate(),
                command.endDate(), command.plannedMinutesPerWeek(), note));
    }

    private static LocalDate normalizeWeekStart(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
