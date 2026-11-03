package com.companyos.idempotency;

public class IdempotencyUnavailableException extends RuntimeException {
    public IdempotencyUnavailableException(Throwable cause) {
        super("Idempotency service is temporarily unavailable", cause);
    }
}
