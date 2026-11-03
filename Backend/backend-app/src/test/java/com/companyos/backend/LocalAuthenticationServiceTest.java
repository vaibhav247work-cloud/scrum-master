package com.companyos.backend;

import com.companyos.auth.local.LocalAuthenticationService;
import com.companyos.auth.local.LocalLoginCommand;
import com.companyos.auth.local.LocalLoginResult;
import com.companyos.identity.access.TenantUserCredentialStore;
import com.companyos.identity.access.TenantUserCredentials;
import com.companyos.security.contract.CredentialHasher;
import com.companyos.security.contract.SessionStore;
import org.springframework.security.authentication.BadCredentialsException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalAuthenticationServiceTest {
    private final TenantUserCredentialStore credentialStore = mock(TenantUserCredentialStore.class);
    private final CredentialHasher credentialHasher = mock(CredentialHasher.class);
    private final SessionStore sessionStore = mock(SessionStore.class);
    private final LocalAuthenticationService service = new LocalAuthenticationService(
            credentialStore, credentialHasher, sessionStore);

    @Test
    void createsTenantBoundSessionForValidCredentials() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(credentialStore.findByTenantKeyAndEmail("acme", "owner@example.com"))
                .thenReturn(java.util.Optional.of(new TenantUserCredentials(
                        tenantId, userId, "acme", "owner@example.com", "Owner",
                        "encoded", "ACTIVE", Set.of("ROLE_TENANT_ADMIN"))));
        when(credentialHasher.matches("StrongPassword1!", "encoded")).thenReturn(true);

        LocalLoginResult result = service.login(new LocalLoginCommand(
                " Acme ", "Owner@Example.com", "StrongPassword1!"));

        assertTrue(result.rawToken().startsWith(tenantId + "."));
        assertTrue(result.session().tenantId().equals(tenantId));
        verify(sessionStore).save(anyString(), any(), any());
    }

    @Test
    void rejectsSuspendedUsersBeforeCreatingASession() {
        when(credentialStore.findByTenantKeyAndEmail("acme", "owner@example.com"))
                .thenReturn(java.util.Optional.of(credentialsWithStatus("SUSPENDED")));

        assertThrows(BadCredentialsException.class, () -> service.login(new LocalLoginCommand(
                "acme", "owner@example.com", "StrongPassword1!")));

        verify(credentialHasher, never()).matches(anyString(), anyString());
        verify(sessionStore, never()).save(anyString(), any(), any());
    }

    @Test
    void rejectsOffboardedUsersBeforeCreatingASession() {
        when(credentialStore.findByTenantKeyAndEmail("acme", "owner@example.com"))
                .thenReturn(java.util.Optional.of(credentialsWithStatus("OFFBOARDED")));

        assertThrows(BadCredentialsException.class, () -> service.login(new LocalLoginCommand(
                "acme", "owner@example.com", "StrongPassword1!")));

        verify(credentialHasher, never()).matches(anyString(), anyString());
        verify(sessionStore, never()).save(anyString(), any(), any());
    }

    @Test
    void revokesAllSessionsForAUserThroughTheTenantScopedStore() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.revokeUserSessions(tenantId, userId);

        verify(sessionStore).revokeAll(tenantId, userId);
    }

    private static TenantUserCredentials credentialsWithStatus(String status) {
        return new TenantUserCredentials(
                UUID.randomUUID(), UUID.randomUUID(), "acme", "owner@example.com", "Owner",
                "encoded", status, Set.of("ROLE_TENANT_ADMIN"));
    }
}
