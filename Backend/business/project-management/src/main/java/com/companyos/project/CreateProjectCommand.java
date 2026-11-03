package com.companyos.project;

import java.util.UUID;

public record CreateProjectCommand(String projectKey, String name, String description, UUID productId) {
    public CreateProjectCommand(String projectKey, String name, String description) {
        this(projectKey, name, description, null);
    }
}
