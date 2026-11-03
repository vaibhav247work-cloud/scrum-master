package com.companyos.security.contract;

public class RateLimitUnavailableException extends RuntimeException {
    public RateLimitUnavailableException(Throwable cause) {
        super("Rate limiting service is unavailable", cause);
    }
}
