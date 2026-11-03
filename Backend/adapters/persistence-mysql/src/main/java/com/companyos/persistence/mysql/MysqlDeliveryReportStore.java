package com.companyos.persistence.mysql;

import com.companyos.reporting.DeliveryReportRow;
import com.companyos.reporting.DeliveryReportStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlDeliveryReportStore implements DeliveryReportStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlDeliveryReportStore(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<DeliveryReportRow> delivery(UUID tenantId, LocalDate from, LocalDate to, UUID projectId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.project_key, p.name,
                       COALESCE(SUM(CASE WHEN w.status NOT IN ('DONE', 'CANCELLED') THEN 1 ELSE 0 END), 0) AS open_work,
                       COALESCE(SUM(CASE WHEN w.status = 'DONE' THEN 1 ELSE 0 END), 0) AS completed_work,
                       COALESCE((SELECT SUM(te.minutes) FROM time_entry te
                                 LEFT JOIN work_item tw ON tw.tenant_id = te.tenant_id AND tw.id = te.work_item_id
                                 WHERE te.tenant_id = p.tenant_id
                                   AND (te.project_id = p.id OR (te.project_id IS NULL AND tw.project_id = p.id))
                                   AND te.entry_date >= ? AND te.entry_date < ?), 0) AS logged_minutes,
                       COALESCE((SELECT COUNT(*) FROM release_item r
                                 WHERE r.tenant_id = p.tenant_id AND r.project_id = p.id
                                   AND r.status IN ('PLANNED', 'IN_PROGRESS', 'AT_RISK')), 0) AS active_releases,
                       COALESCE((SELECT COUNT(*) FROM release_item r
                                 WHERE r.tenant_id = p.tenant_id AND r.project_id = p.id
                                   AND r.target_date < CURRENT_DATE AND r.status NOT IN ('RELEASED', 'CANCELLED')), 0) AS risk_releases
                FROM project p
                LEFT JOIN work_item w ON w.tenant_id = p.tenant_id AND w.project_id = p.id
                WHERE p.tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(from));
        args.add(Date.valueOf(to));
        args.add(uuidBytes(tenantId));
        if (projectId != null) { sql.append(" AND p.id = ?"); args.add(uuidBytes(projectId)); }
        sql.append(" GROUP BY p.id, p.project_key, p.name, p.tenant_id ORDER BY p.name, p.id");
        return jdbcTemplate.query(sql.toString(), this::map, args.toArray());
    }

    private DeliveryReportRow map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DeliveryReportRow(uuid(resultSet.getBytes("id")), resultSet.getString("project_key"),
                resultSet.getString("name"), resultSet.getInt("open_work"), resultSet.getInt("completed_work"),
                resultSet.getInt("logged_minutes"), resultSet.getInt("active_releases"), resultSet.getInt("risk_releases"));
    }

    private static byte[] uuidBytes(UUID value) { ByteBuffer b = ByteBuffer.allocate(16); b.putLong(value.getMostSignificantBits()); b.putLong(value.getLeastSignificantBits()); return b.array(); }
    private static UUID uuid(byte[] bytes) { ByteBuffer b = ByteBuffer.wrap(bytes); return new UUID(b.getLong(), b.getLong()); }
}
