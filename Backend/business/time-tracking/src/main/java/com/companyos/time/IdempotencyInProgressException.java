package com.companyos.time;

public class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException() {
        super("A request with this Idempotency-Key is already in progress");
    }
}
