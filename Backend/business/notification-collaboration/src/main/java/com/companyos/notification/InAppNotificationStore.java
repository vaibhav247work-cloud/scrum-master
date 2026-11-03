package com.companyos.notification;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public interface InAppNotificationStore {
    void create(InAppNotification notification);

    List<InAppNotification> findForUser(UUID tenantId, UUID userId, int limit);

    boolean markRead(UUID tenantId, UUID userId, UUID notificationId, Instant readAt);
}
