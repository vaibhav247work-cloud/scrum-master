package com.companyos.reporting;

import java.time.LocalDate;
import java.util.UUID;

public interface ControlTowerStore {
    ControlTowerSummary summarize(UUID tenantId, LocalDate weekStart);
}
