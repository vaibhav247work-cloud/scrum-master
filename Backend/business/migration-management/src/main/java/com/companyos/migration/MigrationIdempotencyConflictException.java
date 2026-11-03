package com.companyos.migration;

public class MigrationIdempotencyConflictException extends RuntimeException {
    public MigrationIdempotencyConflictException() {
        super("Idempotency-Key was already used with a different import request");
    }
}
