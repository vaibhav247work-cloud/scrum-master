package com.companyos.backend.notification;

import com.companyos.notification.InAppNotification;
import com.companyos.notification.InAppNotificationStore;
import com.companyos.tenantcontext.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {
    private final InAppNotificationStore store;

    public NotificationController(InAppNotificationStore store) {
        this.store = store;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "25") int limit) {
        var context = TenantContextHolder.required();
        return store.findForUser(context.tenantId(), context.userId(), limit).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @PatchMapping("/{notificationId}/read")
    public void markRead(@PathVariable UUID notificationId) {
        var context = TenantContextHolder.required();
        store.markRead(context.tenantId(), context.userId(), notificationId, Instant.now());
    }

    public record NotificationResponse(
            String id, String type, String title, String body, String readAt, String createdAt) {
        static NotificationResponse from(InAppNotification notification) {
            return new NotificationResponse(notification.id().toString(), notification.notificationType(),
                    notification.title(), notification.body(), notification.readAt() == null ? null : notification.readAt().toString(),
                    notification.createdAt().toString());
        }
    }
}
