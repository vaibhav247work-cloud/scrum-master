package com.companyos.backend.auth;

import com.companyos.tenantcontext.TenantContext;
import com.companyos.tenantcontext.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {
    @GetMapping
    public CurrentUserResponse currentUser() {
        TenantContext context = TenantContextHolder.required();
        return new CurrentUserResponse(
                context.tenantId().toString(),
                context.userId().toString(),
                context.tenantKey(),
                context.email(),
                context.displayName(),
                context.authorities());
    }

    public record CurrentUserResponse(
            String tenantId,
            String userId,
            String tenantKey,
            String email,
            String displayName,
            Set<String> authorities) {
    }
}
