package com.companyos.auth.local;

import com.companyos.security.contract.AuthorizationService;
import com.companyos.tenantcontext.TenantContext;
import com.companyos.tenantcontext.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantAuthorizationService implements AuthorizationService {
    @Override
    public boolean hasPermission(String permission) {
        TenantContext context = TenantContextHolder.current();
        return context != null && (context.authorities().contains("PERM_" + permission)
                || context.authorities().contains("PERM_*"));
    }

    @Override
    public void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
        }
    }
}
