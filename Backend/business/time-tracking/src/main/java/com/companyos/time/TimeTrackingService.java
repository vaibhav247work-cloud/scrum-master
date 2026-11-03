package com.companyos.time;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.idempotency.IdempotencyRecord;
import com.companyos.idempotency.IdempotencyStore;
import com.companyos.security.contract.AuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;

@Service
public class TimeTrackingService {
    private static final Set<String> SOURCES = Set.of("QUICK_HOURS", "TIMER", "START_END", "BULK", "SUGGESTED");
    private final TimeEntryStore store;
    private final IdempotencyStore idempotencyStore;
    private final FeatureEntitlementService featureEntitlements;
    private final AuthorizationService authorization;
    private final ObjectMapper objectMapper;
    private final TimePolicyStore timePolicyStore;
    private final TimeCategoryStore timeCategoryStore;
    private final TimesheetStore timesheetStore;
    private final Duration processingTtl;
    private final Duration completedTtl;

    @Autowired
    public TimeTrackingService(
            TimeEntryStore store,
            IdempotencyStore idempotencyStore,
            FeatureEntitlementService featureEntitlements,
            AuthorizationService authorization,
            ObjectMapper objectMapper,
            TimePolicyStore timePolicyStore,
            TimeCategoryStore timeCategoryStore,
            TimesheetStore timesheetStore,
            @Value("${idempotency.processing-ttl:PT10M}") Duration processingTtl,
            @Value("${idempotency.completed-ttl:PT24H}") Duration completedTtl) {
        this.store = store;
        this.idempotencyStore = idempotencyStore;
        this.featureEntitlements = featureEntitlements;
        this.authorization = authorization;
        this.objectMapper = objectMapper;
        this.timePolicyStore = timePolicyStore;
        this.timeCategoryStore = timeCategoryStore;
        this.timesheetStore = timesheetStore;
        this.processingTtl = processingTtl;
        this.completedTtl = completedTtl;
    }

    public TimeTrackingService(TimeEntryStore store, IdempotencyStore idempotencyStore,
                FeatureEntitlementService featureEntitlements, AuthorizationService authorization,
                               ObjectMapper objectMapper, TimePolicyStore timePolicyStore,
                               Duration processingTtl, Duration completedTtl) {
        this(store, idempotencyStore, featureEntitlements, authorization, objectMapper, timePolicyStore,
                null, null, processingTtl, completedTtl);
    }

