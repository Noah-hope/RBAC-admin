package com.rbac.rbac0.service;

public final class DepartmentFailure extends RuntimeException {

    public enum Reason {
        FORBIDDEN, NOT_FOUND, INVALID_REQUEST
    }

    private final Reason reason;

    public DepartmentFailure(Reason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
