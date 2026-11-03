package com.companyos.persistence.mysql;

import com.companyos.workflow.BoardConfiguration;
import com.companyos.workflow.BoardConfigurationCommand;
import com.companyos.workflow.BoardConfigurationStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlBoardConfigurationStore implements BoardConfigurationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MysqlBoardConfigurationStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public BoardConfiguration find(UUID tenantId, UUID projectId) {
        return jdbc.query("""
                SELECT grouping_key, swimlane_key, card_fields_json, wip_limits_json, updated_at
                FROM project_board_configuration
                WHERE tenant_id = ? AND project_id = ?
                """, rs -> rs.next() ? map(projectId, rs.getString("grouping_key"), rs.getString("swimlane_key"),
                rs.getString("card_fields_json"), rs.getString("wip_limits_json"), rs.getTimestamp("updated_at"))
                : defaults(projectId), uuidBytes(tenantId), uuidBytes(projectId));
    }

    @Override
    public BoardConfiguration upsert(UUID tenantId, UUID actorId, UUID projectId, BoardConfigurationCommand command) {
        Instant now = Instant.now();
        try {
            jdbc.update("""
                    INSERT INTO project_board_configuration
                        (tenant_id, project_id, grouping_key, swimlane_key, card_fields_json, wip_limits_json, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE grouping_key = VALUES(grouping_key), swimlane_key = VALUES(swimlane_key),
                        card_fields_json = VALUES(card_fields_json), wip_limits_json = VALUES(wip_limits_json),
                        updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)
                    """, uuidBytes(tenantId), uuidBytes(projectId), command.grouping(), command.swimlane(),
                    objectMapper.writeValueAsString(command.cardFields()), objectMapper.writeValueAsString(command.wipLimits()),
                    uuidBytes(actorId), Timestamp.from(now));
            jdbc.update("""
                    INSERT INTO audit_event
                        (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, 'BOARD_CONFIGURATION_UPDATED', 'PROJECT', ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), uuidBytes(projectId),
                    Timestamp.from(now), objectMapper.writeValueAsString(Map.of("projectId", projectId.toString())));
            return find(tenantId, projectId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize board configuration", exception);
        }
    }

    private BoardConfiguration map(UUID projectId, String grouping, String swimlane, String cardFields, String wipLimits, Timestamp updatedAt) {
        try {
            return new BoardConfiguration(projectId, grouping, swimlane,
                    objectMapper.readValue(cardFields, new TypeReference<List<String>>() { }),
                    objectMapper.readValue(wipLimits, new TypeReference<Map<String, Integer>>() { }), updatedAt.toInstant());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored board configuration is invalid", exception);
        }
    }

    private BoardConfiguration defaults(UUID projectId) {
        return new BoardConfiguration(projectId, "STATUS", "NONE", List.of("KEY", "TITLE", "PRIORITY", "ASSIGNEE"), Map.of(), null);
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
