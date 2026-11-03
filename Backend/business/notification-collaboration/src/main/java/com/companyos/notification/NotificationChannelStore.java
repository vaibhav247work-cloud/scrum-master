package com.companyos.notification;

import java.util.Optional;
import java.util.UUID;

public interface NotificationChannelStore {
    Optional<TelegramChannelConfig> findTelegram(UUID tenantId);

    void saveTelegram(TelegramChannelConfig config);
}
