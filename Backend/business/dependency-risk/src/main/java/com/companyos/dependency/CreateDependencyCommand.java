package com.companyos.dependency;

import java.util.UUID;

public record CreateDependencyCommand(UUID dependsOnWorkItemId, String dependencyType) {
}
