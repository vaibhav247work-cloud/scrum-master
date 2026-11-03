package com.companyos.workflow;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.security.contract.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BoardConfigurationService {
    private static final Set<String> GROUPINGS = Set.of("STATUS", "ASSIGNEE", "TEAM", "PRIORITY");
    private static final Set<String> SWIMLANES = Set.of("NONE", "ASSIGNEE", "TEAM", "PARENT");
    private static final Set<String> CARD_FIELDS = Set.of("KEY", "TITLE", "PRIORITY", "ASSIGNEE", "TEAM", "ESTIMATE", "ACCEPTANCE");
    private static final List<String> DEFAULT_CARD_FIELDS = List.of("KEY", "TITLE", "PRIORITY", "ASSIGNEE");

    private final BoardConfigurationStore store;
    private final FeatureEntitlementService entitlements;
    private final AuthorizationService authorization;

    public BoardConfigurationService(BoardConfigurationStore store, FeatureEntitlementService entitlements,
                                     AuthorizationService authorization) {
        this.store = store;
        this.entitlements = entitlements;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public BoardConfiguration get(UUID tenantId, UUID projectId) {
        require(tenantId, "workflow.read");
        return store.find(tenantId, requiredId(projectId, "projectId"));
    }

    @Transactional
    public BoardConfiguration update(UUID tenantId, UUID actorId, UUID projectId, BoardConfigurationCommand command) {
        require(tenantId, "workflow.manage");
        return store.upsert(tenantId, actorId, requiredId(projectId, "projectId"), normalize(command));
    }

    private void require(UUID tenantId, String permission) {
        entitlements.requireEnabled(tenantId, "PLANNING_WORKFLOWS");
        authorization.requirePermission(permission);
    }

    private static BoardConfigurationCommand normalize(BoardConfigurationCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        String grouping = enumValue(command.grouping(), "grouping", GROUPINGS, "STATUS");
        String swimlane = enumValue(command.swimlane(), "swimlane", SWIMLANES, "NONE");
        List<String> cardFields = new ArrayList<>();
        List<String> requested = command.cardFields() == null || command.cardFields().isEmpty()
                ? DEFAULT_CARD_FIELDS : command.cardFields();
        if (requested.size() > CARD_FIELDS.size()) throw new IllegalArgumentException("too many card fields");
        for (String field : requested) {
            String normalized = required(field, "cardFields", 32).toUpperCase(Locale.ROOT);
            if (!CARD_FIELDS.contains(normalized) || !cardFields.add(normalized) && cardFields.contains(normalized)) {
                throw new IllegalArgumentException("cardFields contains an invalid or duplicate field");
            }
        }
        Map<String, Integer> wipLimits = new LinkedHashMap<>();
        if (command.wipLimits() != null) {
            if (command.wipLimits().size() > 24) throw new IllegalArgumentException("too many WIP limits");
            command.wipLimits().forEach((key, value) -> {
                String normalized = required(key, "wipLimits key", 32).toUpperCase(Locale.ROOT);
                if (value == null || value < 0 || value > 10000) throw new IllegalArgumentException("WIP limit is invalid");
                if (wipLimits.put(normalized, value) != null) throw new IllegalArgumentException("duplicate WIP limit");
            });
        }
        return new BoardConfigurationCommand(grouping, swimlane, List.copyOf(new LinkedHashSet<>(cardFields)), Map.copyOf(wipLimits));
    }

    private static String enumValue(String value, String field, Set<String> allowed, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }

    private static UUID requiredId(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String required(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
