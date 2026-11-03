package com.companyos.notification;

import java.util.UUID;

public interface ExternalNotificationSender {
    void send(UUID tenantId, String eventType, String title, String body);
}
