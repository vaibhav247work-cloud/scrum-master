package com.companyos.backend.work;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.time.TimeEntrySummary;
import com.companyos.time.TimeTrackingService;
import com.companyos.work.WorkItemFilter;
import com.companyos.work.WorkItemSummary;
import com.companyos.work.WorkManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/work-hub")
public class WorkHubController {
    private final WorkManagementService workManagementService;
    private final TimeTrackingService timeTrackingService;

    public WorkHubController(
            WorkManagementService workManagementService,
            TimeTrackingService timeTrackingService) {
        this.workManagementService = workManagementService;
        this.timeTrackingService = timeTrackingService;
    }

    @GetMapping
    public WorkHubResponse get() {
        var context = TenantContextHolder.required();
        return new WorkHubResponse(
                workManagementService.find(context.tenantId(), new WorkItemFilter(null, null, context.userId())),
                timeTrackingService.findForUser(context.tenantId(), context.userId()));
    }

    public record WorkHubResponse(
            List<WorkItemSummary> workItems,
            List<TimeEntrySummary> recentTimeEntries) {
    }
}
