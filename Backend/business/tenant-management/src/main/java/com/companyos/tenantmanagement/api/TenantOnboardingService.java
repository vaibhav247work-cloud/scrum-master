package com.companyos.tenantmanagement.api;

import com.companyos.security.contract.CredentialHasher;
import com.companyos.tenantmanagement.port.TenantOnboardingData;
import com.companyos.tenantmanagement.port.TenantOnboardingStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TenantOnboardingService {
    private static final String INITIAL_ROLE_KEY = "COMPANY_ADMIN";
    private static final Set<String> INITIAL_ROLE_PERMISSIONS = Set.of(
            "tenant.user.read", "tenant.user.manage", "tenant.settings.manage",
            "tenant.lifecycle.read", "tenant.lifecycle.manage", "tenant.data.export",
            "retention.read", "retention.manage", "retention.execute",
            "feature.manage", "project.read", "project.manage", "sprint.read", "sprint.manage",
            "product.read", "product.manage",
            "portfolio.read", "portfolio.manage",
            "migration.read", "migration.execute",
            "work.read", "work.manage", "work.export", "time.read", "time.manage",
            "work.change.request", "work.change.approve",
            "time.entry.create", "time.entry.read", "time.entry.approve", "time.category.read", "time.category.manage",
            "time.timesheet.read", "time.timesheet.submit", "time.timesheet.approve", "resource.workload.read", "resource.capacity.manage",
            "demand.create", "demand.read", "demand.approve",
            "requirement.create", "requirement.read", "requirement.manage", "requirement.review",
            "workflow.read", "workflow.manage",
            "quality.read", "quality.manage", "quality.waive", "incident.read", "incident.manage",
            "resource.allocation.read", "resource.allocation.manage",
            "release.read", "release.manage", "dependency.read", "dependency.manage", "control.tower.read", "report.read",
            "integration.delivery.read", "integration.delivery.manage",
            "integration.connection.read", "integration.connection.manage", "integration.event.ingest",
            "notification.channel.read", "notification.channel.manage",
            "organization.read", "organization.manage", "team.read", "team.manage");
    private static final Pattern TENANT_KEY = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,78}[a-z0-9])?");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final TenantOnboardingStore store;
    private final CredentialHasher credentialHasher;

    public TenantOnboardingService(TenantOnboardingStore store, CredentialHasher credentialHasher) {
        this.store = store;
        this.credentialHasher = credentialHasher;
    }

    @Transactional
    public TenantOnboardingResult onboard(TenantOnboardingCommand command) {
        String tenantKey = normalize(command.tenantKey()).toLowerCase(Locale.ROOT);
        String legalName = required(command.legalName(), "legalName", 240);
        String displayName = required(command.displayName(), "displayName", 240);
        String timezone = required(command.timezone(), "timezone", 80);
        String ownerEmail = required(command.ownerEmail(), "ownerEmail", 320).toLowerCase(Locale.ROOT);
        String ownerDisplayName = required(command.ownerDisplayName(), "ownerDisplayName", 200);
        String ownerPassword = required(command.ownerPassword(), "ownerPassword", 128);

        if (!TENANT_KEY.matcher(tenantKey).matches()) {
            throw new IllegalArgumentException("tenantKey must contain lowercase letters, numbers, or hyphens");
        }
        if (!EMAIL.matcher(ownerEmail).matches()) {
            throw new IllegalArgumentException("ownerEmail is invalid");
        }
        validatePassword(ownerPassword);
        try {
            ZoneId.of(timezone);
        } catch (Exception exception) {
            throw new IllegalArgumentException("timezone is invalid", exception);
        }

        Set<String> features = normalizeFeatures(command.enabledFeatures());
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID initialRoleId = UUID.randomUUID();
        store.createTenant(new TenantOnboardingData(
                tenantId,
                tenantKey,
                legalName,
                displayName,
                timezone,
                ownerId,
                ownerEmail,
                ownerDisplayName,
                credentialHasher.hash(ownerPassword),
                initialRoleId,
                INITIAL_ROLE_KEY,
                INITIAL_ROLE_PERMISSIONS,
                features,
                Instant.now()));

        return new TenantOnboardingResult(
                tenantId,
                ownerId,
                tenantKey,
                ownerEmail,
                features,
                "ACTIVE",
                "ACTIVE");
    }

    private static Set<String> normalizeFeatures(Set<String> requestedFeatures) {
        if (requestedFeatures == null) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        requestedFeatures.forEach(feature -> {
            String value = required(feature, "enabledFeatures", 100)
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_');
            normalized.add(value);
        });
        return Set.copyOf(normalized);
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void validatePassword(String password) {
        if (password.length() < 12
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().noneMatch(character -> !Character.isLetterOrDigit(character))) {
            throw new IllegalArgumentException(
                    "ownerPassword must be at least 12 characters and include upper, lower, digit, and special characters");
        }
    }
}
