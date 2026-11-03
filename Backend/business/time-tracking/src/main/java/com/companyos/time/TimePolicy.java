package com.companyos.time;

import java.util.Set;

public record TimePolicy(
        boolean enabled,
        int expectedDailyMinutes,
        boolean allowOvertime,
        boolean allowWeekend,
        boolean approvalEnabled,
        String approvalCadence,
        boolean autoApprove,
        int roundingIncrementMinutes,
        String roundingMode) {

    private static final Set<Integer> ROUNDING_INCREMENTS = Set.of(1, 5, 10, 15, 30);
    private static final Set<String> ROUNDING_MODES = Set.of("NEAREST", "UP", "DOWN");

    public static TimePolicy defaults() {
        return new TimePolicy(false, 480, false, false, false, "WEEKLY", false, 1, "NEAREST");
    }

    public TimePolicy(boolean enabled, int expectedDailyMinutes, boolean allowOvertime,
                      boolean allowWeekend, int roundingIncrementMinutes, String roundingMode) {
        this(enabled, expectedDailyMinutes, allowOvertime, allowWeekend, false, "WEEKLY", false,
                roundingIncrementMinutes, roundingMode);
    }

    public TimePolicy validated() {
        if (expectedDailyMinutes < 1 || expectedDailyMinutes > 1440) {
            throw new IllegalArgumentException("expectedDailyMinutes must be between 1 and 1440");
        }
        if (!ROUNDING_INCREMENTS.contains(roundingIncrementMinutes)) {
            throw new IllegalArgumentException("roundingIncrementMinutes must be one of 1, 5, 10, 15 or 30");
        }
        String normalizedMode = roundingMode == null ? "NEAREST" : roundingMode.trim().toUpperCase();
        if (!ROUNDING_MODES.contains(normalizedMode)) {
            throw new IllegalArgumentException("roundingMode must be NEAREST, UP or DOWN");
        }
        String normalizedCadence = approvalCadence == null ? "WEEKLY" : approvalCadence.trim().toUpperCase();
        if (!Set.of("DAILY", "WEEKLY", "MONTHLY").contains(normalizedCadence)) {
            throw new IllegalArgumentException("approvalCadence must be DAILY, WEEKLY or MONTHLY");
        }
        return new TimePolicy(enabled, expectedDailyMinutes, allowOvertime, allowWeekend,
                approvalEnabled, normalizedCadence, autoApprove, roundingIncrementMinutes, normalizedMode);
    }

    public int roundMinutes(int minutes) {
        int increment = roundingIncrementMinutes;
        if (increment == 1) return minutes;
        double quotient = (double) minutes / increment;
        return switch (roundingMode) {
            case "UP" -> (int) Math.ceil(quotient) * increment;
            case "DOWN" -> (int) Math.floor(quotient) * increment;
            default -> (int) Math.round(quotient) * increment;
        };
    }
}
