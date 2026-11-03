package com.companyos.reporting;

import java.util.UUID;

/**
 * Tenant-scoped management read model combining delivery, effort, capacity,
 * sprint, release, and dependency signals for one project and a selected period.
 */
public record DeliveryForecastRow(
        UUID projectId,
        String projectKey,
        String projectName,
        long plannedAllocationMinutes,
        long actualLoggedMinutes,
        int openWorkItems,
        int completedWorkItems,
        int completionPercent,
        int activeSprints,
        int activeReleases,
        int atRiskReleases,
        int blockedDependencies,
        String forecastStatus,
        long estimatedScopeMinutes,
        long remainingEstimateMinutes,
        long velocityMinutesPerWeek,
        Integer projectedDaysRemaining,
        double calibrationFactor,
        int calibrationSampleSize) {

    public DeliveryForecastRow(UUID projectId, String projectKey, String projectName,
                               long plannedAllocationMinutes, long actualLoggedMinutes,
                               int openWorkItems, int completedWorkItems, int completionPercent,
                               int activeSprints, int activeReleases, int atRiskReleases,
                               int blockedDependencies, String forecastStatus) {
        this(projectId, projectKey, projectName, plannedAllocationMinutes, actualLoggedMinutes,
                openWorkItems, completedWorkItems, completionPercent, activeSprints, activeReleases,
                atRiskReleases, blockedDependencies, forecastStatus, 0, 0, 0, null, 1.0, 0);
    }

    public DeliveryForecastRow(UUID projectId, String projectKey, String projectName,
                               long plannedAllocationMinutes, long actualLoggedMinutes,
                               int openWorkItems, int completedWorkItems, int completionPercent,
                               int activeSprints, int activeReleases, int atRiskReleases,
                               int blockedDependencies, String forecastStatus,
                               long estimatedScopeMinutes, long remainingEstimateMinutes,
                               long velocityMinutesPerWeek, Integer projectedDaysRemaining) {
        this(projectId, projectKey, projectName, plannedAllocationMinutes, actualLoggedMinutes,
                openWorkItems, completedWorkItems, completionPercent, activeSprints, activeReleases,
                atRiskReleases, blockedDependencies, forecastStatus, estimatedScopeMinutes,
                remainingEstimateMinutes, velocityMinutesPerWeek, projectedDaysRemaining, 1.0, 0);
    }

    public DeliveryForecastRow withForecastStatus(String status) {
        return new DeliveryForecastRow(projectId, projectKey, projectName, plannedAllocationMinutes,
                actualLoggedMinutes, openWorkItems, completedWorkItems, completionPercent, activeSprints,
                activeReleases, atRiskReleases, blockedDependencies, status, estimatedScopeMinutes,
                remainingEstimateMinutes, velocityMinutesPerWeek, projectedDaysRemaining, calibrationFactor,
                calibrationSampleSize);
    }

    public DeliveryForecastRow withProjection(double factor, int sampleSize, Integer projectedDays) {
        return new DeliveryForecastRow(projectId, projectKey, projectName, plannedAllocationMinutes,
                actualLoggedMinutes, openWorkItems, completedWorkItems, completionPercent, activeSprints,
                activeReleases, atRiskReleases, blockedDependencies, forecastStatus, estimatedScopeMinutes,
                remainingEstimateMinutes, velocityMinutesPerWeek, projectedDays, factor, sampleSize);
    }
}
