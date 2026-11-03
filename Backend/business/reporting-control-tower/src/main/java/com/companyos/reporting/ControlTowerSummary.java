package com.companyos.reporting;

import java.time.Instant;

public record ControlTowerSummary(
        Instant generatedAt,
        int activeProjects,
        int activeSprints,
        int openWorkItems,
        int blockedDependencies,
        int activeReleases,
        int atRiskReleases,
        int loggedMinutesThisWeek,
        int activeMembers) {
}
