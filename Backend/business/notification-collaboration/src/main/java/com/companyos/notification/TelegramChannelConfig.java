package com.companyos.notification;

import java.time.Instant;
import java.util.UUID;

public record TelegramChannelConfig(
        UUID tenantId,
        boolean enabled,
        String botToken,
        String chatId,
        Instant updatedAt) {
}
