package com.companyos.workflow;

import java.util.List;
import java.util.Map;

public record BoardConfigurationCommand(
        String grouping,
        String swimlane,
        List<String> cardFields,
        Map<String, Integer> wipLimits) {
}
