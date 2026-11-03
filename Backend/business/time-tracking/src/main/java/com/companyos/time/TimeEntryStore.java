package com.companyos.time;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

public interface TimeEntryStore {
    TimeEntrySummary create(UUID tenantId, UUID userId, CreateTimeEntryCommand command);

    List<TimeEntrySummary> findForUser(UUID tenantId, UUID userId);

    List<TimeEntrySummary> findPending(UUID tenantId);

    TimeEntrySummary decide(UUID tenantId, UUID entryId, UUID approverId, boolean approved, String note);

    int sumMinutesForUserOnDate(UUID tenantId, UUID userId, LocalDate entryDate);
}
