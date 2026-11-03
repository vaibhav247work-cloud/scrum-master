package com.companyos.requirements;

import java.util.UUID;

public record CreateRequirementCommand(
        String requirementKey,
        UUID demandId,
        String title,
        String description,
        String businessContext,
        String uxContext,
        String technicalContext,
        String acceptanceCriteria) {
}
