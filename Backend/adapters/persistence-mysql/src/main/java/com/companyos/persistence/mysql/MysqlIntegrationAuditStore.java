package com.companyos.persistence.mysql;

import com.companyos.integration.CreateIntegrationConnectionCommand;
import com.companyos.integration.ExternalIntegrationEventSummary;
import com.companyos.integration.IngestIntegrationEventCommand;
import com.companyos.integration.IntegrationAuditStore;
import com.companyos.integration.IntegrationConnectionSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlIntegrationAuditStore implements IntegrationAuditStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IntegrationSecretProtector secretProtector;

    public MysqlIntegrationAuditStore(JdbcTemplate jdbc, ObjectMapper objectMapper, IntegrationSecretProtector secretProtector) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.secretProtector = secretProtector;
    }

    @Override
    public List<IntegrationConnectionSummary> findConnections(UUID tenantId) {
        return jdbc.query("""
                SELECT c.id, c.connection_type, c.name, c.status, c.base_url, c.external_account,
                       c.secret_ciphertext IS NOT NULL AS secret_configured,
                       MAX(e.received_at) AS last_event_at, c.last_error, c.created_at, c.updated_at
                FROM integration_connection c
                LEFT JOIN external_integration_event e ON e.tenant_id = c.tenant_id AND e.connection_id = c.id
                WHERE c.tenant_id = ?
                GROUP BY c.id, c.connection_type, c.name, c.status, c.base_url, c.external_account,
                         c.secret_ciphertext, c.last_error, c.created_at, c.updated_at
                ORDER BY c.name, c.id
                """, this::mapConnection, uuidBytes(tenantId));
    }

    @Override
    public IntegrationConnectionSummary createConnection(UUID tenantId, UUID actorId,
                                                         CreateIntegrationConnectionCommand command, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO integration_connection
                    (id, tenant_id, connection_type, name, status, base_url, external_account,
                     secret_ciphertext, last_error, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, '', ?, ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), command.connectionType(), command.name(), command.baseUrl(),
                command.externalAccount(), secretProtector.encrypt(command.secret()), uuidBytes(actorId),
                Timestamp.from(now), Timestamp.from(now));
        publish(tenantId, actorId, id, "INTEGRATION_CONNECTION_CREATED", now,
                Map.of("connectionId", id.toString(), "connectionType", command.connectionType()));
        return findConnections(tenantId).stream().filter(connection -> connection.id().equals(id)).findFirst().orElseThrow();
    }

    @Override
    public ExternalIntegrationEventSummary ingestEvent(UUID tenantId, UUID connectionId, String suppliedSecret,
                                                        IngestIntegrationEventCommand command, Instant receivedAt) {
        ConnectionSecret connection = jdbc.query("""
                SELECT tenant_id, status, secret_ciphertext FROM integration_connection
                WHERE tenant_id = ? AND id = ?
                """, rs -> rs.next() ? new ConnectionSecret(uuid(rs.getBytes("tenant_id")), rs.getString("status"), rs.getString("secret_ciphertext")) : null,
                uuidBytes(tenantId), uuidBytes(connectionId));
        if (connection == null || !"ACTIVE".equals(connection.status())) throw new IllegalArgumentException("integration connection was not found or is inactive");
        if (connection.secretCiphertext() == null || !MessageDigest.isEqual(
                secretProtector.decrypt(connection.secretCiphertext()).getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException("integration secret is invalid");
        return ingestEventWithVerifiedSecret(tenantId, connectionId, command, receivedAt);
    }

    @Override
    public ExternalIntegrationEventSummary ingestSignedEvent(UUID connectionId, String signature, String timestamp,
                                                             String rawBody, IngestIntegrationEventCommand command,
                                                             Instant receivedAt) {
        Instant signedAt;
        try { signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("integration timestamp is invalid", exception); }
        if (Math.abs(java.time.Duration.between(signedAt, Instant.now()).toSeconds()) > 300) {
            throw new IllegalArgumentException("integration signature has expired");
        }
        ConnectionSecret connection = jdbc.query("""
                SELECT tenant_id, status, secret_ciphertext FROM integration_connection WHERE id = ?
                """, rs -> rs.next() ? new ConnectionSecret(uuid(rs.getBytes("tenant_id")), rs.getString("status"), rs.getString("secret_ciphertext")) : null,
                uuidBytes(connectionId));
        if (connection == null || !"ACTIVE".equals(connection.status()) || connection.secretCiphertext() == null) {
            throw new IllegalArgumentException("integration connection was not found or is inactive");
        }
        String secret = secretProtector.decrypt(connection.secretCiphertext());
        String supplied = signature.startsWith("sha256=") ? signature.substring("sha256=".length()) : signature;
        String expected = hmacHex(secret, timestamp + "." + rawBody);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("integration signature is invalid");
        }
        return ingestEventWithVerifiedSecret(connection.tenantId(), connectionId, command, receivedAt);
    }

    @Override
    public UUID tenantIdForConnection(UUID connectionId) {
        if (connectionId == null) return null;
        return jdbc.query("SELECT tenant_id FROM integration_connection WHERE id = ?", rs -> rs.next() ? uuid(rs.getBytes("tenant_id")) : null, uuidBytes(connectionId));
    }

    private ExternalIntegrationEventSummary ingestEventWithVerifiedSecret(UUID tenantId, UUID connectionId,
                                                                            IngestIntegrationEventCommand command, Instant receivedAt) {
        String payload = validJson(command.payload());
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT IGNORE INTO external_integration_event
                    (id, tenant_id, connection_id, source_system, event_type, external_id, repository, branch,
                     environment, source_url, occurred_at, received_at, status, payload_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?)
                """, uuidBytes(id), uuidBytes(tenantId), uuidBytes(connectionId), command.sourceSystem(), command.eventType(),
                command.externalId(), command.repository(), command.branch(), command.environment(), command.sourceUrl(),
                Timestamp.from(command.occurredAt()), Timestamp.from(receivedAt), payload);
        if (inserted == 0) {
            return findEvent(tenantId, connectionId, command.eventType(), command.externalId(), true);
        }
        jdbc.update("UPDATE integration_connection SET updated_at = ?, last_error = '' WHERE tenant_id = ? AND id = ?",
                Timestamp.from(receivedAt), uuidBytes(tenantId), uuidBytes(connectionId));
        publish(tenantId, null, id, "INTEGRATION_EVENT_RECEIVED", receivedAt,
                Map.of("eventId", id.toString(), "connectionId", connectionId.toString(), "sourceSystem", command.sourceSystem(),
                        "eventType", command.eventType(), "externalId", command.externalId()));
        return findEvent(tenantId, connectionId, command.eventType(), command.externalId(), false);
    }

    private ExternalIntegrationEventSummary findEvent(UUID tenantId, UUID connectionId, String eventType, String externalId, boolean duplicate) {
        return jdbc.queryForObject("""
                SELECT id, connection_id, source_system, event_type, external_id, status, repository, branch,
                       environment, source_url, occurred_at, received_at
                FROM external_integration_event
                WHERE tenant_id = ? AND connection_id = ? AND event_type = ? AND external_id = ?
                """, (rs, row) -> mapEvent(rs, duplicate), uuidBytes(tenantId), uuidBytes(connectionId), eventType, externalId);
    }

    private IntegrationConnectionSummary mapConnection(ResultSet rs, int row) throws SQLException {
        Timestamp lastEvent = rs.getTimestamp("last_event_at");
        return new IntegrationConnectionSummary(uuid(rs.getBytes("id")), rs.getString("connection_type"), rs.getString("name"),
                rs.getString("status"), rs.getString("base_url"), rs.getString("external_account"), rs.getBoolean("secret_configured"),
                lastEvent == null ? null : lastEvent.toInstant(), rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private ExternalIntegrationEventSummary mapEvent(ResultSet rs, boolean duplicate) throws SQLException {
        return new ExternalIntegrationEventSummary(uuid(rs.getBytes("id")), uuid(rs.getBytes("connection_id")),
                rs.getString("source_system"), rs.getString("event_type"), rs.getString("external_id"), rs.getString("status"),
                rs.getString("repository"), rs.getString("branch"), rs.getString("environment"), rs.getString("source_url"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("received_at").toInstant(), duplicate);
    }

    private String validJson(String value) {
        String normalized = value == null || value.isBlank() ? "{}" : value.trim();
        try { objectMapper.readTree(normalized); return normalized; }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("payload must be valid JSON", exception); }
    }

    private void publish(UUID tenantId, UUID actorId, UUID aggregateId, String type, Instant now, Map<String, Object> values) {
        try {
            String payload = objectMapper.writeValueAsString(values);
            jdbc.update("""
                    INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                                             correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, ?, 'INTEGRATION_EVENT', ?, NULL, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), type, uuidBytes(aggregateId),
                    Timestamp.from(now), payload);
            jdbc.update("""
                    INSERT INTO outbox_event
                        (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id, actor_id,
                         correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, ?, 1, 'INTEGRATION_EVENT', ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), type, uuidBytes(aggregateId), uuidBytes(actorId),
                    Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize integration event", exception); }
    }

    private record ConnectionSecret(UUID tenantId, String status, String secretCiphertext) { }
    private static String hmacHex(String secret, String material) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("HMAC-SHA256 is unavailable", exception); }
    }
    private static byte[] uuidBytes(UUID value) { if (value == null) return null; ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] value) { ByteBuffer b = ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong()); }
}
