package com.companyos.requirements;

import java.util.List;
import java.util.UUID;

public interface RequirementsStore {
    RequirementSummary create(UUID tenantId, UUID actorId, CreateRequirementCommand command);

    List<RequirementSummary> findAll(UUID tenantId, String status);

    RequirementSummary updateCheck(UUID tenantId, UUID requirementId, String checkKey, String label,
                                   boolean required, boolean completed, String evidence, UUID actorId);

    RequirementSummary decide(UUID tenantId, UUID requirementId, UUID reviewerId, String status, String note);
}
