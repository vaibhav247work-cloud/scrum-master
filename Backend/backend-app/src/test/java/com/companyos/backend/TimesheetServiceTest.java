package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.time.TimePolicy;
import com.companyos.time.TimePolicyStore;
import com.companyos.time.TimesheetService;
import com.companyos.time.TimesheetStore;
import com.companyos.time.TimesheetSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimesheetServiceTest {
    private final TimesheetStore store = mock(TimesheetStore.class);
    private final TimePolicyStore policies = mock(TimePolicyStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final TimesheetService service = new TimesheetService(
            store, policies, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void currentPeriodIsCreatedUsingTenantCadence() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TimesheetSummary expected = summary(userId, "OPEN");
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(policies.find(tenantId)).thenReturn(new TimePolicy(true, 480, false, false, true, "WEEKLY", false, 1, "NEAREST"));
        when(store.findOrCreateCurrent(eq(tenantId), eq(userId), eq("WEEKLY"), any(), any())).thenReturn(expected);

        assertEquals(expected, service.current(tenantId, userId));
        verify(authorization).requirePermission("time.timesheet.read");
    }

    @Test
    void submittingWithApprovalPolicyKeepsPeriodAwaitingManager() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID timesheetId = UUID.randomUUID();
        TimesheetSummary expected = summary(userId, "SUBMITTED");
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(policies.find(tenantId)).thenReturn(new TimePolicy(true, 480, false, false, true, "WEEKLY", false, 1, "NEAREST"));
        when(store.submit(eq(tenantId), eq(userId), eq(timesheetId), eq(false), any())).thenReturn(expected);

        assertEquals(expected, service.submit(tenantId, userId, timesheetId));
        verify(authorization).requirePermission("time.timesheet.submit");
    }

    @Test
    void autoApprovePolicyPassesAutoApprovalToStore() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID timesheetId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(policies.find(tenantId)).thenReturn(new TimePolicy(true, 480, false, false, false, "DAILY", false, 1, "NEAREST"));
        when(store.submit(eq(tenantId), eq(userId), eq(timesheetId), eq(true), any())).thenReturn(summary(userId, "APPROVED"));

        service.submit(tenantId, userId, timesheetId);

        verify(store).submit(eq(tenantId), eq(userId), eq(timesheetId), eq(true), any());
    }

    @Test
    void managerCanApproveSubmittedPeriod() {
        UUID tenantId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID timesheetId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(store.decide(eq(tenantId), eq(timesheetId), eq(approverId), eq(true), eq("Looks good"), any()))
                .thenReturn(summary(UUID.randomUUID(), "APPROVED"));

        TimesheetSummary result = service.decide(tenantId, approverId, timesheetId, true, "Looks good");

        assertEquals("APPROVED", result.status());
        verify(authorization).requirePermission("time.timesheet.approve");
    }

    private static TimesheetSummary summary(UUID userId, String status) {
        return new TimesheetSummary(UUID.randomUUID(), userId, LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 20), "WEEKLY", status, 120, 60, 60, 0, 2,
                null, null, null, null, "");
    }
}
