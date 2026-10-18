package com.adgn.backend.identity.security;

import com.adgn.backend.identity.config.IdentityProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import org.springframework.security.oauth2.jwt.Jwt;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Only configured local keys are trusted. No token-controlled network discovery. */
public final class IdentityTokens {
    public enum Family {
        ADMIN("rbac-admin"), BUSINESS("rbac-business"), OA("rbac-oa-callback");
        final String audience;
        Family(String audience){this.audience=audience;}
    }
    private final Map<Family,Map<String,RSAKey>> keys=new EnumMap<>(Family.class);
    private final Map<Family,String> issuers=new EnumMap<>(Family.class);
    private final Clock clock;
    private final RSAKey signer;
    public IdentityTokens(IdentityProperties properties,Clock clock) {
        this.clock=clock;
        try {
            keys.put(Family.ADMIN,load(properties.getAdminJwkSet(),true));
            keys.put(Family.BUSINESS,load(properties.getBusinessJwkSet(),false));
            keys.put(Family.OA,load(properties.getOaJwkSet(),false));
            issuers.put(Family.ADMIN,issuer(properties.getAdminIssuer()));
            issuers.put(Family.BUSINESS,issuer(properties.getBusinessIssuer()));
            issuers.put(Family.OA,issuer(properties.getOaIssuer()));
            signer=keys.get(Family.ADMIN).get(properties.getAdminKeyId());
            if(signer==null || !signer.isPrivate()) throw new IllegalArgumentException();
        } catch(Exception invalid) {
            // Do not attach parsing errors that may contain key material.
            throw new IllegalStateException("Identity requires valid local RSA JWK sets and a current admin signing kid");
        }
    }
    private static String issuer(String text) {
        if(text==null || text.isBlank() || text.length()>300)throw new IllegalArgumentException();
        return text;
    }
    private static Map<String,RSAKey> load(String file,boolean allowPrivate) throws Exception {
        if(file==null || file.isBlank())throw new IllegalArgumentException();
        Path path=Path.of(file);
        if(!Files.isRegularFile(path) || Files.size(path)>131072)throw new IllegalArgumentException();
        Map<String,RSAKey> result=new HashMap<>();
        for(JWK key:JWKSet.parse(Files.readString(path)).getKeys()) {
            if(!(key instanceof RSAKey rsa) || rsa.getKeyID()==null || !rsa.getKeyID().matches("[A-Za-z0-9_.-]{1,64}")
                    || rsa.toRSAPublicKey().getModulus().bitLength()<2048 || (!allowPrivate && rsa.isPrivate())
                    || (rsa.getAlgorithm()!=null && !JWSAlgorithm.RS256.equals(rsa.getAlgorithm()))
                    || (rsa.getKeyUse()!=null && !KeyUse.SIGNATURE.equals(rsa.getKeyUse()))
                    || result.put(rsa.getKeyID(),rsa)!=null)throw new IllegalArgumentException();
        }
        if(result.isEmpty() || result.size()>16)throw new IllegalArgumentException();
        return Map.copyOf(result);
    }
    public String issue(String account,String sid,String jti,String mode,long credentials,long epoch,Instant expires) {
        try {
            var claims=new JWTClaimsSet.Builder().issuer(issuers.get(Family.ADMIN)).audience(Family.ADMIN.audience)
                    .subject(account).jwtID(jti).issueTime(Date.from(clock.instant())).expirationTime(Date.from(expires))
                    .claim("sid",sid).claim("mode",mode).claim("credentialVersion",credentials).claim("sessionEpoch",epoch).build();
            var token=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signer.getKeyID()).type(JOSEObjectType.JWT).build(),claims);
            token.sign(new RSASSASigner(signer)); return token.serialize();
        } catch(Exception failure) { throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE"); }
    }
    public Jwt verify(String raw,Family family) {
        try {
            if(raw==null || raw.length()>4096)throw new IllegalArgumentException();
            var token=SignedJWT.parse(raw); var header=token.getHeader();
            var key=keys.get(family).get(header.getKeyID());
            if(!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || key==null
                    || header.getJWKURL()!=null || header.getX509CertURL()!=null || header.getJWK()!=null
                    || !token.verify(new RSASSAVerifier(key.toRSAPublicKey())))throw new IllegalArgumentException();
            var c=token.getJWTClaimsSet();
            Instant now=clock.instant(), iat=c.getIssueTime().toInstant(), exp=c.getExpirationTime().toInstant();
            long limit=family==Family.ADMIN && "NORMAL".equals(c.getStringClaim("mode")) ? 900 : 300;
            if(!issuers.get(family).equals(c.getIssuer()) || !c.getAudience().equals(List.of(family.audience))
                    || c.getSubject()==null || c.getSubject().isBlank() || c.getSubject().length()>100
                    || exp.isBefore(now.minusSeconds(60)) || iat.isAfter(now.plusSeconds(60))
                    || !exp.isAfter(iat) || Duration.between(iat,exp).getSeconds()>limit
                    || (c.getNotBeforeTime()!=null && c.getNotBeforeTime().toInstant().isAfter(now.plusSeconds(60))))throw new IllegalArgumentException();
            uuid(c.getJWTID());
            if(family==Family.ADMIN) {
                uuid(c.getSubject()); uuid(c.getStringClaim("sid"));
                if(!Set.of("NORMAL","PASSWORD_CHANGE_ONLY").contains(c.getStringClaim("mode")))throw new IllegalArgumentException();
                boundedVersion(c.getClaim("credentialVersion")); boundedVersion(c.getClaim("sessionEpoch"));
            } else uuid(c.getStringClaim(family==Family.BUSINESS ? "systemId" : "oaInstanceId"));
            return Jwt.withTokenValue(raw).headers(h -> h.putAll(header.toJSONObject()))
                    .claims(claims -> claims.putAll(c.getClaims())).issuedAt(iat).expiresAt(exp).build();
        } catch(Exception invalid) { throw IdentityFailure.authentication(); }
    }
    private static void boundedVersion(Object value) {
        if(!(value instanceof Long || value instanceof Integer))throw new IllegalArgumentException();
        long v=((Number)value).longValue();
        if(v<0 || v>9007199254740991L)throw new IllegalArgumentException();
    }
    private static void uuid(String value) {
        if(value==null || !UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException();
    }
}
