package com.companyos.time;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimesheetSummary(
        UUID id,
        UUID userId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String cadence,
        String status,
        int totalMinutes,
        int pendingMinutes,
        int approvedMinutes,
        int rejectedMinutes,
        int entryCount,
        Instant submittedAt,
        UUID submittedBy,
        Instant decidedAt,
        UUID decidedBy,
        String decisionNote) {
}
