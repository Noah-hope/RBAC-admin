package com.adgn.backend.identity.security;

import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import java.util.Map;
import java.util.UUID;

public final class PasswordPolicy {
    private final DelegatingPasswordEncoder encoder;
    private final String dummy;
    public PasswordPolicy() {
        var pbkdf2=new Pbkdf2PasswordEncoder("",16,600000,256);
        pbkdf2.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        encoder=new DelegatingPasswordEncoder("pbkdf2-v1",Map.of("pbkdf2-v1",pbkdf2));
        dummy=encoder.encode(UUID.randomUUID().toString());
    }
    public boolean matches(String raw, String hash) {
        try { return encoder.matches(raw,hash==null ? dummy : hash) && hash!=null; }
        catch (IllegalArgumentException invalidFormat) {
            encoder.matches(raw,dummy);
            return false;
        }
    }
    public String encode(String raw) { length(raw,12,128); return encoder.encode(raw); }
    public static void length(String raw,int minimum,int maximum) {
        if(raw==null || raw.codePointCount(0,raw.length())<minimum || raw.codePointCount(0,raw.length())>maximum
                || raw.codePoints().anyMatch(c -> c>=0xD800 && c<=0xDFFF)) throw IdentityFailure.invalid();
    }
}
