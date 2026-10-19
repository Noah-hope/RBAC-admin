package com.adgn.identityfixture;

import com.adgn.backend.identity.config.IdentityProperties;
import com.adgn.backend.identity.security.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class IdentitySecurityTest {
    @TempDir Path directory;
    private static RSAKey current,old,business,oa;
    private static final Instant NOW=Instant.parse("2026-10-18T00:00:00Z");
    private static final String ACCOUNT="71000000-0000-4000-8000-000000000001";
    @BeforeAll static void keys() throws Exception {
        current=new RSAKeyGenerator(2048).keyID("current").generate(); old=new RSAKeyGenerator(2048).keyID("old").generate();
        business=new RSAKeyGenerator(2048).keyID("business").generate(); oa=new RSAKeyGenerator(2048).keyID("oa").generate();
    }
    private IdentityProperties properties() throws Exception {
        var p=new IdentityProperties();p.setAdminKeyId("current");
        var adminFile=directory.resolve("admin.json");Files.writeString(adminFile,new JWKSet(List.of(current,old.toPublicJWK())).toString(false));
        var businessFile=directory.resolve("business.json");Files.writeString(businessFile,new JWKSet(business.toPublicJWK()).toString());
        var oaFile=directory.resolve("oa.json");Files.writeString(oaFile,new JWKSet(oa.toPublicJWK()).toString());
        p.setAdminJwkSet(adminFile.toString());p.setBusinessJwkSet(businessFile.toString());p.setOaJwkSet(oaFile.toString());return p;
    }
    private JWTClaimsSet.Builder admin() {
        return new JWTClaimsSet.Builder().subject(ACCOUNT).issuer("urn:rbac:admin").audience("rbac-admin")
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(NOW)).expirationTime(Date.from(NOW.plusSeconds(900)))
                .claim("sid",UUID.randomUUID().toString()).claim("credentialVersion",1L).claim("sessionEpoch",1L).claim("mode","NORMAL");
    }
    private String sign(JWTClaimsSet claims,RSAKey key,JWSAlgorithm algorithm) throws Exception {
        var token=new SignedJWT(new JWSHeader.Builder(algorithm).keyID(key.getKeyID()).build(),claims);
        token.sign(new RSASSASigner(key));return token.serialize();
    }
    @Test void rotationVerifiesOldKeyButNewTokensUseCurrentKid() throws Exception {
        var tokens=new IdentityTokens(properties(),Clock.fixed(NOW,ZoneOffset.UTC));
        var oldToken=sign(admin().build(),old,JWSAlgorithm.RS256);
        assertThat(tokens.verify(oldToken,IdentityTokens.Family.ADMIN).getSubject()).isEqualTo(ACCOUNT);
        var issued=tokens.issue(ACCOUNT,UUID.randomUUID().toString(),UUID.randomUUID().toString(),"NORMAL",1,1,NOW.plusSeconds(900));
        assertThat(SignedJWT.parse(issued).getHeader().getKeyID()).isEqualTo("current");
        assertThat(tokens.verify(issued,IdentityTokens.Family.ADMIN).getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
    }
    @Test void missingHistoricalKeyFailsClosed() throws Exception {
        var p=properties();Files.writeString(Path.of(p.getAdminJwkSet()),new JWKSet(current).toString(false));
        var tokens=new IdentityTokens(p,Clock.fixed(NOW,ZoneOffset.UTC));
        assertThatThrownBy(()->tokens.verify(sign(admin().build(),old,JWSAlgorithm.RS256),IdentityTokens.Family.ADMIN)).isInstanceOf(IdentityFailure.class);
    }
    @Test void rejectsExpiredFutureOverlongWrongIssuerWrongAudienceAndWrongAlgorithm() throws Exception {
        var tokens=new IdentityTokens(properties(),Clock.fixed(NOW,ZoneOffset.UTC));
        for(var invalid:List.of(admin().issueTime(Date.from(NOW.minusSeconds(1000))).expirationTime(Date.from(NOW.minusSeconds(61))).build(),
                admin().issueTime(Date.from(NOW.plusSeconds(61))).build(),admin().expirationTime(Date.from(NOW.plusSeconds(901))).build(),
                admin().issuer("untrusted").build(),admin().audience("rbac-business").build(),admin().claim("sid",null).build(),
                admin().jwtID(null).build())) {
            assertThatThrownBy(()->tokens.verify(sign(invalid,current,JWSAlgorithm.RS256),IdentityTokens.Family.ADMIN)).isInstanceOf(IdentityFailure.class);
        }
        assertThatThrownBy(()->tokens.verify(sign(admin().build(),current,JWSAlgorithm.RS512),IdentityTokens.Family.ADMIN)).isInstanceOf(IdentityFailure.class);
    }
    @Test void rejectsFractionalCredentialVersionInsteadOfRoundingItToAValidVersion() throws Exception {
        var tokens=new IdentityTokens(properties(),Clock.fixed(NOW,ZoneOffset.UTC));
        var raw=sign(admin().claim("credentialVersion",1.5).build(),current,JWSAlgorithm.RS256);
        assertThatThrownBy(()->tokens.verify(raw,IdentityTokens.Family.ADMIN)).isInstanceOf(IdentityFailure.class);
    }
    @Test void businessAndOaAudienceKeyAndBindingClaimsAreIsolated() throws Exception {
        var tokens=new IdentityTokens(properties(),Clock.fixed(NOW,ZoneOffset.UTC));
        var bc=new JWTClaimsSet.Builder().issuer("urn:rbac:business").audience("rbac-business").subject("fixture-business")
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(NOW)).expirationTime(Date.from(NOW.plusSeconds(300)))
                .claim("systemId",UUID.randomUUID().toString()).build();
        String bt=sign(bc,business,JWSAlgorithm.RS256);
        assertThat(tokens.verify(bt,IdentityTokens.Family.BUSINESS).getSubject()).isEqualTo("fixture-business");
        assertThatThrownBy(()->tokens.verify(bt,IdentityTokens.Family.OA)).isInstanceOf(IdentityFailure.class);
        assertThatThrownBy(()->tokens.verify(bt,IdentityTokens.Family.ADMIN)).isInstanceOf(IdentityFailure.class);
        var oc=new JWTClaimsSet.Builder(bc).issuer("urn:rbac:oa").audience("rbac-oa-callback").subject("fixture-oa")
                .claim("systemId",null).claim("oaInstanceId",UUID.randomUUID().toString()).build();
        assertThat(tokens.verify(sign(oc,oa,JWSAlgorithm.RS256),IdentityTokens.Family.OA).getSubject()).isEqualTo("fixture-oa");
    }
    @Test void missingPrivateKeyAndPrivateServiceTrustSetAreStartupFailuresWithoutMaterialInException() throws Exception {
        var p=properties();Files.writeString(Path.of(p.getAdminJwkSet()),new JWKSet(current.toPublicJWK()).toString());
        assertThatThrownBy(()->new IdentityTokens(p,Clock.systemUTC())).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("d\":");
        var invalid=properties();Files.writeString(Path.of(invalid.getBusinessJwkSet()),new JWKSet(business).toString(false));
        assertThatThrownBy(()->new IdentityTokens(invalid,Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }
    @Test void passwordEncodingPreservesSpacesSupportsLongUnicodeAndRejectsUnknownHashVersions() {
        var passwords=new PasswordPolicy();String plain=" 𠮷".repeat(40)+" ";
        String first=passwords.encode(plain),second=passwords.encode(plain);
        assertThat(first).isNotEqualTo(second).startsWith("{pbkdf2-v1}");
        assertThat(passwords.matches(plain,first)).isTrue();
        assertThat(passwords.matches(plain.trim(),first)).isFalse();
        assertThat(passwords.matches(plain,"{noop}"+plain)).isFalse();
        assertThat(passwords.matches(plain,null)).isFalse();
    }
    @Test void loginLimitsBothIpAndAccountAndExpiresWithPositiveRetryAfter() {
        var clock=new MutableClock();var limiter=new LoginRateLimiter(clock,2,2);
        limiter.admit("ip1","a");limiter.admit("ip1","b");
        assertThatThrownBy(()->limiter.admit("ip1","c")).isInstanceOfSatisfying(IdentityFailure.class,e->{
            assertThat(e.status()).isEqualTo(429);assertThat(e.retryAfter()).isEqualTo(60);
        });
        limiter.admit("ip2","a");
        assertThatThrownBy(()->limiter.admit("ip3","a")).isInstanceOf(IdentityFailure.class);
        clock.instant=NOW.plusSeconds(60);limiter.admit("ip1","a");
    }
    private static class MutableClock extends Clock {
        Instant instant=NOW;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return instant;}
    }
}
