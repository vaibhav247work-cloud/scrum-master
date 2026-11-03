package com.companyos.persistence.mysql;

import com.companyos.reporting.ForecastCalibrationRow;
import com.companyos.reporting.ForecastCalibrationStore;
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
public class MysqlForecastCalibrationStore implements ForecastCalibrationStore {
    private final JdbcTemplate jdbcTemplate;

    public MysqlForecastCalibrationStore(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<ForecastCalibrationRow> calibration(UUID tenantId, LocalDate from, LocalDate to, UUID projectId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.project_key, p.name,
                       COUNT(DISTINCT work.id) AS sample_size,
                       SUM(work.estimate_minutes) AS estimated_minutes,
                       SUM(time.minutes) AS actual_minutes
                FROM project p
                JOIN work_item work ON work.tenant_id = p.tenant_id
                                   AND work.project_id = p.id
                                   AND work.status = 'DONE'
                                   AND work.estimate_minutes IS NOT NULL
                                   AND work.completed_at >= ? AND work.completed_at < ?
                JOIN time_entry time ON time.tenant_id = work.tenant_id
                                    AND time.work_item_id = work.id
                WHERE p.tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(from));
        args.add(Date.valueOf(to));
        args.add(uuidBytes(tenantId));
        if (projectId != null) { sql.append(" AND p.id = ?"); args.add(uuidBytes(projectId)); }
        sql.append(" GROUP BY p.id, p.project_key, p.name ORDER BY p.name, p.id");
        return jdbcTemplate.query(sql.toString(), this::map, args.toArray());
    }

    private ForecastCalibrationRow map(ResultSet rs, int rowNumber) throws SQLException {
        int sampleSize = rs.getInt("sample_size");
        long estimated = rs.getLong("estimated_minutes");
        long actual = rs.getLong("actual_minutes");
        double factor = estimated == 0 ? 1.0 : Math.max(0.25, Math.min(4.0, (double) actual / estimated));
        int accuracy = estimated == 0 ? 0 : (int) Math.round((actual * 100.0) / estimated);
        return new ForecastCalibrationRow(uuid(rs.getBytes("id")), rs.getString("project_key"),
                rs.getString("name"), sampleSize, estimated, actual, factor, accuracy);
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
