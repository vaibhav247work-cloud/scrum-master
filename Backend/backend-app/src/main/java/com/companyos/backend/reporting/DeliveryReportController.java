package com.companyos.backend.reporting;

import com.companyos.reporting.DeliveryReportRow;
import com.companyos.reporting.DeliveryReportService;
import com.companyos.reporting.DeliveryForecastRow;
import com.companyos.reporting.DeliveryForecastService;
import com.companyos.reporting.ForecastCalibrationRow;
import com.companyos.reporting.ForecastCalibrationService;
import com.companyos.reporting.ReleaseReadinessRow;
import com.companyos.reporting.ReleaseReadinessService;
import com.companyos.tenantcontext.TenantContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manager/reports")
public class DeliveryReportController {
    private final DeliveryReportService service;
    private final DeliveryForecastService forecastService;
    private final ReleaseReadinessService releaseReadinessService;
    private final ForecastCalibrationService calibrationService;

    public DeliveryReportController(DeliveryReportService service, DeliveryForecastService forecastService,
                                    ReleaseReadinessService releaseReadinessService,
                                    ForecastCalibrationService calibrationService) {
        this.service = service;
        this.forecastService = forecastService;
        this.releaseReadinessService = releaseReadinessService;
        this.calibrationService = calibrationService;
    }

    @GetMapping("/release-readiness")
    public List<ReleaseReadinessRow> releaseReadiness(@RequestParam(required = false) UUID projectId) {
        return releaseReadinessService.readiness(TenantContextHolder.required().tenantId(), projectId);
    }

    @GetMapping("/forecast")
    public List<DeliveryForecastRow> forecast(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID projectId) {
        return forecastService.forecast(TenantContextHolder.required().tenantId(), from, to, projectId);
    }

    @GetMapping("/forecast-calibration")
    public List<ForecastCalibrationRow> forecastCalibration(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID projectId) {
        return calibrationService.calibration(TenantContextHolder.required().tenantId(), from, to, projectId);
    }

    @GetMapping("/delivery")
    public List<DeliveryReportRow> delivery(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID projectId) {
        return service.delivery(TenantContextHolder.required().tenantId(), from, to, projectId);
    }

    @GetMapping(value = "/delivery.csv", produces = "text/csv")
    public ResponseEntity<byte[]> deliveryCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID projectId) {
        List<DeliveryReportRow> rows = service.delivery(TenantContextHolder.required().tenantId(), from, to, projectId);
        StringBuilder csv = new StringBuilder("project_key,project_name,open_work_items,completed_work_items,logged_minutes,active_releases,at_risk_releases\n");
        rows.forEach(row -> csv.append(csv(row.projectKey())).append(',').append(csv(row.projectName())).append(',')
                .append(row.openWorkItems()).append(',').append(row.completedWorkItems()).append(',')
                .append(row.loggedMinutes()).append(',').append(row.activeReleases()).append(',').append(row.atRiskReleases()).append('\n'));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=delivery-report.csv")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String value) { return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\""; }
}
