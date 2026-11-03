package com.companyos.security.contract;

public interface AuthorizationService {
    boolean hasPermission(String permission);

    void requirePermission(String permission);
}
