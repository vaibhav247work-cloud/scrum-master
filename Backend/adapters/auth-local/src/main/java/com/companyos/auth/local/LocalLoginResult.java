package com.companyos.auth.local;

import com.companyos.security.contract.SessionData;

import java.time.Duration;

public record LocalLoginResult(String rawToken, SessionData session, Duration ttl) {
}
