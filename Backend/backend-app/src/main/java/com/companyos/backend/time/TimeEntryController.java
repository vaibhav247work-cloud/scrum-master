package com.companyos.backend.time;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.time.CreateTimeEntryCommand;
import com.companyos.time.TimeEntrySummary;
import com.companyos.time.TimeTrackingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/time-entries")
public class TimeEntryController {
    private final TimeTrackingService service;

    public TimeEntryController(TimeTrackingService service) {
        this.service = service;
    }

    @GetMapping
    public List<TimeEntrySummary> list() {
        var context = TenantContextHolder.required();
        return service.findForUser(context.tenantId(), context.userId());
    }

    @PostMapping
    public ResponseEntity<TimeEntrySummary> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTimeEntryRequest request) {
        var context = TenantContextHolder.required();
        var result = service.create(context.tenantId(), context.userId(), new CreateTimeEntryCommand(
                request.workItemId(), request.projectId(), request.sprintId(), request.entryDate(),
                request.minutes(), request.description(), request.source(), request.categoryKey(), "APPROVED"), idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replay", Boolean.toString(result.replayed()))
                .body(result.entry());
    }

    public record CreateTimeEntryRequest(
            UUID workItemId,
            UUID projectId,
            UUID sprintId,
            LocalDate entryDate,
            @Min(1) @Max(1440) int minutes,
            @Size(max = 4000) String description,
            @Size(max = 32) String source,
            @Size(max = 64) String categoryKey) {
    }
}
