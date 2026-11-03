package com.companyos.eventdelivery;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "outbox.notification.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDeliveryMetrics {
    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryMetrics.class);
    private static final long PROCESSING_LEASE_SECONDS = 300;

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final Map<MetricKey, AtomicLong> deliveryCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> overdueCounts = new ConcurrentHashMap<>();
    private final AtomicLong refreshFailures = new AtomicLong();
    private final AtomicLong lastRefreshEpochSeconds = new AtomicLong();

    public OutboxDeliveryMetrics(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
        Gauge.builder("companyos.outbox.refresh.failures", refreshFailures, AtomicLong::doubleValue)
                .description("Outbox metrics refresh failures since process start")
                .register(meterRegistry);
        Gauge.builder("companyos.outbox.refresh.last_epoch_seconds", lastRefreshEpochSeconds, AtomicLong::doubleValue)
                .description("Unix time of the last successful outbox metrics refresh")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${outbox.notification.metrics-poll-ms:30000}",
            initialDelayString = "${outbox.notification.metrics-initial-delay-ms:10000}")
    public void refresh() {
        try {
            deliveryCounts.values().forEach(value -> value.set(0));
            overdueCounts.values().forEach(value -> value.set(0));

            jdbcTemplate.query("""
                    SELECT consumer, status, COUNT(*) AS delivery_count
                    FROM outbox_event_delivery
                    GROUP BY consumer, status
                    """, (resultSet, rowNumber) -> {
                String consumer = resultSet.getString("consumer");
                String status = resultSet.getString("status");
                deliveryCount(consumer, status).set(resultSet.getLong("delivery_count"));
                return null;
            });

            jdbcTemplate.query("""
                    SELECT consumer, COUNT(*) AS overdue_count
                    FROM outbox_event_delivery
                    WHERE (status IN ('PENDING', 'RETRY') AND next_attempt_at <= CURRENT_TIMESTAMP(6))
                       OR (status = 'PROCESSING' AND updated_at <= DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL ? SECOND))
                    GROUP BY consumer
                    """, (resultSet, rowNumber) -> {
                overdueCount(resultSet.getString("consumer")).set(resultSet.getLong("overdue_count"));
                return null;
            }, PROCESSING_LEASE_SECONDS);

            lastRefreshEpochSeconds.set(Instant.now().getEpochSecond());
        } catch (RuntimeException exception) {
            refreshFailures.incrementAndGet();
            log.warn("Unable to refresh outbox delivery metrics", exception);
        }
    }

    private AtomicLong deliveryCount(String consumer, String status) {
        MetricKey key = new MetricKey(consumer, status);
        return deliveryCounts.computeIfAbsent(key, ignored -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("companyos.outbox.delivery.count", value, AtomicLong::doubleValue)
                    .tags(Tags.of("consumer", consumer, "status", status))
                    .description("Outbox delivery records by consumer and status")
                    .register(meterRegistry);
            return value;
        });
    }

    private AtomicLong overdueCount(String consumer) {
        return overdueCounts.computeIfAbsent(consumer, ignored -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("companyos.outbox.delivery.overdue", value, AtomicLong::doubleValue)
                    .tags("consumer", consumer)
                    .description("Outbox deliveries due or past their processing lease")
                    .register(meterRegistry);
            return value;
        });
    }

    private record MetricKey(String consumer, String status) { }
}
