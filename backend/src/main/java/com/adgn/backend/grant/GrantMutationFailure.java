package com.adgn.backend.grant;

public final class GrantMutationFailure extends RuntimeException {
    private final String code;
    public GrantMutationFailure(String code){super("Grant mutation rejected: "+code);this.code=code;}
    public String code(){return code;}
}
