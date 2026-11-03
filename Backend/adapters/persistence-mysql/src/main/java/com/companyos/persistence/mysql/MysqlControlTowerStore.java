package com.companyos.persistence.mysql;

import com.companyos.reporting.ControlTowerStore;
import com.companyos.reporting.ControlTowerSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public class MysqlControlTowerStore implements ControlTowerStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlControlTowerStore(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public ControlTowerSummary summarize(UUID tenantId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(7);
        return new ControlTowerSummary(Instant.now(),
                count("SELECT COUNT(*) FROM project WHERE tenant_id = ? AND status = 'ACTIVE'", tenantId),
                count("SELECT COUNT(*) FROM sprint WHERE tenant_id = ? AND status IN ('PLANNED', 'IN_PROGRESS')", tenantId),
                count("SELECT COUNT(*) FROM work_item WHERE tenant_id = ? AND status NOT IN ('DONE', 'CANCELLED')", tenantId),
                count("SELECT COUNT(*) FROM work_item_dependency WHERE tenant_id = ? AND status = 'OPEN'", tenantId),
                count("SELECT COUNT(*) FROM release_item WHERE tenant_id = ? AND status IN ('PLANNED', 'IN_PROGRESS', 'AT_RISK')", tenantId),
                count("SELECT COUNT(*) FROM release_item WHERE tenant_id = ? AND target_date < CURRENT_DATE AND status NOT IN ('RELEASED', 'CANCELLED')", tenantId),
                sumMinutes(tenantId, weekStart, weekEnd),
                count("SELECT COUNT(*) FROM tenant_user WHERE tenant_id = ? AND status = 'ACTIVE'", tenantId));
    }

    private int count(String sql, UUID tenantId) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, uuidBytes(tenantId));
        return value == null ? 0 : value.intValue();
    }

    private int sumMinutes(UUID tenantId, LocalDate weekStart, LocalDate weekEnd) {
        Number value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(minutes), 0) FROM time_entry
                WHERE tenant_id = ? AND entry_date >= ? AND entry_date < ?
                """, Number.class, uuidBytes(tenantId), Date.valueOf(weekStart), Date.valueOf(weekEnd));
        return value == null ? 0 : value.intValue();
    }

    private static byte[] uuidBytes(UUID value) { ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()); b.putLong(value.getLeastSignificantBits()); return b.array(); }
}
