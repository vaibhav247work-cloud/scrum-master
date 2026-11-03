package com.companyos.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DeliveryForecastStore {
    List<DeliveryForecastRow> forecast(UUID tenantId, LocalDate from, LocalDate to, UUID projectId);
}
