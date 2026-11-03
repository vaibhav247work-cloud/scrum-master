package com.companyos.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ForecastCalibrationStore {
    List<ForecastCalibrationRow> calibration(UUID tenantId, LocalDate from, LocalDate to, UUID projectId);
}
