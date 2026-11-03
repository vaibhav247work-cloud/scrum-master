package com.companyos.auth.local;

public record LocalLoginCommand(String tenantKey, String email, String password) {
}
