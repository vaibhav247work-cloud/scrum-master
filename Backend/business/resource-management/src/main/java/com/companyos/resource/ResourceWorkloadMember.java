package com.companyos.resource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResourceWorkloadMember(
        UUID userId,
        String email,
        String displayName,
        String status,
        int capacityMinutesPerWeek,
        int loggedMinutesThisWeek,
        int openWorkItemCount,
        int utilizationPercent,
        String loadStatus,
        Instant lastActivityAt,
        List<ResourceAssignedWork> assignedWork,
        List<ResourceActivity> recentActivity,
        List<ResourceTeamMembership> teams,
        int plannedAllocationMinutesThisWeek,
        int actualVsPlannedPercent,
        String allocationStatus) {
    public ResourceWorkloadMember(UUID userId, String email, String displayName, String status,
                                  int capacityMinutesPerWeek, int loggedMinutesThisWeek, int openWorkItemCount,
                                  int utilizationPercent, String loadStatus, Instant lastActivityAt,
                                  List<ResourceAssignedWork> assignedWork, List<ResourceActivity> recentActivity,
                                  List<ResourceTeamMembership> teams) {
        this(userId, email, displayName, status, capacityMinutesPerWeek, loggedMinutesThisWeek,
                openWorkItemCount, utilizationPercent, loadStatus, lastActivityAt, assignedWork, recentActivity,
                teams, 0, 0, "UNPLANNED");
    }
}
