package com.companyos.persistence.mysql;

import com.companyos.resource.ResourceActivity;
import com.companyos.resource.ResourceAssignedWork;
import com.companyos.resource.ResourceWorkloadMember;
import com.companyos.resource.ResourceWorkloadStore;
import com.companyos.resource.ResourceTeamMembership;
import com.companyos.resource.CreateResourceAllocationCommand;
import com.companyos.resource.ResourceAllocationSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MysqlResourceWorkloadStore implements ResourceWorkloadStore {
    private static final int DEFAULT_CAPACITY_MINUTES = 2400;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlResourceWorkloadStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ResourceWorkloadMember> findWorkload(UUID tenantId, LocalDate weekStart, int recentActivityDays) {
        Map<UUID, MemberBuilder> members = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT u.id, u.email, u.display_name, u.status,
                       COALESCE(c.weekly_capacity_minutes, ?) AS weekly_capacity_minutes,
                       COALESCE(a.planned_minutes, 0) AS planned_minutes
                FROM tenant_user u
                LEFT JOIN resource_user_capacity c
                  ON c.tenant_id = u.tenant_id AND c.user_id = u.id
                LEFT JOIN (
                    SELECT tenant_id, user_id, SUM(planned_minutes_per_week) AS planned_minutes
                    FROM resource_allocation
                    WHERE status = 'ACTIVE' AND start_date <= ? AND end_date >= ?
                    GROUP BY tenant_id, user_id
                ) a ON a.tenant_id = u.tenant_id AND a.user_id = u.id
                WHERE u.tenant_id = ? AND u.status = 'ACTIVE'
                ORDER BY u.display_name, u.id
                """, resultSet -> {
                    UUID userId = uuid(resultSet.getBytes("id"));
                    members.put(userId, new MemberBuilder(userId, resultSet.getString("email"),
                            resultSet.getString("display_name"), resultSet.getString("status"),
                            resultSet.getInt("weekly_capacity_minutes"), resultSet.getInt("planned_minutes")));
                }, DEFAULT_CAPACITY_MINUTES, Date.valueOf(weekStart.plusDays(6)), Date.valueOf(weekStart), uuidBytes(tenantId));

        if (members.isEmpty()) return List.of();

        jdbcTemplate.query("""
                SELECT m.user_id, t.id AS team_id, t.team_key, t.name, m.membership_role
                FROM team_membership m
                JOIN team t ON t.tenant_id = m.tenant_id AND t.id = m.team_id
                WHERE m.tenant_id = ? AND m.status = 'ACTIVE' AND t.status = 'ACTIVE'
                ORDER BY m.user_id, t.name, t.id
                """, resultSet -> {
                    MemberBuilder member = members.get(uuid(resultSet.getBytes("user_id")));
                    if (member != null) {
                        member.teams.add(new ResourceTeamMembership(uuid(resultSet.getBytes("team_id")),
                                resultSet.getString("team_key"), resultSet.getString("name"),
                                resultSet.getString("membership_role")));
                    }
                }, uuidBytes(tenantId));

        jdbcTemplate.query("""
                SELECT assignee_user_id, id, work_key, project_id, sprint_id, title,
                       work_type, priority, status, updated_at
                FROM work_item
                WHERE tenant_id = ?
                  AND assignee_user_id IS NOT NULL
                  AND status NOT IN ('DONE', 'CANCELLED')
                ORDER BY assignee_user_id, updated_at DESC, id DESC
                """, resultSet -> {
                    UUID userId = uuid(resultSet.getBytes("assignee_user_id"));
                    MemberBuilder member = members.get(userId);
                    if (member != null) {
                        member.assignedWork.add(new ResourceAssignedWork(
                                uuid(resultSet.getBytes("id")), resultSet.getString("work_key"),
                                nullableUuid(resultSet.getBytes("project_id")), nullableUuid(resultSet.getBytes("sprint_id")),
                                resultSet.getString("title"), resultSet.getString("work_type"),
                                resultSet.getString("priority"), resultSet.getString("status"),
                                resultSet.getTimestamp("updated_at").toInstant()));
                    }
                }, uuidBytes(tenantId));

        LocalDate activityFrom = weekStart.minusDays(Math.max(1, recentActivityDays));
        LocalDate activityUntil = weekStart.plusDays(7);
        jdbcTemplate.query("""
                SELECT user_id, id, work_item_id, project_id, sprint_id, entry_date,
                       minutes, description, source, created_at
                FROM time_entry
                WHERE tenant_id = ? AND entry_date >= ? AND entry_date < ?
                ORDER BY user_id, created_at DESC, id DESC
                """, resultSet -> {
                    UUID userId = uuid(resultSet.getBytes("user_id"));
                    MemberBuilder member = members.get(userId);
                    if (member == null) return;
                    Date entryDate = resultSet.getDate("entry_date");
                    Timestamp createdAt = resultSet.getTimestamp("created_at");
                    ResourceActivity activity = new ResourceActivity(
                            uuid(resultSet.getBytes("id")), nullableUuid(resultSet.getBytes("work_item_id")),
                            nullableUuid(resultSet.getBytes("project_id")), nullableUuid(resultSet.getBytes("sprint_id")),
                            entryDate.toLocalDate(), resultSet.getInt("minutes"),
                            resultSet.getString("description"), resultSet.getString("source"), createdAt.toInstant());
                    member.recentActivity.add(activity);
                    if (!activity.entryDate().isBefore(weekStart) && activity.entryDate().isBefore(activityUntil)) {
                        member.loggedMinutesThisWeek += activity.minutes();
                    }
                    if (member.lastActivityAt == null || createdAt.toInstant().isAfter(member.lastActivityAt)) {
                        member.lastActivityAt = createdAt.toInstant();
                    }
                }, uuidBytes(tenantId), Date.valueOf(activityFrom), Date.valueOf(activityUntil));

        return members.values().stream().map(MemberBuilder::build).toList();
    }

    @Override
    public List<ResourceAllocationSummary> findAllocations(UUID tenantId, LocalDate weekStart, LocalDate weekEnd) {
        return jdbcTemplate.query("""
                SELECT id, user_id, project_id, sprint_id, team_id, start_date, end_date,
                       planned_minutes_per_week, status, note, created_at
                FROM resource_allocation
                WHERE tenant_id = ? AND status = 'ACTIVE'
                  AND start_date <= ? AND end_date >= ?
                ORDER BY start_date, user_id, created_at, id
                LIMIT 500
                """, this::mapAllocation, uuidBytes(tenantId), Date.valueOf(weekEnd), Date.valueOf(weekStart));
    }

    @Override
    public ResourceAllocationSummary createAllocation(UUID tenantId, UUID actorId, CreateResourceAllocationCommand command) {
        Integer capacity = jdbcTemplate.query("""
                SELECT COALESCE(c.weekly_capacity_minutes, 2400) AS capacity, u.status
                FROM tenant_user u
                LEFT JOIN resource_user_capacity c ON c.tenant_id = u.tenant_id AND c.user_id = u.id
                WHERE u.tenant_id = ? AND u.id = ?
                FOR UPDATE
                """, rs -> rs.next() ? ("ACTIVE".equals(rs.getString("status")) ? rs.getInt("capacity") : -1) : null,
                uuidBytes(tenantId), uuidBytes(command.userId()));
        if (capacity == null) throw new IllegalArgumentException("user was not found");
        if (capacity < 0) throw new IllegalArgumentException("user is not active");
        Integer allocated = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(planned_minutes_per_week), 0)
                FROM resource_allocation
                WHERE tenant_id = ? AND user_id = ? AND status = 'ACTIVE'
                  AND start_date <= ? AND end_date >= ?
                """, Integer.class, uuidBytes(tenantId), uuidBytes(command.userId()),
                Date.valueOf(command.endDate()), Date.valueOf(command.startDate()));
        if ((allocated == null ? 0 : allocated) + command.plannedMinutesPerWeek() > capacity) {
            throw new IllegalArgumentException("allocation exceeds the user's weekly capacity");
        }
        UUID allocationId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO resource_allocation
                    (id, tenant_id, user_id, project_id, sprint_id, team_id, start_date, end_date,
                     planned_minutes_per_week, status, note, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                """, uuidBytes(allocationId), uuidBytes(tenantId), uuidBytes(command.userId()),
                uuidBytes(command.projectId()), uuidBytes(command.sprintId()), uuidBytes(command.teamId()),
                Date.valueOf(command.startDate()), Date.valueOf(command.endDate()), command.plannedMinutesPerWeek(),
                command.note(), uuidBytes(actorId), Timestamp.from(now), Timestamp.from(now));
        recordMutation(tenantId, actorId, allocationId, command, now);
        return new ResourceAllocationSummary(allocationId, command.userId(), command.projectId(), command.sprintId(),
                command.teamId(), command.startDate(), command.endDate(), command.plannedMinutesPerWeek(), "ACTIVE", command.note(), now);
    }

    private ResourceAllocationSummary mapAllocation(ResultSet rs, int row) throws SQLException {
        return new ResourceAllocationSummary(uuid(rs.getBytes("id")), uuid(rs.getBytes("user_id")),
                uuid(rs.getBytes("project_id")), nullableUuid(rs.getBytes("sprint_id")), nullableUuid(rs.getBytes("team_id")),
                rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
                rs.getInt("planned_minutes_per_week"), rs.getString("status"), rs.getString("note"),
                rs.getTimestamp("created_at").toInstant());
    }

    private void recordMutation(UUID tenantId, UUID actorId, UUID allocationId,
                                CreateResourceAllocationCommand command, Instant now) {
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("allocationId", allocationId.toString());
            values.put("userId", command.userId().toString());
            values.put("projectId", command.projectId().toString());
            values.put("plannedMinutesPerWeek", command.plannedMinutesPerWeek());
            String payload = objectMapper.writeValueAsString(values);
            jdbcTemplate.update("""
                    INSERT INTO audit_event (id, tenant_id, actor_id, event_type, aggregate_type, aggregate_id,
                                             correlation_id, occurred_at, payload_json)
                    VALUES (?, ?, ?, 'RESOURCE_ALLOCATION_CREATED', 'RESOURCE_ALLOCATION', ?, NULL, ?, CAST(? AS JSON))
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(actorId), uuidBytes(allocationId), Timestamp.from(now), payload);
            jdbcTemplate.update("""
                    INSERT INTO outbox_event (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                                              actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                    VALUES (?, ?, 'RESOURCE_ALLOCATION_CREATED', 1, 'RESOURCE_ALLOCATION', ?, ?, NULL, NULL, ?, CAST(? AS JSON), ?)
                    """, uuidBytes(UUID.randomUUID()), uuidBytes(tenantId), uuidBytes(allocationId), uuidBytes(actorId), Timestamp.from(now), payload, Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize resource allocation event", exception);
        }
    }

    private static final class MemberBuilder {
        private final UUID userId;
        private final String email;
        private final String displayName;
        private final String status;
        private final int capacityMinutesPerWeek;
        private final int plannedAllocationMinutesThisWeek;
        private final List<ResourceAssignedWork> assignedWork = new ArrayList<>();
        private final List<ResourceActivity> recentActivity = new ArrayList<>();
        private final List<ResourceTeamMembership> teams = new ArrayList<>();
        private int loggedMinutesThisWeek;
        private Instant lastActivityAt;

        private MemberBuilder(UUID userId, String email, String displayName, String status, int capacityMinutesPerWeek,
                              int plannedAllocationMinutesThisWeek) {
            this.userId = userId;
            this.email = email;
            this.displayName = displayName;
            this.status = status;
            this.capacityMinutesPerWeek = capacityMinutesPerWeek;
            this.plannedAllocationMinutesThisWeek = plannedAllocationMinutesThisWeek;
        }

        private ResourceWorkloadMember build() {
            int utilization = capacityMinutesPerWeek == 0
                    ? 0 : Math.round((loggedMinutesThisWeek * 100.0f) / capacityMinutesPerWeek);
            String loadStatus = utilization >= 100 ? "OVERLOADED"
                    : utilization >= 80 ? "AT_RISK"
                    : assignedWork.isEmpty() && loggedMinutesThisWeek == 0 ? "AVAILABLE" : "BALANCED";
            int actualVsPlanned = plannedAllocationMinutesThisWeek == 0 ? 0
                    : Math.round((loggedMinutesThisWeek * 100.0f) / plannedAllocationMinutesThisWeek);
            String allocationStatus = plannedAllocationMinutesThisWeek == 0 ? "UNPLANNED"
                    : actualVsPlanned > 110 ? "OVER_PLAN"
                    : actualVsPlanned >= 80 ? "ON_TRACK" : "UNDER_PLAN";
            return new ResourceWorkloadMember(userId, email, displayName, status, capacityMinutesPerWeek,
                    loggedMinutesThisWeek, assignedWork.size(), utilization, loadStatus, lastActivityAt,
                    List.copyOf(assignedWork), List.copyOf(recentActivity), List.copyOf(teams),
                    plannedAllocationMinutesThisWeek, actualVsPlanned, allocationStatus);
        }
    }

    private static byte[] uuidBytes(UUID value) {
        if (value == null) return null;
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static UUID nullableUuid(byte[] bytes) {
        return bytes == null ? null : uuid(bytes);
    }
}
