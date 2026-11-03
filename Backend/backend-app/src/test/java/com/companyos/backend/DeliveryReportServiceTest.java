package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.reporting.DeliveryReportRow;
import com.companyos.reporting.DeliveryReportService;
import com.companyos.reporting.DeliveryReportStore;
import com.companyos.security.contract.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeliveryReportServiceTest {
    private final DeliveryReportStore reports = mock(DeliveryReportStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final DeliveryReportService service = new DeliveryReportService(
            reports, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void requiresReportingFeatureAndReportPermissionBeforeReading() {
        UUID tenantId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 10, 1);
        List<DeliveryReportRow> expected = List.of(new DeliveryReportRow(
                UUID.randomUUID(), "WEB", "Web", 2, 4, 300, 1, 0));
        when(entitlements.isEnabled(tenantId, "REPORTING")).thenReturn(true);
        when(reports.delivery(tenantId, from, to, null)).thenReturn(expected);

        assertEquals(expected, service.delivery(tenantId, from, to, null));

        verify(authorization).requirePermission("report.read");
        verify(reports).delivery(tenantId, from, to, null);
    }

    @Test
    void blocksDisabledReportingBeforeStoreAccess() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "REPORTING")).thenReturn(false);

        assertThrows(FeatureEntitlementService.FeatureNotEnabledException.class,
                () -> service.delivery(tenantId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), null));

        verifyNoInteractions(authorization, reports);
    }

    @Test
    void rejectsReportRangesLongerThanTwelveMonths() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "REPORTING")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.delivery(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 2, 1), null));

        verifyNoInteractions(reports);
    }
}
