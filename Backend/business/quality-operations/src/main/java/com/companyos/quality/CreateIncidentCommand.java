package com.companyos.quality;

import java.util.UUID;

public record CreateIncidentCommand(UUID projectId, UUID releaseId, String incidentKey, String title,
                                    String description, String severity, UUID ownerUserId) { }
