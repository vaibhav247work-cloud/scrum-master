package com.companyos.backend;

import com.companyos.tenantmanagement.api.TenantOnboardingCommand;
import com.companyos.tenantmanagement.api.TenantOnboardingResult;
import com.companyos.tenantmanagement.api.TenantOnboardingService;
import com.companyos.tenantmanagement.port.TenantOnboardingData;
import com.companyos.tenantmanagement.port.TenantOnboardingStore;
import com.companyos.security.contract.CredentialHasher;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantOnboardingServiceTest {
    private final TenantOnboardingStore store = mock(TenantOnboardingStore.class);
    private final CredentialHasher credentialHasher = mock(CredentialHasher.class);
    private final TenantOnboardingService service = new TenantOnboardingService(store, credentialHasher);

    TenantOnboardingServiceTest() {
        when(credentialHasher.hash("StrongPassword1!" )).thenReturn("$2a$12$test-hash");
    }

    @Test
    void normalizesTenantAndOwnerDataBeforePersisting() {
        TenantOnboardingResult result = service.onboard(new TenantOnboardingCommand(
                " Acme-Engineering ",
                "Acme Engineering LLC",
                "Acme",
                "Asia/Kolkata",
                "Owner@Example.com ",
                " Rahul ",
                "StrongPassword1!",
                Set.of("projects", "time-tracking")));

        assertEquals("acme-engineering", result.tenantKey());
        assertEquals("owner@example.com", result.ownerEmail());
        assertEquals(Set.of("PROJECTS", "TIME_TRACKING"), result.enabledFeatures());
        assertEquals("ACTIVE", result.ownerStatus());
        verify(store).createTenant(org.mockito.ArgumentMatchers.argThat(data ->
                data.tenantKey().equals("acme-engineering")
                        && data.ownerEmail().equals("owner@example.com")
                        && data.enabledFeatures().equals(Set.of("PROJECTS", "TIME_TRACKING"))
                        && data.initialRolePermissions().contains("report.read")));
        assertTrue(result.enabledFeatures().contains("PROJECTS"));
    }

    @Test
    void rejectsInvalidTimezone() {
        assertThrows(IllegalArgumentException.class, () -> service.onboard(new TenantOnboardingCommand(
                "acme", "Acme Engineering LLC", "Acme", "Not/AZone",
                "owner@example.com", "Rahul", "StrongPassword1!", Set.of())));
    }
}
