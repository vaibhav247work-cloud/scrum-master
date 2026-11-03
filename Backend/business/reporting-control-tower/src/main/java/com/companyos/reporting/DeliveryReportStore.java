package com.companyos.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DeliveryReportStore {
    List<DeliveryReportRow> delivery(UUID tenantId, LocalDate from, LocalDate to, UUID projectId);
}
