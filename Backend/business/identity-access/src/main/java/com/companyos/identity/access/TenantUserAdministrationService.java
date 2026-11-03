package com.companyos.identity.access;

import com.companyos.security.contract.CredentialHasher;
import com.companyos.security.contract.SessionStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TenantUserAdministrationService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Duration INVITATION_TTL = Duration.ofHours(48);

    private final TenantUserAdministrationStore store;
    private final CredentialHasher credentialHasher;
    private final SessionStore sessionStore;
    private final SecureRandom secureRandom = new SecureRandom();

    public TenantUserAdministrationService(
            TenantUserAdministrationStore store,
            CredentialHasher credentialHasher,
            SessionStore sessionStore) {
        this.store = store;
        this.credentialHasher = credentialHasher;
        this.sessionStore = sessionStore;
    }

    @Transactional
    public TenantUserInvitationResult invite(UUID tenantId, UUID actorId, InviteUserCommand command) {
        String email = required(command.email(), "email", 320).toLowerCase(Locale.ROOT);
        String displayName = required(command.displayName(), "displayName", 200);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email is invalid");
        }
        Set<String> roleKeys = normalizeRoles(command.roleKeys());
        if (roleKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }

        UUID invitationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String rawToken = randomToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(INVITATION_TTL);
        store.createInvitation(new TenantUserInvitationData(
                invitationId, tenantId, userId, email, displayName,
                hashToken(rawToken), expiresAt, actorId, roleKeys, now));
        return new TenantUserInvitationResult(invitationId, userId, email, expiresAt, roleKeys, rawToken);
    }

    @Transactional
    public void activate(UUID invitationId, String rawToken, String password) {
        validatePassword(password);
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("activation token is required");
        }
        boolean activated = store.activateInvitation(
                invitationId, hashToken(rawToken), credentialHasher.hash(password), Instant.now());
        if (!activated) {
            throw new IllegalArgumentException("Invitation is invalid, expired, or already used");
        }
    }

    @Transactional(readOnly = true)
    public Set<TenantRoleSummary> roles(UUID tenantId) {
        return Set.copyOf(store.findRoles(tenantId));
    }

    @Transactional
    public void replaceRoles(UUID tenantId, UUID userId, Set<String> roleKeys) {
        Set<String> normalized = normalizeRoles(roleKeys);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        store.replaceRoles(tenantId, userId, normalized);
    }

    @Transactional
    public void changeStatus(UUID tenantId, UUID actorId, UUID userId, String status) {
        if (actorId.equals(userId)) {
            throw new IllegalArgumentException("A user cannot offboard or suspend their own account");
        }
        String normalized = required(status, "status", 32).toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "OFFBOARDED", "SUSPENDED").contains(normalized)) {
            throw new IllegalArgumentException("status must be ACTIVE, SUSPENDED, or OFFBOARDED");
        }
        store.updateStatus(tenantId, userId, normalized, "OFFBOARDED".equals(normalized) ? Instant.now() : null);
        sessionStore.revokeAll(tenantId, userId);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Set<String> normalizeRoles(Set<String> values) {
        if (values == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(required(value, "roleKeys", 100).toUpperCase(Locale.ROOT)));
        return Set.copyOf(result);
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().noneMatch(character -> !Character.isLetterOrDigit(character))) {
            throw new IllegalArgumentException(
                    "password must be at least 12 characters and include upper, lower, digit, and special characters");
        }
    }
}
