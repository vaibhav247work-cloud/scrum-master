package com.companyos.auth.local;

import com.companyos.security.contract.SessionData;
import com.companyos.security.contract.SessionStore;
import com.companyos.tenantcontext.TenantContext;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "company_os_session";

    private final SessionStore sessionStore;

    public SessionAuthenticationFilter(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String rawToken = token(request);
        try {
            if (rawToken != null) {
                sessionStore.find(rawToken)
                        .filter(session -> session.expiresAt().isAfter(Instant.now()))
                        .ifPresent(session -> authenticate(request, session, rawToken));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(HttpServletRequest request, SessionData session, String rawToken) {
        var authorities = session.authorities().stream().map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken(session, rawToken, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContextHolder.set(new TenantContext(
                session.tenantId(),
                session.userId(),
                session.tenantKey(),
                session.email(),
                session.displayName(),
                session.authorities()));
    }

    private static String token(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return null;
    }
}
