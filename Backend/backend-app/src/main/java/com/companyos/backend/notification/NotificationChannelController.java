package com.companyos.backend.notification;

import com.companyos.feature.entitlement.FeatureEntitlementService;
import com.companyos.notification.NotificationChannelStore;
import com.companyos.notification.TelegramChannelConfig;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/tenant/notification-channels")
public class NotificationChannelController {
    private final AuthorizationService authorization;
    private final FeatureEntitlementService features;
    private final NotificationChannelStore store;

    public NotificationChannelController(
            AuthorizationService authorization,
            FeatureEntitlementService features,
            NotificationChannelStore store) {
        this.authorization = authorization;
        this.features = features;
        this.store = store;
    }

    @GetMapping("/telegram")
    public TelegramChannelResponse telegram() {
        authorization.requirePermission("notification.channel.read");
        var tenantId = TenantContextHolder.required().tenantId();
        return TelegramChannelResponse.from(store.findTelegram(tenantId).orElse(null));
    }

    @PutMapping("/telegram")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTelegram(@Valid @RequestBody TelegramChannelUpdateRequest request) {
        authorization.requirePermission("notification.channel.manage");
        var tenantId = TenantContextHolder.required().tenantId();
        TelegramChannelConfig current = store.findTelegram(tenantId).orElse(null);
        if (request.enabled()) features.requireEnabled(tenantId, "TELEGRAM_NOTIFICATIONS");
        String botToken = request.botToken() == null || request.botToken().isBlank()
                ? current == null ? null : current.botToken() : request.botToken().trim();
        String chatId = request.chatId() == null || request.chatId().isBlank()
                ? current == null ? null : current.chatId() : request.chatId().trim();
        if (request.enabled() && (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank())) {
            throw new IllegalArgumentException("botToken and chatId are required when Telegram is enabled");
        }
        store.saveTelegram(new TelegramChannelConfig(tenantId, request.enabled(), botToken, chatId, Instant.now()));
    }

    public record TelegramChannelUpdateRequest(
            @NotNull Boolean enabled,
            @Size(max = 256) String botToken,
            @Size(max = 200) String chatId) { }

    public record TelegramChannelResponse(boolean enabled, boolean configured, String chatId, String updatedAt) {
        static TelegramChannelResponse from(TelegramChannelConfig config) {
            return config == null
                    ? new TelegramChannelResponse(false, false, null, null)
                    : new TelegramChannelResponse(config.enabled(), config.botToken() != null && !config.botToken().isBlank(),
                    config.chatId(), config.updatedAt().toString());
        }
    }
}
