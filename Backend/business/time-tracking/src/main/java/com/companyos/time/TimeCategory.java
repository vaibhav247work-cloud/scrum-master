package com.companyos.time;

import java.time.Instant;
import java.util.UUID;

public record TimeCategory(UUID id, String categoryKey, String name, boolean enabled, Instant createdAt, Instant updatedAt) { }
