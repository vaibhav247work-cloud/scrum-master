package com.companyos.reporting;

import java.util.UUID;

public record ForecastCalibrationRow(
        UUID projectId,
        String projectKey,
        String projectName,
        int sampleSize,
        long estimatedMinutes,
        long actualLoggedMinutes,
        double calibrationFactor,
        int accuracyPercent) {
}
