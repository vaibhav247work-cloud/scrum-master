package com.companyos.organization;

import java.util.UUID;

public record CreateOrganizationUnitCommand(
        UUID parentUnitId, String unitKey, String name, String unitType, UUID managerUserId) {
}
