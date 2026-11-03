package com.companyos.reporting;

import java.time.LocalDate;
import java.util.UUID;

/** Tenant-scoped release readiness read model for management views. */
public record ReleaseReadinessRow(
        UUID releaseId,
        UUID projectId,
        String projectKey,
        String releaseKey,
        String releaseName,
        LocalDate targetDate,
        String releaseStatus,
        int totalWorkItems,
        int completedWorkItems,
        int openWorkItems,
        int blockedWorkItems,
        int completionPercent,
        Integer daysToTarget,
        String readinessStatus,
        long estimatedScopeMinutes,
        long remainingEstimateMinutes,
        long velocityMinutesPerWeek,
        Integer projectedDaysRemaining,
        double calibrationFactor,
        int calibrationSampleSize) {

    public ReleaseReadinessRow(UUID releaseId, UUID projectId, String projectKey, String releaseKey,
                               String releaseName, LocalDate targetDate, String releaseStatus,
                               int totalWorkItems, int completedWorkItems, int openWorkItems,
                               int blockedWorkItems, int completionPercent, Integer daysToTarget,
                               String readinessStatus) {
        this(releaseId, projectId, projectKey, releaseKey, releaseName, targetDate, releaseStatus,
                totalWorkItems, completedWorkItems, openWorkItems, blockedWorkItems, completionPercent,
                daysToTarget, readinessStatus, 0, 0, 0, null, 1.0, 0);
    }

    public ReleaseReadinessRow(UUID releaseId, UUID projectId, String projectKey, String releaseKey,
                               String releaseName, LocalDate targetDate, String releaseStatus,
                               int totalWorkItems, int completedWorkItems, int openWorkItems,
                               int blockedWorkItems, int completionPercent, Integer daysToTarget,
                               String readinessStatus, long estimatedScopeMinutes,
                               long remainingEstimateMinutes, long velocityMinutesPerWeek,
                               Integer projectedDaysRemaining) {
        this(releaseId, projectId, projectKey, releaseKey, releaseName, targetDate, releaseStatus,
                totalWorkItems, completedWorkItems, openWorkItems, blockedWorkItems, completionPercent,
                daysToTarget, readinessStatus, estimatedScopeMinutes, remainingEstimateMinutes,
                velocityMinutesPerWeek, projectedDaysRemaining, 1.0, 0);
    }

    public ReleaseReadinessRow withReadinessStatus(String status) {
        return new ReleaseReadinessRow(releaseId, projectId, projectKey, releaseKey, releaseName, targetDate,
                releaseStatus, totalWorkItems, completedWorkItems, openWorkItems, blockedWorkItems,
                completionPercent, daysToTarget, status, estimatedScopeMinutes, remainingEstimateMinutes,
                velocityMinutesPerWeek, projectedDaysRemaining, calibrationFactor, calibrationSampleSize);
    }

    public ReleaseReadinessRow withProjection(double factor, int sampleSize, Integer projectedDays) {
        return new ReleaseReadinessRow(releaseId, projectId, projectKey, releaseKey, releaseName, targetDate,
                releaseStatus, totalWorkItems, completedWorkItems, openWorkItems, blockedWorkItems,
                completionPercent, daysToTarget, readinessStatus, estimatedScopeMinutes,
                remainingEstimateMinutes, velocityMinutesPerWeek, projectedDays, factor, sampleSize);
    }
}
