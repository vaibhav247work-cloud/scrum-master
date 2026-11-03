package com.companyos.workflow;

public record WorkflowStatusDefinition(String key, String label, boolean terminal, int position) { }
