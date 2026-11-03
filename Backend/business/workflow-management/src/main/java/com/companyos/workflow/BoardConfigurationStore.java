package com.companyos.workflow;

import java.util.UUID;

public interface BoardConfigurationStore {
    BoardConfiguration find(UUID tenantId, UUID projectId);

    BoardConfiguration upsert(UUID tenantId, UUID actorId, UUID projectId, BoardConfigurationCommand command);
}
