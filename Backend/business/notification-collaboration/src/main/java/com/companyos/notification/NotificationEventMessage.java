package com.companyos.notification;

import java.util.UUID;

public record NotificationEventMessage(UUID recipientUserId, String type, String title, String body) {
}
