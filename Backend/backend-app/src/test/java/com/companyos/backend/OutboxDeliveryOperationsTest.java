package com.companyos.backend;

import com.companyos.eventdelivery.OutboxDeliveryOperations;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OutboxDeliveryOperationsTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final OutboxDeliveryOperations operations = new OutboxDeliveryOperations(jdbcTemplate);

    @Test
    void rejectsUnsupportedStatusBeforeQueryingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> operations.list(UUID.randomUUID(), "DROP TABLE", 50));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void replayReturnsFalseWhenDeliveryIsNotDeadLetteredForTenant() {
        doReturn(java.util.List.of()).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(), any());

        assertFalse(operations.replay(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }
}
