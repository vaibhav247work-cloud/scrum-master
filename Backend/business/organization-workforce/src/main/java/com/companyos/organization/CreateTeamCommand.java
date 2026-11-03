package com.companyos.organization;

import java.util.UUID;

public record CreateTeamCommand(
        UUID parentUnitId, String teamKey, String name, UUID managerUserId) {
}
