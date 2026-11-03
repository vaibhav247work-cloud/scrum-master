package com.companyos.backend.auth;

import com.companyos.security.contract.AuthorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AuthorizationProbeController {
    private final AuthorizationService authorizationService;

    public AuthorizationProbeController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GetMapping("/authorization-check")
    public AuthorizationResponse authorizationCheck() {
        authorizationService.requirePermission("tenant.user.read");
        return new AuthorizationResponse("tenant.user.read", "granted");
    }

    public record AuthorizationResponse(String permission, String status) {
    }
}
