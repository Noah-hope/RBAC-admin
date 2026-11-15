package com.rbac.rbac0.service;

public final class AuthenticationFailure extends RuntimeException {

    public enum Reason {
        UNAUTHENTICATED, ACCOUNT_DISABLED, TOO_MANY_REQUESTS
    }

    public final Reason reason;
    public final long retryAfterSeconds;

    public AuthenticationFailure(Reason reason) {
        this(reason, 0);
    }

    public AuthenticationFailure(Reason reason, long retryAfterSeconds) {
        super(reason.name());
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
