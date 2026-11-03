package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.reporting.DeliveryForecastRow;
import com.companyos.reporting.DeliveryForecastService;
import com.companyos.reporting.DeliveryForecastStore;
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

class DeliveryForecastServiceTest {
    private final DeliveryForecastStore forecasts = mock(DeliveryForecastStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final DeliveryForecastService service = new DeliveryForecastService(
            forecasts, new FeatureEntitlementService(entitlements), authorization);

    @Test
    void requiresReportingFeatureAndReportPermissionBeforeReading() {
        UUID tenantId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 10, 1);
        List<DeliveryForecastRow> expected = List.of(new DeliveryForecastRow(
                UUID.randomUUID(), "WEB", "Web", 4800, 3600, 3, 7, 70,
                1, 2, 0, 0, "ON_TRACK"));
        when(entitlements.isEnabled(tenantId, "REPORTING")).thenReturn(true);
        when(forecasts.forecast(tenantId, from, to, null)).thenReturn(expected);

        assertEquals(expected, service.forecast(tenantId, from, to, null));

        verify(authorization).requirePermission("report.read");
        verify(forecasts).forecast(tenantId, from, to, null);
    }

    @Test
    void blocksDisabledReportingBeforeStoreAccess() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "REPORTING")).thenReturn(false);

        assertThrows(FeatureEntitlementService.FeatureNotEnabledException.class,
                () -> service.forecast(tenantId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), null));

        verifyNoInteractions(authorization, forecasts);
    }

    @Test
    void rejectsForecastRangesLongerThanTwelveMonths() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "REPORTING")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.forecast(tenantId, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 2, 1), null));

        verifyNoInteractions(forecasts);
    }
}
