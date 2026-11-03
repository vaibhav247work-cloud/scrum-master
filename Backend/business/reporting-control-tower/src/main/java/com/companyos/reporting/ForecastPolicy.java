package com.companyos.reporting;

public record ForecastPolicy(
        boolean enabled,
        boolean historicalCalibrationEnabled,
        int releaseAtRiskDays,
        int releaseAtRiskCompletionPercent,
        int projectOverPlanPercent) {

    public static ForecastPolicy defaults() {
        return new ForecastPolicy(false, false, 7, 80, 100);
    }

    public ForecastPolicy(boolean enabled, int releaseAtRiskDays,
                          int releaseAtRiskCompletionPercent, int projectOverPlanPercent) {
        this(enabled, false, releaseAtRiskDays, releaseAtRiskCompletionPercent, projectOverPlanPercent);
    }

    public ForecastPolicy validated() {
        if (releaseAtRiskDays < 0 || releaseAtRiskDays > 365) {
            throw new IllegalArgumentException("releaseAtRiskDays must be between 0 and 365");
        }
        if (releaseAtRiskCompletionPercent < 0 || releaseAtRiskCompletionPercent > 100) {
            throw new IllegalArgumentException("releaseAtRiskCompletionPercent must be between 0 and 100");
        }
        if (projectOverPlanPercent < 1 || projectOverPlanPercent > 500) {
            throw new IllegalArgumentException("projectOverPlanPercent must be between 1 and 500");
        }
        return this;
    }
}