    @Transactional
    public TimeEntryCreationResult create(UUID tenantId, UUID userId, CreateTimeEntryCommand command, String idempotencyKey) {
        featureEntitlements.requireEnabled(tenantId, "TIME_TRACKING");
        authorization.requirePermission("time.entry.create");
        CreateTimeEntryCommand normalized = normalize(command);
        String key = normalizeIdempotencyKey(idempotencyKey);
        requireRelatedFeatures(tenantId, normalized);
        // Reject company-policy violations before reserving Redis idempotency state.
        // Invalid requests must not leave short-lived processing garbage behind.
        normalized = applyPolicy(tenantId, userId, normalized);
        if (timesheetStore != null && timesheetStore.isDateLocked(tenantId, userId, normalized.entryDate())) {
            throw new IllegalArgumentException("This date belongs to an approved timesheet and is locked");
        }
        String requestHash = requestHash(normalized);
        String scope = tenantId + ":" + userId + ":TIME_ENTRY";
        IdempotencyRecord existing = idempotencyStore.find(scope, key).orElse(null);
        if (existing != null) return replayOrReject(existing, requestHash);
        if (!idempotencyStore.reserve(scope, key, requestHash, processingTtl)) {
            existing = idempotencyStore.find(scope, key).orElse(null);
            if (existing == null) throw new IdempotencyInProgressException();
            return replayOrReject(existing, requestHash);
        }
        TimeEntrySummary created;
        try {
            TimePolicy policy = timePolicyStore.find(tenantId);
            timesheetStoreEnsurePeriod(tenantId, userId, policy == null ? "WEEKLY" : policy.validated().approvalCadence(), normalized.entryDate());
            created = store.create(tenantId, userId, normalized);
        } catch (RuntimeException exception) {
            releaseReservation(scope, key, requestHash);
            throw exception;
        }
        try {
            String responseJson = objectMapper.writeValueAsString(created);
            String finalScope = scope;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        idempotencyStore.complete(finalScope, key, requestHash, responseJson, completedTtl);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) releaseReservation(finalScope, key, requestHash);
                    }
                });
            } else {
                idempotencyStore.complete(finalScope, key, requestHash, responseJson, completedTtl);
            }
        } catch (Exception exception) {
            releaseReservation(scope, key, requestHash);
            throw new IllegalStateException("Unable to store idempotent response", exception);
        }
        return new TimeEntryCreationResult(created, false);
    }

    private void timesheetStoreEnsurePeriod(UUID tenantId, UUID userId, String cadence, LocalDate entryDate) {
        if (timesheetStore != null) {
            timesheetStore.ensurePeriodForDate(tenantId, userId, cadence, entryDate, java.time.Instant.now());
        }
    }

    private void releaseReservation(String scope, String key, String requestHash) {
        try {
            idempotencyStore.release(scope, key, requestHash);
        } catch (RuntimeException ignored) {
            // The processing TTL remains the final safety net if Redis is unavailable during cleanup.
        }
    }

    @Transactional(readOnly = true)
    public List<TimeEntrySummary> findForUser(UUID tenantId, UUID userId) {
        featureEntitlements.requireEnabled(tenantId, "TIME_TRACKING");
        authorization.requirePermission("time.entry.read");
        return store.findForUser(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<TimeEntrySummary> findPending(UUID tenantId) {
        featureEntitlements.requireEnabled(tenantId, "TIME_TRACKING");
        authorization.requirePermission("time.entry.approve");
        return store.findPending(tenantId);
    }

    @Transactional
    public TimeEntrySummary decide(UUID tenantId, UUID approverId, UUID entryId, boolean approved, String note) {
        featureEntitlements.requireEnabled(tenantId, "TIME_TRACKING");
        authorization.requirePermission("time.entry.approve");
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedNote.length() > 1000) throw new IllegalArgumentException("approval note must be at most 1000 characters");
        return store.decide(tenantId, entryId, approverId, approved, normalizedNote);
    }

    private void requireRelatedFeatures(UUID tenantId, CreateTimeEntryCommand command) {
        if (command != null && command.workItemId() != null) {
            featureEntitlements.requireEnabled(tenantId, "WORK_MANAGEMENT");
        }
        if (command != null && command.projectId() != null) {
            featureEntitlements.requireEnabled(tenantId, "PROJECTS");
        }
        if (command != null && command.sprintId() != null) {
            featureEntitlements.requireEnabled(tenantId, "PROJECTS");
            featureEntitlements.requireEnabled(tenantId, "SCRUM");
        }
    }

    private CreateTimeEntryCommand applyPolicy(UUID tenantId, UUID userId, CreateTimeEntryCommand command) {
        TimePolicy policy = timePolicyStore.find(tenantId);
        if (policy == null) policy = TimePolicy.defaults();
        policy = policy.validated();
        String approvalStatus = policy.approvalEnabled() && !policy.autoApprove() ? "PENDING" : "APPROVED";
        String categoryKey = normalizeCategoryKey(command.categoryKey());
        if (timeCategoryStore != null && timeCategoryStore.hasConfiguredCategories(tenantId)
                && !timeCategoryStore.isEnabled(tenantId, categoryKey)) {
            throw new IllegalArgumentException("time category is not enabled for this company: " + categoryKey);
        }
        if (!policy.enabled()) {
            return new CreateTimeEntryCommand(command.workItemId(), command.projectId(), command.sprintId(),
                    command.entryDate(), command.minutes(), command.description(), command.source(), categoryKey, approvalStatus);
        }
        if (!policy.allowWeekend() && switch (command.entryDate().getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> false;
        }) {
            throw new IllegalArgumentException("Weekend time entries are disabled for this company");
        }
        int roundedMinutes = policy.roundMinutes(command.minutes());
        if (roundedMinutes < 1 || roundedMinutes > 1440) {
            throw new IllegalArgumentException("Rounded minutes must be between 1 and 1440");
        }
        if (!policy.allowOvertime()) {
            int existingMinutes = store.sumMinutesForUserOnDate(tenantId, userId, command.entryDate());
            if (existingMinutes + roundedMinutes > policy.expectedDailyMinutes()) {
                throw new IllegalArgumentException("Daily time limit exceeded for this company");
            }
        }
        return new CreateTimeEntryCommand(command.workItemId(), command.projectId(), command.sprintId(),
                command.entryDate(), roundedMinutes, command.description(), command.source(), categoryKey, approvalStatus);
    }

    private static CreateTimeEntryCommand normalize(CreateTimeEntryCommand command) {
        if (command == null) throw new IllegalArgumentException("request body is required");
        if (command.workItemId() != null && (command.projectId() != null || command.sprintId() != null)) {
            throw new IllegalArgumentException("Use either workItemId or direct project/sprint context, not both");
        }
        if (command.sprintId() != null && command.projectId() == null) {
            throw new IllegalArgumentException("projectId is required when sprintId is provided");
        }
        if (command.minutes() < 1 || command.minutes() > 1440) {
            throw new IllegalArgumentException("minutes must be between 1 and 1440");
        }
        String description = command.description() == null ? "" : command.description().trim();
        if (description.length() > 4000) throw new IllegalArgumentException("description must be at most 4000 characters");
        String source = command.source() == null || command.source().isBlank()
                ? "QUICK_HOURS" : command.source().trim().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(source)) throw new IllegalArgumentException("source is invalid");
        LocalDate entryDate = command.entryDate() == null ? LocalDate.now() : command.entryDate();
        if (entryDate.isAfter(LocalDate.now())) throw new IllegalArgumentException("entryDate cannot be in the future");
        return new CreateTimeEntryCommand(command.workItemId(), command.projectId(), command.sprintId(), entryDate,
                command.minutes(), description, source, normalizeCategoryKey(command.categoryKey()), command.approvalStatus());
    }

    private static String normalizeIdempotencyKey(String key) {
        String normalized = key == null ? "" : key.trim();
        if (normalized.isEmpty() || normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 128 letters, numbers, '.', '_', ':' or '-'");
        }
        return normalized;
    }

    private TimeEntryCreationResult replayOrReject(IdempotencyRecord existing, String requestHash) {
        if (!requestHash.equals(existing.requestHash())) throw new IdempotencyConflictException();
        if (existing.processing()) throw new IdempotencyInProgressException();
        if (!existing.completed() || existing.responseJson() == null) throw new IdempotencyInProgressException();
        try {
            return new TimeEntryCreationResult(objectMapper.readValue(existing.responseJson(), TimeEntrySummary.class), true);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to replay idempotent response", exception);
        }
    }

    private static String requestHash(CreateTimeEntryCommand command) {
        String material = String.join("|", value(command.workItemId()), value(command.projectId()),
                value(command.sprintId()), command.entryDate().toString(), Integer.toString(command.minutes()),
                command.description(), command.source());
        if (!"GENERAL".equals(command.categoryKey())) material += "|" + command.categoryKey();
        try {
            return HexFormatHolder.format(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String value(Object value) { return value == null ? "" : value.toString(); }

    private static String normalizeCategoryKey(String value) {
        String key = value == null || value.isBlank() ? "GENERAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!key.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("categoryKey is invalid");
        return key;
    }

    private static final class HexFormatHolder {
        private static String format(byte[] bytes) {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        }
    }
}
