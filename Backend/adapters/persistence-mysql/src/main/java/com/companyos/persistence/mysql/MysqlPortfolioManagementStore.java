package com.companyos.persistence.mysql;

import com.companyos.portfolio.CreateInitiativeCommand;
import com.companyos.portfolio.CreatePortfolioCommand;
import com.companyos.portfolio.CreateProgramCommand;
import com.companyos.portfolio.InitiativeSummary;
import com.companyos.portfolio.PortfolioManagementStore;
import com.companyos.portfolio.PortfolioSummary;
import com.companyos.portfolio.ProgramSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlPortfolioManagementStore implements PortfolioManagementStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MysqlPortfolioManagementStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PortfolioSummary> findPortfolios(UUID tenantId) {
        return jdbc.query("""
                SELECT id, owner_user_id, portfolio_key, name, description, status, created_at, updated_at
                FROM portfolio
                WHERE tenant_id = ? AND status <> 'ARCHIVED'
                ORDER BY name, id
                """, this::mapPortfolio, uuidBytes(tenantId));
    }

    @Override
    public PortfolioSummary createPortfolio(UUID tenantId, UUID actorId, CreatePortfolioCommand command, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO portfolio (id, tenant_id, portfolio_key, name, description, status, owner_user_id,
                                       created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
                """, uuidBytes(id), uuidBytes(tenantId), command.portfolioKey(), command.name(), command.description(),
                command.status(), uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now));
        publish(tenantId, actorId, id, "PORTFOLIO_CREATED", "PORTFOLIO", now,
                Map.of("portfolioId", id.toString(), "portfolioKey", command.portfolioKey()));
        return findPortfolio(tenantId, id);
    }

    @Override
    public List<ProgramSummary> findPrograms(UUID tenantId, UUID portfolioId) {
        return jdbc.query("""
                SELECT id, portfolio_id, owner_user_id, program_key, name, description, status, created_at, updated_at
                FROM portfolio_program
                WHERE tenant_id = ? AND portfolio_id = ? AND status <> 'ARCHIVED'
                ORDER BY name, id
                """, this::mapProgram, uuidBytes(tenantId), uuidBytes(portfolioId));
    }

    @Override
    public ProgramSummary createProgram(UUID tenantId, UUID actorId, UUID portfolioId,
                                        CreateProgramCommand command, Instant now) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO portfolio_program (id, tenant_id, portfolio_id, program_key, name, description, status,
                                               owner_user_id, created_by, created_at, updated_at)
                SELECT ?, p.tenant_id, p.id, ?, ?, ?, ?, NULL, ?, ?, ?
                FROM portfolio p
                WHERE p.tenant_id = ? AND p.id = ? AND p.status <> 'ARCHIVED'
                """, uuidBytes(id), command.programKey(), command.name(), command.description(), command.status(),
                uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now), uuidBytes(tenantId), uuidBytes(portfolioId));
        if (inserted != 1) throw new IllegalArgumentException("portfolio was not found or is archived");
        publish(tenantId, actorId, id, "PROGRAM_CREATED", "PROGRAM", now,
                Map.of("programId", id.toString(), "portfolioId", portfolioId.toString(), "programKey", command.programKey()));
        return findProgram(tenantId, id);
    }

    @Override
    public List<InitiativeSummary> findInitiatives(UUID tenantId, UUID portfolioId, UUID programId) {
        if (programId == null) {
            return jdbc.query(initiativeQuery() + " WHERE i.tenant_id = ? AND i.portfolio_id = ? ORDER BY i.target_date IS NULL, i.target_date, i.title, i.id",
                    this::mapInitiative, uuidBytes(tenantId), uuidBytes(portfolioId));
        }
        return jdbc.query(initiativeQuery() + " WHERE i.tenant_id = ? AND i.portfolio_id = ? AND i.program_id = ? ORDER BY i.target_date IS NULL, i.target_date, i.title, i.id",
                this::mapInitiative, uuidBytes(tenantId), uuidBytes(portfolioId), uuidBytes(programId));
    }

    @Override
    public InitiativeSummary createInitiative(UUID tenantId, UUID actorId, CreateInitiativeCommand command, Instant now) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO portfolio_initiative (id, tenant_id, portfolio_id, program_id, product_id, initiative_key,
                                                  title, description, priority, status, target_date, owner_user_id,
                                                  created_by, created_at, updated_at)
                SELECT ?, p.tenant_id, p.id, pg.id, tp.id, ?, ?, ?, ?, 'PLANNING', ?, NULL, ?, ?, ?
                FROM portfolio p
                LEFT JOIN portfolio_program pg
                  ON pg.tenant_id = p.tenant_id AND pg.id = ? AND pg.portfolio_id = p.id AND pg.status <> 'ARCHIVED'
                LEFT JOIN tenant_product tp
                  ON tp.tenant_id = p.tenant_id AND tp.id = ? AND tp.status = 'ACTIVE'
                WHERE p.tenant_id = ? AND p.id = ? AND p.status <> 'ARCHIVED'
                  AND (? IS NULL OR pg.id IS NOT NULL)
                  AND (? IS NULL OR tp.id IS NOT NULL)
                """, uuidBytes(id), command.initiativeKey(), command.title(), command.description(), command.priority(),
                command.targetDate(), uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now),
                uuidBytes(command.programId()), uuidBytes(command.productId()), uuidBytes(tenantId), uuidBytes(command.portfolioId()),
                uuidBytes(command.programId()), uuidBytes(command.productId()));
        if (inserted != 1) throw new IllegalArgumentException("portfolio, program, or product was not found in this tenant");
        publish(tenantId, actorId, id, "INITIATIVE_CREATED", "INITIATIVE", now,
                Map.of("initiativeId", id.toString(), "portfolioId", command.portfolioId().toString(), "initiativeKey", command.initiativeKey()));
        return findInitiative(tenantId, id);
    }

    private PortfolioSummary findPortfolio(UUID tenantId, UUID id) {
        return jdbc.queryForObject("""
                SELECT id, owner_user_id, portfolio_key, name, description, status, created_at, updated_at
                FROM portfolio WHERE tenant_id = ? AND id = ?
                """, this::mapPortfolio, uuidBytes(tenantId), uuidBytes(id));
    }

    private ProgramSummary findProgram(UUID tenantId, UUID id) {
        return jdbc.queryForObject("""
                SELECT id, portfolio_id, owner_user_id, program_key, name, description, status, created_at, updated_at
                FROM portfolio_program WHERE tenant_id = ? AND id = ?
                """, this::mapProgram, uuidBytes(tenantId), uuidBytes(id));
    }

    private InitiativeSummary findInitiative(UUID tenantId, UUID id) {
        return jdbc.queryForObject(initiativeQuery() + " WHERE i.tenant_id = ? AND i.id = ?",
                this::mapInitiative, uuidBytes(tenantId), uuidBytes(id));
    }

    private String initiativeQuery() {
        return """
                SELECT i.id, i.portfolio_id, i.program_id, i.product_id, i.initiative_key, i.title, i.description,
                       i.priority, i.status, i.target_date, i.owner_user_id, i.created_at, i.updated_at
                FROM portfolio_initiative i
                """;
    }

    private PortfolioSummary mapPortfolio(ResultSet rs, int row) throws SQLException {
        return new PortfolioSummary(uuid(rs.getBytes("id")), rs.getString("portfolio_key"), rs.getString("name"),
                rs.getString("description"), rs.getString("status"), nullableUuid(rs.getBytes("owner_user_id")),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private ProgramSummary mapProgram(ResultSet rs, int row) throws SQLException {
        return new ProgramSummary(uuid(rs.getBytes("id")), uuid(rs.getBytes("portfolio_id")), rs.getString("program_key"),
                rs.getString("name"), rs.getString("description"), rs.getString("status"),
                nullableUuid(rs.getBytes("owner_user_id")), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private InitiativeSummary mapInitiative(ResultSet rs, int row) throws SQLException {
        java.sql.Date targetDate = rs.getDate("target_date");
        return new InitiativeSummary(uuid(rs.getBytes("id")), uuid(rs.getBytes("portfolio_id")),
                nullableUuid(rs.getBytes("program_id")), nullableUuid(rs.getBytes("product_id")),
                rs.getString("initiative_key"), rs.getString("title"), rs.getString("description"),
                rs.getString("priority"), rs.getString("status"), targetDate == null ? null : targetDate.toLocalDate(),
                nullableUuid(rs.getBytes("owner_user_id")), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private void publish(UUID tenantId, UUID actorId, UUID aggregateId, String eventType, String aggregateType,
                         Instant now, Map<String, Object> values) {
        try {
            String payload = objectMapper.writeValueAsString(values);
            jdbc.update("""
                    INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                                             correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), eventType, aggregateType,
                    uuidBytes(aggregateId), Timestamp.from(now), payload);
            jdbc.update("""
                    INSERT INTO outbox_event (event_id, tenant_id, event_type, event_version, aggregate_type,
                                              aggregate_id, actor_id, correlation_id, causation_id, occurred_at,
                                              payload_json, created_at)
                    VALUES (?, ?, ?, 1, ?, ?, ?, NULL, NULL, ?, ?, ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), eventType, aggregateType, uuidBytes(aggregateId),
                    uuidBytes(actorId), Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize portfolio event", exception);
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

    private static UUID nullableUuid(byte[] bytes) { return bytes == null ? null : uuid(bytes); }
}
