package com.adgn.backend.web.identity.dto;

import java.time.Instant;

public final class IdentityDtos {
    private IdentityDtos(){ }
    public record Login(String loginName,String password) {
        @Override public String toString(){return "Login[redacted]";}
    }
    public record PasswordChange(String currentPassword,String newPassword,String confirmPassword) {
        @Override public String toString(){return "PasswordChange[redacted]";}
    }
    public record Session(String sessionId,String accountId,String employeeId,String identity,String mode,
                          String accessToken,String tokenType,Instant expiresAt) {
        @Override public String toString(){return "Session[redacted]";}
    }
}
