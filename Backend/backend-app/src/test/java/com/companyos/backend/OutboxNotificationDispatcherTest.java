package com.companyos.backend;

import com.companyos.notification.OutboxNotificationConsumer;
import com.companyos.eventdelivery.OutboxNotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxNotificationDispatcherTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void marksAConsumerSuccessfulAfterItProcessesAnEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OutboxNotificationConsumer consumer = mock(OutboxNotificationConsumer.class);
        when(consumer.consumerKey()).thenReturn("test.notification");
        stubEventQuery(eventId, tenantId, "TENANT_ONBOARDED", "{\"ownerId\":\"" + UUID.randomUUID() + "\"}");
        stubClaimAndAttempt(1);
        OutboxNotificationDispatcher dispatcher = new OutboxNotificationDispatcher(jdbcTemplate, List.of(consumer));

        dispatcher.dispatch();

        verify(consumer).consume(eq(eventId), eq(tenantId), eq("TENANT_ONBOARDED"), anyString());
        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    void retriesTransientConsumerFailureAndKeepsDeliveryIndependent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OutboxNotificationConsumer consumer = mock(OutboxNotificationConsumer.class);
        when(consumer.consumerKey()).thenReturn("test.retryable");
        when(consumer.retryable(any(Exception.class))).thenReturn(true);
        doThrow(new IllegalStateException("provider unavailable"))
                .when(consumer).consume(any(), any(), anyString(), anyString());
        stubEventQuery(eventId, tenantId, "USER_INVITED", "{\"userId\":\"" + UUID.randomUUID() + "\"}");
        stubClaimAndAttempt(1);
        OutboxNotificationDispatcher dispatcher = new OutboxNotificationDispatcher(jdbcTemplate, List.of(consumer));

        dispatcher.dispatch();

        verify(consumer).consume(eq(eventId), eq(tenantId), eq("USER_INVITED"), anyString());
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("SET status = ?"), any(Object[].class));
    }

    @Test
    void permanentConsumerFailureIsDeadLetteredWithoutASecondAttempt() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OutboxNotificationConsumer consumer = mock(OutboxNotificationConsumer.class);
        when(consumer.consumerKey()).thenReturn("test.permanent");
        when(consumer.retryable(any(Exception.class))).thenReturn(false);
        doThrow(new IllegalArgumentException("invalid provider configuration"))
                .when(consumer).consume(any(), any(), anyString(), anyString());
        stubEventQuery(eventId, tenantId, "USER_INVITED", "{\"userId\":\"" + UUID.randomUUID() + "\"}");
        stubClaimAndAttempt(1);
        OutboxNotificationDispatcher dispatcher = new OutboxNotificationDispatcher(jdbcTemplate, List.of(consumer));

        dispatcher.dispatch();

        verify(consumer, times(1)).consume(any(), any(), anyString(), anyString());
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("SET status = ?"), any(Object[].class));
    }

    @Test
    void oneEventIsDeliveredIndependentlyToEachConsumer() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OutboxNotificationConsumer first = mock(OutboxNotificationConsumer.class);
        OutboxNotificationConsumer second = mock(OutboxNotificationConsumer.class);
        when(first.consumerKey()).thenReturn("test.first");
        when(second.consumerKey()).thenReturn("test.second");
        stubEventQuery(eventId, tenantId, "TENANT_ONBOARDED", "{\"ownerId\":\"" + UUID.randomUUID() + "\"}");
        stubClaimAndAttempt(1);
        OutboxNotificationDispatcher dispatcher = new OutboxNotificationDispatcher(jdbcTemplate, List.of(first, second));

        dispatcher.dispatch();

        verify(first).consume(eq(eventId), eq(tenantId), eq("TENANT_ONBOARDED"), anyString());
        verify(second).consume(eq(eventId), eq(tenantId), eq("TENANT_ONBOARDED"), anyString());
        assertEquals(6, org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update"))
                .count());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubEventQuery(UUID eventId, UUID tenantId, String eventType, String payload) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getBytes("event_id")).thenReturn(uuidBytes(eventId));
        when(resultSet.getBytes("tenant_id")).thenReturn(uuidBytes(tenantId));
        when(resultSet.getString("event_type")).thenReturn(eventType);
        when(resultSet.getString("payload_json")).thenReturn(payload);
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    private void stubClaimAndAttempt(int attempt) {
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(attempt);
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
