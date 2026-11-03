package com.companyos.integration;

public record CreateIntegrationConnectionCommand(String connectionType, String name, String baseUrl,
                                                 String externalAccount, String secret) { }
