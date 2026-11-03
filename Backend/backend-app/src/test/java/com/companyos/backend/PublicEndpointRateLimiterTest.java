package com.companyos.backend;

import com.companyos.backend.security.PublicEndpointRateLimiter;
import com.companyos.security.contract.RateLimitDecision;
import com.companyos.security.contract.RateLimitExceededException;
import com.companyos.security.contract.RateLimitRule;
import com.companyos.security.contract.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicEndpointRateLimiterTest {
    private final RateLimitService service = mock(RateLimitService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final PublicEndpointRateLimiter limiter = new PublicEndpointRateLimiter(
            service, true, 30, 10, Duration.ofMinutes(15), 5, Duration.ofHours(1), 10, Duration.ofHours(1),
            120, Duration.ofMinutes(1));

    @Test
    void loginUsesIpAndIdentityBuckets() {
        when(request.getRemoteAddr()).thenReturn("192.0.2.10");
        when(service.consume(argThat(rules -> rules.size() == 2))).thenReturn(new RateLimitDecision(true, 0));

        limiter.checkLogin(request, "Tenant-A", "Owner@Example.com");

        verify(service).consume(argThat(rules -> rules.stream().map(RateLimitRule::key).toList().equals(List.of(
                "login:ip:192.0.2.10", "login:identity:tenant-a:owner@example.com"))));
    }

    @Test
    void rejectsWhenRedisLimiterDeniesRequest() {
        when(request.getRemoteAddr()).thenReturn("192.0.2.10");
        when(service.consume(argThat(rules -> rules.size() == 1))).thenReturn(new RateLimitDecision(false, 42));

        assertThrows(RateLimitExceededException.class, () -> limiter.checkOnboarding(request));
    }
}
