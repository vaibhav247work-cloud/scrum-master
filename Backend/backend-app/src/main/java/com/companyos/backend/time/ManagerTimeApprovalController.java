package com.companyos.backend.time;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.time.TimeEntrySummary;
import com.companyos.time.TimeTrackingService;
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
@RequestMapping("/api/v1/manager/time-entries")
public class ManagerTimeApprovalController {
    private final TimeTrackingService service;

    public ManagerTimeApprovalController(TimeTrackingService service) { this.service = service; }

    @GetMapping("/pending")
    public List<TimeEntrySummary> pending() {
        return service.findPending(TenantContextHolder.required().tenantId());
    }

    @PostMapping("/{entryId}/approval")
    public TimeEntrySummary decide(@PathVariable UUID entryId, @Valid @RequestBody ApprovalRequest request) {
        var context = TenantContextHolder.required();
        return service.decide(context.tenantId(), context.userId(), entryId, request.approved(), request.note());
    }

    public record ApprovalRequest(boolean approved, @Size(max = 1000) String note) { }
}
