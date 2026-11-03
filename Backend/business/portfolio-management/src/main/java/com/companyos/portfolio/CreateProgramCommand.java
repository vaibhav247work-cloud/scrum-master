package com.companyos.portfolio;

public record CreateProgramCommand(String programKey, String name, String description, String status) { }
