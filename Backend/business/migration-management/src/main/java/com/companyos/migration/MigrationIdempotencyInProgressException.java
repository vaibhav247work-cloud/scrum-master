package com.companyos.migration;

public class MigrationIdempotencyInProgressException extends RuntimeException {
    public MigrationIdempotencyInProgressException() {
        super("An import with this Idempotency-Key is already being processed");
    }
}
