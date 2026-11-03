package com.companyos.persistence.mysql;

import com.companyos.migration.ImportJobSummary;
import com.companyos.migration.ImportRowError;
import com.companyos.migration.MigrationManagementStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MysqlMigrationManagementStore implements MigrationManagementStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MysqlMigrationManagementStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImportJobSummary start(UUID tenantId, UUID actorId, String jobType, int totalRows, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO migration_import_job
                    (id, tenant_id, job_type, status, total_rows, processed_rows, accepted_rows, rejected_rows,
                     error_count, failure_reason, requested_by, created_at, updated_at, completed_at)
                VALUES (?, ?, ?, 'PROCESSING', ?, 0, 0, 0, 0, '', ?, ?, ?, NULL)
                """, uuidBytes(id), uuidBytes(tenantId), jobType, totalRows, uuidBytes(actorId),
                Timestamp.from(now), Timestamp.from(now));
        publish(tenantId, actorId, id, "IMPORT_JOB_STARTED", now, Map.of("jobId", id.toString(), "jobType", jobType, "totalRows", totalRows));
        return find(tenantId, id).orElseThrow();
    }

    @Override
    public void reject(UUID tenantId, UUID jobId, List<ImportRowError> errors, Instant now) {
        jdbc.update("""
                UPDATE migration_import_job
                SET status = 'REJECTED', processed_rows = 0, accepted_rows = 0, rejected_rows = ?,
                    error_count = ?, failure_reason = 'Validation failed', updated_at = ?, completed_at = ?
                WHERE tenant_id = ? AND id = ?
                """, errors.size(), errors.size(), Timestamp.from(now), Timestamp.from(now), uuidBytes(tenantId), uuidBytes(jobId));
        for (ImportRowError error : errors) {
            jdbc.update("""
                    INSERT INTO migration_import_error
                        (id, tenant_id, job_id, `row_number`, source_key, field_name, error_code, message, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(jobId), error.rowNumber(),
                    error.sourceKey(), error.field(), error.code(), error.message(), Timestamp.from(now));
        }
        publish(tenantId, null, jobId, "IMPORT_JOB_REJECTED", now, Map.of("jobId", jobId.toString(), "errorCount", errors.size()));
    }

    @Override
    public void progress(UUID tenantId, UUID jobId, int processedRows, int acceptedRows, Instant now) {
        jdbc.update("""
                UPDATE migration_import_job SET processed_rows = ?, accepted_rows = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND status = 'PROCESSING'
                """, processedRows, acceptedRows, Timestamp.from(now), uuidBytes(tenantId), uuidBytes(jobId));
    }

    @Override
    public void validated(UUID tenantId, UUID jobId, int totalRows, Instant now) {
        jdbc.update("""
                UPDATE migration_import_job
                SET status = 'VALIDATED', processed_rows = ?, accepted_rows = 0, rejected_rows = 0,
                    error_count = 0, updated_at = ?, completed_at = ?
                WHERE tenant_id = ? AND id = ? AND status = 'PROCESSING'
                """, totalRows, Timestamp.from(now), Timestamp.from(now), uuidBytes(tenantId), uuidBytes(jobId));
        publish(tenantId, null, jobId, "IMPORT_JOB_VALIDATED", now, Map.of("jobId", jobId.toString(), "totalRows", totalRows));
    }

    @Override
    public void complete(UUID tenantId, UUID jobId, int acceptedRows, Instant now) {
        jdbc.update("""
                UPDATE migration_import_job
                SET status = 'COMPLETED', processed_rows = total_rows, accepted_rows = ?, rejected_rows = 0,
                    error_count = 0, updated_at = ?, completed_at = ?
                WHERE tenant_id = ? AND id = ? AND status = 'PROCESSING'
                """, acceptedRows, Timestamp.from(now), Timestamp.from(now), uuidBytes(tenantId), uuidBytes(jobId));
        publish(tenantId, null, jobId, "IMPORT_JOB_COMPLETED", now, Map.of("jobId", jobId.toString(), "acceptedRows", acceptedRows));
    }

    @Override
    public Optional<ImportJobSummary> find(UUID tenantId, UUID jobId) {
        return jdbc.query("""
                SELECT id, job_type, status, total_rows, processed_rows, accepted_rows, rejected_rows, error_count,
                       failure_reason, requested_by, created_at, updated_at, completed_at
                FROM migration_import_job WHERE tenant_id = ? AND id = ?
                """, this::map, uuidBytes(tenantId), uuidBytes(jobId)).stream().findFirst();
    }

    private ImportJobSummary map(ResultSet rs, int row) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new ImportJobSummary(uuid(rs.getBytes("id")), rs.getString("job_type"), rs.getString("status"),
                rs.getInt("total_rows"), rs.getInt("processed_rows"), rs.getInt("accepted_rows"),
                rs.getInt("rejected_rows"), rs.getInt("error_count"), rs.getString("failure_reason"),
                uuid(rs.getBytes("requested_by")), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), completed == null ? null : completed.toInstant());
    }

    private void publish(UUID tenantId, UUID actorId, UUID aggregateId, String eventType, Instant now, Map<String, Object> values) {
        try {
            String payload = objectMapper.writeValueAsString(values);
            jdbc.update("""
                    INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                                             correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, ?, 'IMPORT_JOB', ?, NULL, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), eventType,
                    uuidBytes(aggregateId), Timestamp.from(now), payload);
            jdbc.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id, actor_id,
                         correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'IMPORT_JOB', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), eventType, uuidBytes(aggregateId),
                    uuidBytes(actorId), Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize import event", exception);
        }
    }

    private static byte[] uuidBytes(UUID value) {
        if (value == null) return null;
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
