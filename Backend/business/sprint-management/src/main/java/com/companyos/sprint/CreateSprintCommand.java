package com.companyos.sprint;

import java.time.LocalDate;

public record CreateSprintCommand(
        String sprintKey,
        String name,
        String goal,
        LocalDate startDate,
        LocalDate endDate) {
}
