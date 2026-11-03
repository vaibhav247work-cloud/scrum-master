package com.companyos.backend;

import com.companyos.notification.NotificationEventMessage;
import com.companyos.notification.NotificationEventParser;
import com.companyos.notification.NotificationPayloadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationEventParserTest {
    private final NotificationEventParser parser = new NotificationEventParser(
            new ObjectMapper().findAndRegisterModules());

    @Test
    void parsesTenantOnboardedRecipient() {
        UUID ownerId = UUID.randomUUID();

        NotificationEventMessage message = parser.parse("TENANT_ONBOARDED",
                "{\"ownerId\":\"" + ownerId + "\"}");

        assertEquals(ownerId, message.recipientUserId());
        assertEquals("TENANT_ONBOARDED", message.type());
    }

    @Test
    void rejectsUnsupportedEventsAsPermanentPayloadFailures() {
        assertThrows(NotificationPayloadException.class,
                () -> parser.parse("UNSUPPORTED_EVENT", "{\"ownerId\":\"" + UUID.randomUUID() + "\"}"));
    }

    @Test
    void rejectsMalformedPayloadAsPermanentPayloadFailure() {
        assertThrows(NotificationPayloadException.class,
                () -> parser.parse("USER_INVITED", "{\"userId\":\"not-a-uuid\"}"));
    }

    @Test
    void parsesReassignmentForNewAndPreviousOwners() {
        UUID previous = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        var messages = parser.parseMany("WORK_ITEM_REASSIGNED", """
                {"previousAssigneeUserId":"%s","newAssigneeUserId":"%s","title":"Payment retry","reason":"Emergency handoff"}
                """.formatted(previous, next));

        assertEquals(2, messages.size());
        assertTrue(messages.stream().anyMatch(message -> message.recipientUserId().equals(previous)));
        assertTrue(messages.stream().anyMatch(message -> message.recipientUserId().equals(next)));
    }

    @Test
    void parsesApprovedScopeChangeForRequesterAndAssignee() {
        UUID requester = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        var messages = parser.parseMany("WORK_SCOPE_CHANGED", """
                {"requestedBy":"%s","assigneeUserId":"%s","title":"Checkout","status":"APPROVED"}
                """.formatted(requester, assignee));

        assertEquals(2, messages.size());
        assertTrue(messages.stream().anyMatch(message -> message.recipientUserId().equals(requester)));
        assertTrue(messages.stream().anyMatch(message -> message.recipientUserId().equals(assignee)));
    }
}
