package com.companyos.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BoardConfiguration(
        UUID projectId,
        String grouping,
        String swimlane,
        List<String> cardFields,
        Map<String, Integer> wipLimits,
        Instant updatedAt) {
}
