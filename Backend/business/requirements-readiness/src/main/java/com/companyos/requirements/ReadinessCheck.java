package com.companyos.requirements;

import java.time.Instant;
import java.util.UUID;

public record ReadinessCheck(UUID id, String checkKey, String label, boolean required,
                             boolean completed, String evidence, Instant updatedAt) {
}
