package com.companyos.backend.time;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.time.TimesheetService;
import com.companyos.time.TimesheetSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/timesheet")
public class TimesheetController {
    private final TimesheetService service;

    public TimesheetController(TimesheetService service) { this.service = service; }

    @GetMapping
    public TimesheetSummary current() {
        var context = TenantContextHolder.required();
        return service.current(context.tenantId(), context.userId());
    }

    @PostMapping("/{timesheetId}/submit")
    public TimesheetSummary submit(@PathVariable UUID timesheetId) {
        var context = TenantContextHolder.required();
        return service.submit(context.tenantId(), context.userId(), timesheetId);
    }
}
