package com.companyos.quality;

import java.util.UUID;

public record CreateQualityGateCommand(UUID projectId, UUID releaseId, String gateKey, String gateType,
                                      boolean required) { }
