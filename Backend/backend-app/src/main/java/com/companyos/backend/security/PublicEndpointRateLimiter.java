package com.companyos.backend.security;

import com.companyos.security.contract.RateLimitDecision;
import com.companyos.security.contract.RateLimitExceededException;
import com.companyos.security.contract.RateLimitRule;
import com.companyos.security.contract.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class PublicEndpointRateLimiter {
    private final RateLimitService rateLimitService;
    private final boolean enabled;
    private final int loginIpLimit;
    private final int loginIdentityLimit;
    private final Duration loginWindow;
    private final int onboardingIpLimit;
    private final Duration onboardingWindow;
    private final int activationIpLimit;
    private final Duration activationWindow;
    private final int webhookIpLimit;
    private final Duration webhookWindow;

    public PublicEndpointRateLimiter(
            RateLimitService rateLimitService,
            @Value("${security.rate-limit.enabled:true}") boolean enabled,
            @Value("${security.rate-limit.login.ip-limit:30}") int loginIpLimit,
            @Value("${security.rate-limit.login.identity-limit:10}") int loginIdentityLimit,
            @Value("${security.rate-limit.login.window:PT15M}") Duration loginWindow,
            @Value("${security.rate-limit.onboarding.ip-limit:5}") int onboardingIpLimit,
            @Value("${security.rate-limit.onboarding.window:PT1H}") Duration onboardingWindow,
            @Value("${security.rate-limit.activation.ip-limit:10}") int activationIpLimit,
            @Value("${security.rate-limit.activation.window:PT1H}") Duration activationWindow,
            @Value("${security.rate-limit.webhook.ip-limit:120}") int webhookIpLimit,
            @Value("${security.rate-limit.webhook.window:PT1M}") Duration webhookWindow) {
        this.rateLimitService = rateLimitService;
        this.enabled = enabled;
        this.loginIpLimit = loginIpLimit;
        this.loginIdentityLimit = loginIdentityLimit;
        this.loginWindow = loginWindow;
        this.onboardingIpLimit = onboardingIpLimit;
        this.onboardingWindow = onboardingWindow;
        this.activationIpLimit = activationIpLimit;
        this.activationWindow = activationWindow;
        this.webhookIpLimit = webhookIpLimit;
        this.webhookWindow = webhookWindow;
    }

    public void checkLogin(HttpServletRequest request, String tenantKey, String email) {
        if (!enabled) return;
        check(List.of(
                new RateLimitRule("login:ip:" + clientIp(request), loginIpLimit, loginWindow),
                new RateLimitRule("login:identity:" + normalized(tenantKey) + ":" + normalized(email),
                        loginIdentityLimit, loginWindow)));
    }

    public void checkOnboarding(HttpServletRequest request) {
        if (!enabled) return;
        check(List.of(new RateLimitRule("onboarding:ip:" + clientIp(request), onboardingIpLimit, onboardingWindow)));
    }

    public void checkActivation(HttpServletRequest request) {
        if (!enabled) return;
        check(List.of(new RateLimitRule("activation:ip:" + clientIp(request), activationIpLimit, activationWindow)));
    }

    public void checkIntegrationWebhook(HttpServletRequest request, UUID connectionId) {
        if (!enabled) return;
        check(List.of(new RateLimitRule("integration-webhook:ip:" + clientIp(request) + ":" + connectionId,
                webhookIpLimit, webhookWindow)));
    }

    private void check(List<RateLimitRule> rules) {
        RateLimitDecision decision = rateLimitService.consume(rules);
        if (!decision.allowed()) throw new RateLimitExceededException(decision.retryAfterSeconds());
    }

    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
