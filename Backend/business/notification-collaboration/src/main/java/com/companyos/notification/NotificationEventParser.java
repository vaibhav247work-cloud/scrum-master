package com.companyos.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Component
public class NotificationEventParser {
    private final ObjectMapper objectMapper;

    public NotificationEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NotificationEventMessage parse(String eventType, String payloadJson) {
        return parseMany(eventType, payloadJson).stream().findFirst()
                .orElseThrow(() -> new NotificationPayloadException("Notification event has no recipient"));
    }

    public List<NotificationEventMessage> parseMany(String eventType, String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            if ("WORK_SCOPE_CHANGED".equals(eventType) || "WORK_SCOPE_CHANGE_REJECTED".equals(eventType)) {
                List<NotificationEventMessage> messages = new ArrayList<>();
                JsonNode requester = payload.get("requestedBy");
                String status = "WORK_SCOPE_CHANGED".equals(eventType) ? "approved" : "rejected";
                String workTitle = payload.path("title").asText("Work scope change");
                if (requester != null && !requester.isNull()) {
                    messages.add(new NotificationEventMessage(UUID.fromString(requester.asText()), eventType,
                            "Work scope change " + status, workTitle + " scope change was " + status + "."));
                }
                if ("WORK_SCOPE_CHANGED".equals(eventType)) {
                    JsonNode assignee = payload.get("assigneeUserId");
                    if (assignee != null && !assignee.isNull() && !assignee.asText().isBlank()
                            && (requester == null || !assignee.asText().equals(requester.asText()))) {
                        messages.add(new NotificationEventMessage(UUID.fromString(assignee.asText()), eventType,
                                "Work scope changed", workTitle + " priority or sprint scope changed."));
                    }
                }
                if (messages.isEmpty()) throw new NotificationPayloadException("Notification event has no recipient");
                return messages;
            }
            if ("WORK_ITEM_REASSIGNED".equals(eventType)) {
                List<NotificationEventMessage> messages = new ArrayList<>();
                JsonNode newAssignee = payload.get("newAssigneeUserId");
                JsonNode previousAssignee = payload.get("previousAssigneeUserId");
                String title = payload.path("title").asText("Work item");
                String reason = payload.path("reason").asText("");
                if (newAssignee != null && !newAssignee.isNull()) {
                    messages.add(new NotificationEventMessage(UUID.fromString(newAssignee.asText()), eventType,
                            "Work assigned to you", title + (reason.isBlank() ? " was assigned to you." : " was reassigned to you: " + reason)));
                }
                if (previousAssignee != null && !previousAssignee.isNull()
                        && !previousAssignee.asText().equals(newAssignee == null ? "" : newAssignee.asText())) {
                    messages.add(new NotificationEventMessage(UUID.fromString(previousAssignee.asText()), eventType,
                            "Work assignment changed", title + " was reassigned to another teammate."));
                }
                if (messages.isEmpty()) throw new NotificationPayloadException("Notification event has no recipient");
                return messages;
            }
            JsonNode recipient = "USER_INVITED".equals(eventType) ? payload.get("userId") : payload.get("ownerId");
            if (!("USER_INVITED".equals(eventType) || "TENANT_ONBOARDED".equals(eventType))) {
                throw new NotificationPayloadException("Unsupported notification event type: " + eventType);
            }
            if (recipient == null || recipient.isNull()) {
                throw new NotificationPayloadException("Notification event has no recipient");
            }
            return List.of(new NotificationEventMessage(UUID.fromString(recipient.asText()),
                    eventType,
                    "USER_INVITED".equals(eventType) ? "You have been invited to the workspace" : "Workspace created",
                    "USER_INVITED".equals(eventType)
                            ? "Complete your account activation to join this tenant workspace."
                            : "Your company workspace is ready to configure."));
        } catch (NotificationPayloadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new NotificationPayloadException("Invalid notification event payload");
        }
    }
}
