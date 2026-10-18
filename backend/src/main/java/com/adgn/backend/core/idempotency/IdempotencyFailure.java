package com.adgn.backend.core.idempotency;
public final class IdempotencyFailure extends RuntimeException {
    public enum Code { IDEMPOTENCY_CONFLICT, REQUEST_IN_PROGRESS, AUTHENTICATION_FAILED, FORBIDDEN }
    private final Code code; private final long retryAfter;
    public IdempotencyFailure(Code code,long retryAfter) { super(code.name()); this.code=code; this.retryAfter=retryAfter; }
    public Code code() { return code; }
    public long retryAfter() { return retryAfter; }
}
