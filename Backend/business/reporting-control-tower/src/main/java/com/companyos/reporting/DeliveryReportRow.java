package com.companyos.reporting;

import java.util.UUID;

public record DeliveryReportRow(
        UUID projectId,
        String projectKey,
        String projectName,
        int openWorkItems,
        int completedWorkItems,
        int loggedMinutes,
        int activeReleases,
        int atRiskReleases) {
}
