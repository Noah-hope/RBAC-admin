package com.adgn.backend.identity.security;

/** Public safe classification only; never include a token, password or storage exception. */
public final class IdentityFailure extends RuntimeException {
    private final int status;
    private final String code;
    private final long retryAfter;
    public IdentityFailure(int status, String code) { this(status,code,0); }
    public IdentityFailure(int status, String code, long retryAfter) {
        super(code); this.status=status; this.code=code; this.retryAfter=retryAfter;
    }
    public int status() { return status; }
    public String code() { return code; }
    public long retryAfter() { return retryAfter; }
    public static IdentityFailure authentication() { return new IdentityFailure(401,"AUTHENTICATION_FAILED"); }
    public static IdentityFailure revoked() { return new IdentityFailure(401,"SESSION_REVOKED"); }
    public static IdentityFailure invalid() { return new IdentityFailure(422,"VALIDATION_ERROR"); }
}
