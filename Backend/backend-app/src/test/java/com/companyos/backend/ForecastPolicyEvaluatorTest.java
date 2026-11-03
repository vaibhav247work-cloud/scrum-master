package com.companyos.backend;

import com.companyos.reporting.DeliveryForecastRow;
import com.companyos.reporting.ForecastPolicy;
import com.companyos.reporting.ForecastPolicyEvaluator;
import com.companyos.reporting.ReleaseReadinessRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForecastPolicyEvaluatorTest {
    @Test
    void disabledPolicyLeavesExistingStatusUnchanged() {
        DeliveryForecastRow row = project("ON_TRACK", 1100, 1000);
        assertEquals("ON_TRACK", ForecastPolicyEvaluator.projectStatus(row, ForecastPolicy.defaults()));
    }

    @Test
    void enabledPolicyAppliesTenantOverPlanThreshold() {
        DeliveryForecastRow row = project("ON_TRACK", 1100, 1000);
        ForecastPolicy policy = new ForecastPolicy(true, 7, 80, 120);
        assertEquals("ON_TRACK", ForecastPolicyEvaluator.projectStatus(row, policy));
        assertEquals("OVER_PLAN", ForecastPolicyEvaluator.projectStatus(project("ON_TRACK", 1300, 1000), policy));
    }

    @Test
    void enabledPolicyAppliesTenantReleaseRiskWindow() {
        ReleaseReadinessRow row = new ReleaseReadinessRow(UUID.randomUUID(), UUID.randomUUID(), "WEB", "R1",
                "Release", LocalDate.now().plusDays(10), "IN_PROGRESS", 10, 5, 5, 0, 50, 10, "ON_TRACK");
        ForecastPolicy policy = new ForecastPolicy(true, 14, 80, 100);
        assertEquals("AT_RISK", ForecastPolicyEvaluator.releaseStatus(row, policy));
    }

    private static DeliveryForecastRow project(String status, long actual, long planned) {
        return new DeliveryForecastRow(UUID.randomUUID(), "WEB", "Web", planned, actual, 2, 3, 60,
                1, 1, 0, 0, status);
    }
}
