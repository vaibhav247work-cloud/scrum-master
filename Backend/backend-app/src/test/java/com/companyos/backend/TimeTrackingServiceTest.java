package com.companyos.backend;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.feature.entitlement.FeatureEntitlementStore;
import com.companyos.idempotency.IdempotencyStore;
import com.companyos.idempotency.IdempotencyRecord;
import com.companyos.idempotency.IdempotencyUnavailableException;
import com.companyos.time.IdempotencyConflictException;
import com.companyos.time.IdempotencyInProgressException;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.time.CreateTimeEntryCommand;
import com.companyos.time.TimeEntryCreationResult;
import com.companyos.time.TimeEntryStore;
import com.companyos.time.TimeEntrySummary;
import com.companyos.time.TimePolicy;
import com.companyos.time.TimePolicyStore;
import com.companyos.time.TimeTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TimeTrackingServiceTest {
    private final TimeEntryStore store = mock(TimeEntryStore.class);
    private final IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
    private final FeatureEntitlementStore entitlements = mock(FeatureEntitlementStore.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final TimePolicyStore timePolicies = mock(TimePolicyStore.class);
    private final TimeTrackingService service = new TimeTrackingService(
            store, idempotencyStore, new FeatureEntitlementService(entitlements), authorization,
            new ObjectMapper().findAndRegisterModules(), timePolicies,
            Duration.ofMinutes(10), Duration.ofHours(24));

    @Test
    void createsUsingValidatedIdempotencyKeyAndRequestHash() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateTimeEntryCommand command = new CreateTimeEntryCommand(
                null, null, null, LocalDate.of(2026, 9, 15), 60, "Build report", "quick_hours");
        TimeEntrySummary entry = new TimeEntrySummary(UUID.randomUUID(), userId, null, null, null,
                command.entryDate(), command.minutes(), command.description(), "QUICK_HOURS", Instant.now());
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(idempotencyStore.find(any(), eq("report-1"))).thenReturn(Optional.empty());
        when(idempotencyStore.reserve(any(), eq("report-1"), matches("[0-9a-f]{64}"), any())).thenReturn(true);
        when(store.create(eq(tenantId), eq(userId), any())).thenReturn(entry);

        TimeEntryCreationResult result = service.create(tenantId, userId, command, " report-1 ");

        assertEquals(entry, result.entry());
        verify(authorization).requirePermission("time.entry.create");
        verify(store).create(eq(tenantId), eq(userId), any());
    }

    @Test
    void rejectsMissingIdempotencyKeyBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 30, "Work", "QUICK_HOURS"), null));

        verifyNoInteractions(store);
    }

    @Test
    void rejectsDisabledTimeTrackingBeforeAuthorizationIdempotencyOrWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(false);

        assertThrows(FeatureEntitlementService.FeatureNotEnabledException.class,
                () -> service.create(tenantId, UUID.randomUUID(),
                        new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 30, "Work", "QUICK_HOURS"),
                        "disabled-time-1"));

        verifyNoInteractions(authorization, idempotencyStore, store);
    }

    @Test
    void rejectsCreateWhenPermissionIsMissingBeforeRedisOrWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN))
                .when(authorization).requirePermission("time.entry.create");

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.create(tenantId, UUID.randomUUID(),
                        new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 30, "Work", "QUICK_HOURS"),
                        "permission-denied-1"));

        verifyNoInteractions(idempotencyStore, store);
    }

    @Test
    void rejectsDisabledRelatedFeatureBeforeReservingIdempotencyOrWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(entitlements.isEnabled(tenantId, "WORK_MANAGEMENT")).thenReturn(false);

        assertThrows(FeatureEntitlementService.FeatureNotEnabledException.class,
                () -> service.create(tenantId, UUID.randomUUID(),
                        new CreateTimeEntryCommand(UUID.randomUUID(), null, null,
                                LocalDate.now(), 30, "Work", "QUICK_HOURS"),
                        "disabled-related-1"));

        verifyNoInteractions(idempotencyStore, store);
    }

    @Test
    void supportsEveryConfiguredEntrySourceThroughTheSameStore() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(idempotencyStore.find(any(), any())).thenReturn(Optional.empty());
        when(idempotencyStore.reserve(any(), any(), any(), any())).thenReturn(true);
        when(store.create(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            CreateTimeEntryCommand command = invocation.getArgument(2);
            return new TimeEntrySummary(UUID.randomUUID(), userId, command.workItemId(), command.projectId(),
                    command.sprintId(), command.entryDate(), command.minutes(), command.description(),
                    command.source(), Instant.now());
        });

        for (String source : new String[]{"QUICK_HOURS", "TIMER", "START_END", "BULK", "SUGGESTED"}) {
            TimeEntryCreationResult result = service.create(tenantId, userId,
                    new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 30,
                            "Source " + source, source.toLowerCase()), "source-" + source);
            assertEquals(source, result.entry().source());
        }

        verify(store, org.mockito.Mockito.times(5)).create(eq(tenantId), eq(userId), any());
    }

    @Test
    void rejectsInvalidTimeValuesBeforeRedisOrWriting() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 0, "Work", "QUICK_HOURS"), "bad-1"));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateTimeEntryCommand(null, null, null, LocalDate.now().plusDays(1), 30, "Work", "QUICK_HOURS"), "bad-2"));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 30, "Work", "UNSUPPORTED"), "bad-3"));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 30, "Work", "QUICK_HOURS"), "bad key"));

        verifyNoInteractions(idempotencyStore, store);
    }

    @Test
    void appliesConfiguredRoundingBeforeWriting() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(timePolicies.find(tenantId)).thenReturn(new TimePolicy(true, 480, true, true, 15, "UP"));
        when(idempotencyStore.find(any(), any())).thenReturn(Optional.empty());
        when(idempotencyStore.reserve(any(), any(), any(), any())).thenReturn(true);
        when(store.create(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            CreateTimeEntryCommand command = invocation.getArgument(2);
            return new TimeEntrySummary(UUID.randomUUID(), userId, null, null, null,
                    command.entryDate(), command.minutes(), command.description(), command.source(), Instant.now());
        });

        service.create(tenantId, userId,
                new CreateTimeEntryCommand(null, null, null, LocalDate.of(2026, 9, 15), 31,
                        "Rounded work", "QUICK_HOURS"), "rounding-1");

        org.mockito.Mockito.verify(store).create(eq(tenantId), eq(userId), org.mockito.ArgumentMatchers.argThat(
                command -> command.minutes() == 45));
    }

    @Test
    void rejectsWeekendWhenTenantPolicyDisablesWeekendEntries() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(timePolicies.find(tenantId)).thenReturn(new TimePolicy(true, 480, false, false, 1, "NEAREST"));

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateTimeEntryCommand(null, null, null, LocalDate.of(2026, 9, 19), 30,
                        "Weekend work", "QUICK_HOURS"), "weekend-1"));

        verifyNoInteractions(idempotencyStore, store);
    }

    @Test
    void rejectsEntryThatWouldExceedConfiguredDailyLimit() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(timePolicies.find(tenantId)).thenReturn(new TimePolicy(true, 60, false, true, 1, "NEAREST"));
        when(idempotencyStore.find(any(), eq("limit-1"))).thenReturn(Optional.empty());
        when(idempotencyStore.reserve(any(), eq("limit-1"), any(), any())).thenReturn(true);
        when(store.sumMinutesForUserOnDate(tenantId, userId, LocalDate.of(2026, 9, 15))).thenReturn(45);

        assertThrows(IllegalArgumentException.class, () -> service.create(tenantId, userId,
                new CreateTimeEntryCommand(null, null, null, LocalDate.of(2026, 9, 15), 30,
                        "Too much time", "QUICK_HOURS"), "limit-1"));

        verifyNoInteractions(idempotencyStore);
        verify(store, org.mockito.Mockito.never()).create(any(), any(), any());
    }

    @Test
    void rejectsConcurrentRequestWhileSameKeyIsProcessing() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        String matchingHash = hash("|||2026-09-15|30|Work|QUICK_HOURS");
        when(idempotencyStore.find(any(), eq("race-1"))).thenReturn(Optional.of(
                new IdempotencyRecord("scope", "race-1", matchingHash, "PROCESSING", null, Instant.now().plusSeconds(60))));

        assertThrows(IdempotencyInProgressException.class, () -> service.create(tenantId, userId,
                new CreateTimeEntryCommand(null, null, null, LocalDate.of(2026, 9, 15), 30, "Work", "QUICK_HOURS"), "race-1"));

        verifyNoInteractions(store);
    }

    @Test
    void rejectsSameKeyWhenPayloadHashDoesNotMatch() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(idempotencyStore.find(any(), eq("reuse-1"))).thenReturn(Optional.of(
                new IdempotencyRecord("scope", "reuse-1", "different-hash", "COMPLETED", "{}", Instant.now().plusSeconds(60))));

        assertThrows(IdempotencyConflictException.class, () -> service.create(tenantId, userId,
                new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 30, "Work", "QUICK_HOURS"), "reuse-1"));

        verifyNoInteractions(store);
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        UUID tenantId = UUID.randomUUID();
        when(entitlements.isEnabled(tenantId, "TIME_TRACKING")).thenReturn(true);
        when(idempotencyStore.find(any(), any())).thenThrow(new IdempotencyUnavailableException(new IllegalStateException("redis down")));

        assertThrows(IdempotencyUnavailableException.class, () -> service.create(tenantId, UUID.randomUUID(),
                new CreateTimeEntryCommand(null, null, null, LocalDate.now(), 30, "Work", "QUICK_HOURS"), "outage-1"));

        verifyNoInteractions(store);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
