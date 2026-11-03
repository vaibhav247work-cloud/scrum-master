package com.companyos.backend.time;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.time.TimesheetService;
import com.companyos.time.TimesheetSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manager/timesheets")
public class ManagerTimesheetController {
    private final TimesheetService service;

    public ManagerTimesheetController(TimesheetService service) { this.service = service; }

    @GetMapping("/pending")
    public List<TimesheetSummary> pending() {
        return service.pending(TenantContextHolder.required().tenantId());
    }

    @PostMapping("/{timesheetId}/decision")
    public TimesheetSummary decide(@PathVariable UUID timesheetId,
                                   @Valid @RequestBody DecisionRequest request) {
        var context = TenantContextHolder.required();
        return service.decide(context.tenantId(), context.userId(), timesheetId, request.approved(), request.note());
    }

    @PostMapping("/{timesheetId}/reopen")
    public TimesheetSummary reopen(@PathVariable UUID timesheetId,
                                   @Valid @RequestBody NoteRequest request) {
        var context = TenantContextHolder.required();
        return service.reopen(context.tenantId(), context.userId(), timesheetId, request.note());
    }

    public record DecisionRequest(boolean approved, @Size(max = 1000) String note) { }
    public record NoteRequest(@Size(max = 1000) String note) { }
}
