package com.companyos.auth.local;

import com.companyos.identity.access.TenantUserCredentialStore;
import com.companyos.identity.access.TenantUserCredentials;
import com.companyos.security.contract.CredentialHasher;
import com.companyos.security.contract.SessionData;
import com.companyos.security.contract.SessionStore;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalAuthenticationService {
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final TenantUserCredentialStore credentialStore;
    private final CredentialHasher credentialHasher;
    private final SessionStore sessionStore;
    private final SecureRandom secureRandom = new SecureRandom();

    public LocalAuthenticationService(
            TenantUserCredentialStore credentialStore,
            CredentialHasher credentialHasher,
            SessionStore sessionStore) {
        this.credentialStore = credentialStore;
        this.credentialHasher = credentialHasher;
        this.sessionStore = sessionStore;
    }

    public LocalLoginResult login(LocalLoginCommand command) {
        String tenantKey = normalize(command.tenantKey()).toLowerCase(Locale.ROOT);
        String email = normalize(command.email()).toLowerCase(Locale.ROOT);
        String password = command.password() == null ? "" : command.password();

        TenantUserCredentials credentials = credentialStore
                .findByTenantKeyAndEmail(tenantKey, email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!"ACTIVE".equals(credentials.status())
                || !credentialHasher.matches(password, credentials.passwordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        SessionData session = new SessionData(
                credentials.tenantId(),
                credentials.userId(),
                credentials.tenantKey(),
                credentials.email(),
                credentials.displayName(),
                credentials.authorities(),
                expiresAt);
        String rawToken = credentials.tenantId() + "." + randomToken();
        sessionStore.save(rawToken, session, SESSION_TTL);
        return new LocalLoginResult(rawToken, session, SESSION_TTL);
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            sessionStore.revoke(rawToken);
        }
    }

    public void revokeUserSessions(UUID tenantId, UUID userId) {
        sessionStore.revokeAll(tenantId, userId);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
