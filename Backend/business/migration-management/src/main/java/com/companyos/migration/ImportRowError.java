package com.companyos.migration;

public record ImportRowError(int rowNumber, String sourceKey, String field, String code, String message) { }
