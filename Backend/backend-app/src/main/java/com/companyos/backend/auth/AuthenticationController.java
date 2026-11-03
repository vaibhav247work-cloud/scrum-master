package com.companyos.backend.auth;

import com.companyos.auth.local.LocalAuthenticationService;
import com.companyos.auth.local.LocalLoginCommand;
import com.companyos.auth.local.LocalLoginResult;
import com.companyos.auth.local.SessionAuthenticationFilter;
import com.companyos.backend.security.PublicEndpointRateLimiter;
import com.companyos.identity.access.TenantUserAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final LocalAuthenticationService authenticationService;
    private final TenantUserAdministrationService userAdministrationService;
    private final PublicEndpointRateLimiter rateLimiter;
    private final boolean secureCookie;

    public AuthenticationController(
            LocalAuthenticationService authenticationService,
            TenantUserAdministrationService userAdministrationService,
            PublicEndpointRateLimiter rateLimiter,
            @Value("${security.session.cookie-secure:false}") boolean secureCookie) {
        this.authenticationService = authenticationService;
        this.userAdministrationService = userAdministrationService;
        this.rateLimiter = rateLimiter;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(
            @Valid @RequestBody ActivationRequest request, HttpServletRequest httpRequest) {
        rateLimiter.checkActivation(httpRequest);
        userAdministrationService.activate(request.invitationId(), request.activationToken(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        rateLimiter.checkLogin(httpRequest, request.tenantKey(), request.email());
        LocalLoginResult result = authenticationService.login(new LocalLoginCommand(
                request.tenantKey(), request.email(), request.password()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(result.rawToken(), result.ttl()).toString())
                .body(LoginResponse.from(result));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (authentication != null && authentication.getCredentials() instanceof String rawToken) {
            authenticationService.logout(rawToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                .build();
    }

    private ResponseCookie sessionCookie(String token, Duration ttl) {
        return ResponseCookie.from(SessionAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(ttl)
                .build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(SessionAuthenticationFilter.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 80) String tenantKey,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    public record LoginResponse(String tenantId, String userId, String tenantKey, String email, String expiresAt) {
        static LoginResponse from(LocalLoginResult result) {
            return new LoginResponse(
                    result.session().tenantId().toString(),
                    result.session().userId().toString(),
                    result.session().tenantKey(),
                    result.session().email(),
                    result.session().expiresAt().toString());
        }
    }

    public record CsrfResponse(String token) {
    }

    public record ActivationRequest(
            @NotNull UUID invitationId,
            @NotBlank @Size(max = 256) String activationToken,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }
}
