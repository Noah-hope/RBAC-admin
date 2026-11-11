package com.rbac.rbac0.service;

public final class ManagementFailure extends RuntimeException {

    public enum Reason {
        FORBIDDEN, NOT_FOUND, CONFLICT, INVALID_REQUEST, OUTSIDE_MANAGEMENT_SCOPE, APPLICATION_MISMATCH
    }

    private final Reason reason;

    public ManagementFailure(Reason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
