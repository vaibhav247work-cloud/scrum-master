package com.companyos.time;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TimesheetStore {
    TimesheetSummary findOrCreateCurrent(UUID tenantId, UUID userId, String cadence, LocalDate today, Instant now);

    default void ensurePeriodForDate(UUID tenantId, UUID userId, String cadence, LocalDate date, Instant now) {
        // Compatibility no-op for lightweight/unit implementations. The MySQL adapter
        // overrides this so every central time-entry path creates its period atomically.
    }

    TimesheetSummary submit(UUID tenantId, UUID userId, UUID timesheetId, boolean autoApprove, Instant now);

    List<TimesheetSummary> findPending(UUID tenantId);

    TimesheetSummary decide(UUID tenantId, UUID timesheetId, UUID approverId, boolean approved, String note, Instant now);

    TimesheetSummary reopen(UUID tenantId, UUID timesheetId, UUID actorId, String note, Instant now);

    boolean isDateLocked(UUID tenantId, UUID userId, LocalDate entryDate);
}
