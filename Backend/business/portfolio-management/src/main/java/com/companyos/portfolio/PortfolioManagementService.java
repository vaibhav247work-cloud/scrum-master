package com.companyos.portfolio;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PortfolioManagementService {
    private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,47}");
    private static final Set<String> STATUSES = Set.of("PLANNING", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED", "ARCHIVED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private final PortfolioManagementStore store;
    private final FeatureEntitlementService features;
    private final AuthorizationService authorization;

    public PortfolioManagementService(PortfolioManagementStore store, FeatureEntitlementService features,
                                      AuthorizationService authorization) {
        this.store = store;
        this.features = features;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<PortfolioSummary> portfolios(UUID tenantId) {
        requireRead(tenantId);
        return store.findPortfolios(tenantId);
    }

    @Transactional
    public PortfolioSummary createPortfolio(UUID tenantId, UUID actorId, CreatePortfolioCommand command) {
        requireManage(tenantId);
        if (command == null) throw new IllegalArgumentException("request body is required");
        return store.createPortfolio(tenantId, actorId, new CreatePortfolioCommand(
                key(command.portfolioKey(), "portfolioKey"), required(command.name(), "name", 200),
                bounded(command.description(), "description", 4000), status(command.status())), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ProgramSummary> programs(UUID tenantId, UUID portfolioId) {
        requireRead(tenantId);
        return store.findPrograms(tenantId, requiredId(portfolioId, "portfolioId"));
    }

    @Transactional
    public ProgramSummary createProgram(UUID tenantId, UUID actorId, UUID portfolioId, CreateProgramCommand command) {
        requireManage(tenantId);
        UUID parentId = requiredId(portfolioId, "portfolioId");
        if (command == null) throw new IllegalArgumentException("request body is required");
        return store.createProgram(tenantId, actorId, parentId, new CreateProgramCommand(
                key(command.programKey(), "programKey"), required(command.name(), "name", 200),
                bounded(command.description(), "description", 4000), status(command.status())), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<InitiativeSummary> initiatives(UUID tenantId, UUID portfolioId, UUID programId) {
        requireRead(tenantId);
        return store.findInitiatives(tenantId, portfolioId, programId);
    }

    @Transactional
    public InitiativeSummary createInitiative(UUID tenantId, UUID actorId, CreateInitiativeCommand command) {
        requireManage(tenantId);
        if (command == null) throw new IllegalArgumentException("request body is required");
        UUID portfolioId = requiredId(command.portfolioId(), "portfolioId");
        return store.createInitiative(tenantId, actorId, new CreateInitiativeCommand(
                portfolioId, command.programId(), command.productId(), key(command.initiativeKey(), "initiativeKey"),
                required(command.title(), "title", 240), bounded(command.description(), "description", 4000),
                priority(command.priority()), command.targetDate()), Instant.now());
    }

    private void requireRead(UUID tenantId) {
        features.requireEnabled(tenantId, "PORTFOLIO_PLANNING");
        authorization.requirePermission("portfolio.read");
    }

    private void requireManage(UUID tenantId) {
        features.requireEnabled(tenantId, "PORTFOLIO_PLANNING");
        authorization.requirePermission("portfolio.manage");
    }

    private static String key(String value, String field) {
        String normalized = required(value, field, 48).toUpperCase(Locale.ROOT).replace('-', '_');
        if (!KEY.matcher(normalized).matches()) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }

    private static String status(String value) {
        String normalized = value == null || value.isBlank() ? "PLANNING" : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("status is invalid");
        return normalized;
    }

    private static String priority(String value) {
        String normalized = value == null || value.isBlank() ? "MEDIUM" : value.trim().toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(normalized)) throw new IllegalArgumentException("priority is invalid");
        return normalized;
    }

    private static UUID requiredId(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }

    private static String bounded(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        return normalized;
    }
}
