package com.companyos.backend;

import com.companyos.auth.local.SessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final String contentSecurityPolicy;

    public SecurityConfiguration(
            SessionAuthenticationFilter sessionAuthenticationFilter,
            @Value("${security.headers.content-security-policy:default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'}")
            String contentSecurityPolicy) {
        this.sessionAuthenticationFilter = sessionAuthenticationFilter;
        this.contentSecurityPolicy = contentSecurityPolicy;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers("/api/v1/integrations/webhooks/**"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/onboarding/**").permitAll()
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/activate").permitAll()
                        .requestMatchers("/api/v1/integrations/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
