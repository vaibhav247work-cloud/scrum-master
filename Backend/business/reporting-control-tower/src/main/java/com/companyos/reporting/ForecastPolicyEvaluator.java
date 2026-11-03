package com.companyos.reporting;

import java.time.LocalDate;

public final class ForecastPolicyEvaluator {
    private ForecastPolicyEvaluator() { }

    public static String projectStatus(DeliveryForecastRow row, ForecastPolicy policy) {
        if (!policy.enabled()) return row.forecastStatus();
        if (row.atRiskReleases() > 0 || row.blockedDependencies() > 0) return "AT_RISK";
        if (row.plannedAllocationMinutes() == 0 && row.actualLoggedMinutes() == 0) return "NO_PLAN";
        if (row.plannedAllocationMinutes() == 0
                || row.actualLoggedMinutes() * 100 > row.plannedAllocationMinutes() * policy.projectOverPlanPercent()) {
            return "OVER_PLAN";
        }
        if (row.openWorkItems() == 0 && row.completedWorkItems() > 0) return "COMPLETE";
        return "ON_TRACK";
    }

    public static String releaseStatus(ReleaseReadinessRow row, ForecastPolicy policy) {
        if (!policy.enabled()) return row.readinessStatus();
        if ("RELEASED".equals(row.releaseStatus())) return "RELEASED";
        if ("CANCELLED".equals(row.releaseStatus())) return "CANCELLED";
        LocalDate targetDate = row.targetDate();
        if (targetDate == null) return "NO_TARGET";
        if (row.blockedWorkItems() > 0 || (row.daysToTarget() != null && row.daysToTarget() < 0)) return "AT_RISK";
        if (row.completionPercent() == 100 && row.openWorkItems() == 0 && row.totalWorkItems() > 0) return "READY";
        if (row.daysToTarget() != null && row.daysToTarget() <= policy.releaseAtRiskDays()
                && row.completionPercent() < policy.releaseAtRiskCompletionPercent()) return "AT_RISK";
        if (row.totalWorkItems() == 0) return "NO_SCOPE";
        return "ON_TRACK";
    }
}
