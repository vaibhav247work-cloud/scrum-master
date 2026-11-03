package com.companyos.persistence.mysql;

import com.companyos.reporting.ReleaseReadinessRow;
import com.companyos.reporting.ReleaseReadinessStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class MysqlReleaseReadinessStore implements ReleaseReadinessStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlReleaseReadinessStore(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<ReleaseReadinessRow> readiness(UUID tenantId, UUID projectId) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.project_id, p.project_key, r.release_key, r.name,
                       r.target_date, r.status,
                       COALESCE(scope.total_work, 0) AS total_work,
                       COALESCE(scope.completed_work, 0) AS completed_work,
                       COALESCE(scope.open_work, 0) AS open_work,
                       COALESCE(scope.estimated_scope, 0) AS estimated_scope,
                       COALESCE(scope.completed_estimate, 0) AS completed_estimate,
                       COALESCE(velocity.velocity_minutes, 0) AS velocity_minutes,
                       COALESCE(blockers.blocked_work, 0) AS blocked_work,
                       DATEDIFF(r.target_date, CURRENT_DATE) AS days_to_target
                FROM release_item r
                JOIN project p ON p.tenant_id = r.tenant_id AND p.id = r.project_id
                LEFT JOIN (
                    SELECT link.release_id,
                           COUNT(*) AS total_work,
                           SUM(work.status = 'DONE') AS completed_work,
                           SUM(work.status NOT IN ('DONE', 'CANCELLED')) AS open_work,
                           SUM(CASE WHEN work.status <> 'CANCELLED' THEN COALESCE(work.estimate_minutes, 0) ELSE 0 END) AS estimated_scope,
                           SUM(CASE WHEN work.status = 'DONE' THEN COALESCE(work.estimate_minutes, 0) ELSE 0 END) AS completed_estimate
                    FROM release_work_item link
                    JOIN work_item work ON work.tenant_id = link.tenant_id AND work.id = link.work_item_id
                    WHERE link.tenant_id = ?
                    GROUP BY link.release_id
                ) scope ON scope.release_id = r.id
                LEFT JOIN (
                    SELECT COALESCE(te.project_id, wi.project_id) AS project_id,
                           SUM(te.minutes) / 4 AS velocity_minutes
                    FROM time_entry te
                    LEFT JOIN work_item wi ON wi.tenant_id = te.tenant_id AND wi.id = te.work_item_id
                    WHERE te.tenant_id = ? AND te.entry_date >= DATE_SUB(CURRENT_DATE, INTERVAL 28 DAY)
                    GROUP BY COALESCE(te.project_id, wi.project_id)
                ) velocity ON velocity.project_id = r.project_id
                LEFT JOIN (
                    SELECT link.release_id, COUNT(DISTINCT dependency.id) AS blocked_work
                    FROM release_work_item link
                    JOIN work_item work ON work.tenant_id = link.tenant_id AND work.id = link.work_item_id
                    JOIN work_item_dependency dependency
                      ON dependency.tenant_id = work.tenant_id
                     AND dependency.blocked_work_item_id = work.id
                     AND dependency.status = 'OPEN'
                    WHERE link.tenant_id = ?
                    GROUP BY link.release_id
                ) blockers ON blockers.release_id = r.id
                WHERE r.tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(tenantId));
        if (projectId != null) { sql.append(" AND r.project_id = ?"); args.add(uuidBytes(projectId)); }
        sql.append(" ORDER BY COALESCE(r.target_date, '9999-12-31'), r.name, r.id");
        return jdbcTemplate.query(sql.toString(), this::map, args.toArray());
    }

    private ReleaseReadinessRow map(ResultSet rs, int rowNumber) throws SQLException {
        LocalDate targetDate = rs.getObject("target_date", LocalDate.class);
        int total = rs.getInt("total_work");
        int completed = rs.getInt("completed_work");
        int open = rs.getInt("open_work");
        int blocked = rs.getInt("blocked_work");
        long estimatedScope = rs.getLong("estimated_scope");
        long completedEstimate = rs.getLong("completed_estimate");
        long remainingEstimate = Math.max(0, estimatedScope - completedEstimate);
        long velocity = rs.getLong("velocity_minutes");
        Integer projectedDays = remainingEstimate > 0 && velocity > 0
                ? (int) Math.ceil((remainingEstimate * 7.0) / velocity) : null;
        int completion = total == 0 ? 0 : Math.round((completed * 100.0f) / total);
        Integer days = rs.getObject("days_to_target", Integer.class);
        return new ReleaseReadinessRow(uuid(rs.getBytes("id")), uuid(rs.getBytes("project_id")),
                rs.getString("project_key"), rs.getString("release_key"), rs.getString("name"),
                targetDate, rs.getString("status"), total, completed, open, blocked, completion,
                days, status(rs.getString("status"), targetDate, days, total, open, blocked, completion),
                estimatedScope, remainingEstimate, velocity, projectedDays);
    }

    private static String status(String releaseStatus, LocalDate targetDate, Integer daysToTarget,
                                 int total, int open, int blocked, int completion) {
        if ("RELEASED".equals(releaseStatus)) return "RELEASED";
        if ("CANCELLED".equals(releaseStatus)) return "CANCELLED";
        if (targetDate == null) return "NO_TARGET";
        if (blocked > 0 || (daysToTarget != null && daysToTarget < 0)) return "AT_RISK";
        if (completion == 100 && open == 0 && total > 0) return "READY";
        if (daysToTarget != null && daysToTarget <= 7 && completion < 80) return "AT_RISK";
        if (total == 0) return "NO_SCOPE";
        return "ON_TRACK";
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
