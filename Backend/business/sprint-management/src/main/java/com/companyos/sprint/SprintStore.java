package com.companyos.sprint;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.Set;

public interface SprintStore {
    SprintSummary create(UUID tenantId, UUID projectId, UUID actorId, CreateSprintCommand command);

    List<SprintSummary> findByProject(UUID tenantId, UUID projectId);

    Optional<SprintSummary> findById(UUID tenantId, UUID sprintId);

    SprintSummary updateStatus(UUID tenantId, UUID sprintId, UUID actorId, String status, String completionNote);

    SprintProgressSummary progress(UUID tenantId, UUID sprintId);

    SprintCarryOverResult carryOver(UUID tenantId, UUID sourceSprintId, UUID targetSprintId,
                                    UUID actorId, Set<UUID> workItemIds);
}
