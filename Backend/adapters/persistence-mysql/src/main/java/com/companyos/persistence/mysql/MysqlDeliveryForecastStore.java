package com.companyos.persistence.mysql;

import com.companyos.reporting.DeliveryForecastRow;
import com.companyos.reporting.DeliveryForecastStore;
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
public class MysqlDeliveryForecastStore implements DeliveryForecastStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlDeliveryForecastStore(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<DeliveryForecastRow> forecast(UUID tenantId, LocalDate from, LocalDate to, UUID projectId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.project_key, p.name,
                       COALESCE(plan.planned_minutes, 0) AS planned_minutes,
                       COALESCE(actual.actual_minutes, 0) AS actual_minutes,
                       COALESCE(work.open_work, 0) AS open_work,
                       COALESCE(work.completed_work, 0) AS completed_work,
                       COALESCE(work.estimated_scope, 0) AS estimated_scope,
                       COALESCE(work.completed_estimate, 0) AS completed_estimate,
                       COALESCE(velocity.velocity_minutes, 0) AS velocity_minutes,
                       COALESCE(sprints.active_sprints, 0) AS active_sprints,
                       COALESCE(releases.active_releases, 0) AS active_releases,
                       COALESCE(releases.at_risk_releases, 0) AS at_risk_releases,
                       COALESCE(dependencies.blocked_dependencies, 0) AS blocked_dependencies
                FROM project p
                LEFT JOIN (
                    SELECT project_id,
                           SUM(ROUND(planned_minutes_per_week *
                               (DATEDIFF(LEAST(end_date, DATE_SUB(?, INTERVAL 1 DAY)),
                                         GREATEST(start_date, ?)) + 1) / 7, 0)) AS planned_minutes
                    FROM resource_allocation
                    WHERE tenant_id = ? AND status = 'ACTIVE'
                      AND start_date < ? AND end_date >= ?
                    GROUP BY project_id
                ) plan ON plan.project_id = p.id
                LEFT JOIN (
                    SELECT COALESCE(te.project_id, wi.project_id) AS project_id,
                           SUM(te.minutes) AS actual_minutes
                    FROM time_entry te
                    LEFT JOIN work_item wi ON wi.tenant_id = te.tenant_id AND wi.id = te.work_item_id
                    WHERE te.tenant_id = ? AND te.entry_date >= ? AND te.entry_date < ?
                    GROUP BY COALESCE(te.project_id, wi.project_id)
                ) actual ON actual.project_id = p.id
                LEFT JOIN (
                    SELECT project_id,
                           SUM(status NOT IN ('DONE', 'CANCELLED')) AS open_work,
                           SUM(status = 'DONE') AS completed_work,
                           SUM(CASE WHEN status <> 'CANCELLED' THEN COALESCE(estimate_minutes, 0) ELSE 0 END) AS estimated_scope,
                           SUM(CASE WHEN status = 'DONE' THEN COALESCE(estimate_minutes, 0) ELSE 0 END) AS completed_estimate
                    FROM work_item
                    WHERE tenant_id = ?
                    GROUP BY project_id
                ) work ON work.project_id = p.id
                LEFT JOIN (
                    SELECT COALESCE(te.project_id, wi.project_id) AS project_id,
                           SUM(te.minutes) / 4 AS velocity_minutes
                    FROM time_entry te
                    LEFT JOIN work_item wi ON wi.tenant_id = te.tenant_id AND wi.id = te.work_item_id
                    WHERE te.tenant_id = ? AND te.entry_date >= DATE_SUB(CURRENT_DATE, INTERVAL 28 DAY)
                    GROUP BY COALESCE(te.project_id, wi.project_id)
                ) velocity ON velocity.project_id = p.id
                LEFT JOIN (
                    SELECT project_id, COUNT(*) AS active_sprints
                    FROM sprint
                    WHERE tenant_id = ? AND status = 'ACTIVE'
                    GROUP BY project_id
                ) sprints ON sprints.project_id = p.id
                LEFT JOIN (
                    SELECT project_id,
                           SUM(status IN ('PLANNED', 'IN_PROGRESS', 'AT_RISK')) AS active_releases,
                           SUM(status = 'AT_RISK' OR (target_date < CURRENT_DATE AND status NOT IN ('RELEASED', 'CANCELLED'))) AS at_risk_releases
                    FROM release_item
                    WHERE tenant_id = ?
                    GROUP BY project_id
                ) releases ON releases.project_id = p.id
                LEFT JOIN (
                    SELECT blocked.project_id, COUNT(*) AS blocked_dependencies
                    FROM work_item_dependency dependency
                    JOIN work_item blocked ON blocked.tenant_id = dependency.tenant_id
                                          AND blocked.id = dependency.blocked_work_item_id
                    WHERE dependency.tenant_id = ? AND dependency.status = 'OPEN'
                    GROUP BY blocked.project_id
                ) dependencies ON dependencies.project_id = p.id
                WHERE p.tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(to));
        args.add(Date.valueOf(from));
        args.add(uuidBytes(tenantId));
        args.add(Date.valueOf(to));
        args.add(Date.valueOf(from));
        args.add(uuidBytes(tenantId));
        args.add(Date.valueOf(from));
        args.add(Date.valueOf(to));
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(tenantId));
        args.add(uuidBytes(tenantId));
        if (projectId != null) { sql.append(" AND p.id = ?"); args.add(uuidBytes(projectId)); }
        sql.append(" ORDER BY p.name, p.id");
        return jdbcTemplate.query(sql.toString(), this::map, args.toArray());
    }

    private DeliveryForecastRow map(ResultSet rs, int rowNumber) throws SQLException {
        long planned = rs.getLong("planned_minutes");
        long actual = rs.getLong("actual_minutes");
        int open = rs.getInt("open_work");
        int completed = rs.getInt("completed_work");
        long estimatedScope = rs.getLong("estimated_scope");
        long completedEstimate = rs.getLong("completed_estimate");
        long remainingEstimate = Math.max(0, estimatedScope - completedEstimate);
        long velocity = rs.getLong("velocity_minutes");
        Integer projectedDays = remainingEstimate > 0 && velocity > 0
                ? (int) Math.ceil((remainingEstimate * 7.0) / velocity) : null;
        int total = open + completed;
        int completion = total == 0 ? 0 : Math.round((completed * 100.0f) / total);
        int activeReleases = rs.getInt("active_releases");
        int atRiskReleases = rs.getInt("at_risk_releases");
        int blocked = rs.getInt("blocked_dependencies");
        return new DeliveryForecastRow(uuid(rs.getBytes("id")), rs.getString("project_key"),
                rs.getString("name"), planned, actual, open, completed, completion,
                rs.getInt("active_sprints"), activeReleases, atRiskReleases, blocked,
                status(planned, actual, open, completed, atRiskReleases, blocked), estimatedScope,
                remainingEstimate, velocity, projectedDays);
    }

    private static String status(long planned, long actual, int open, int completed,
                                 int atRiskReleases, int blockedDependencies) {
        if (atRiskReleases > 0 || blockedDependencies > 0) return "AT_RISK";
        if (planned == 0 && actual == 0) return "NO_PLAN";
        if (planned == 0 || actual > planned) return "OVER_PLAN";
        if (open == 0 && completed > 0) return "COMPLETE";
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
