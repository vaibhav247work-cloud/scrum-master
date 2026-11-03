package com.companyos.release;

import java.time.LocalDate;

public record CreateReleaseCommand(String releaseKey, String name, String description, LocalDate targetDate) {
}
