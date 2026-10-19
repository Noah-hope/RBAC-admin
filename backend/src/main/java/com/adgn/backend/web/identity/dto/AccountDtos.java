package com.adgn.backend.web.identity.dto;

public final class AccountDtos {
    private AccountDtos(){ }
    public record Create(String employeeId,String loginName,String temporaryPassword,String confirmPassword,String reason){
        @Override public String toString(){return "AccountCreate[redacted]";}
    }
    public record State(Long expectedVersion,Boolean enabled,String reason){ }
    public record Reset(Long expectedVersion,String temporaryPassword,String confirmPassword,String reason){
        @Override public String toString(){return "AccountPasswordReset[redacted]";}
    }
}
