package com.adgn.backend;

import com.adgn.backend.identity.service.*;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.security.IdentityTokens;
import com.adgn.backend.web.identity.dto.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.scope.service.ScopeInput;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.security.oauth2.jwt.Jwt;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class IdentityBoundaryContractTest {
    @Test void loggingCredentialBearingValuesCannotRevealPasswordsHashesOrTokens() {
        String secret="private-credential-fixture";Instant expires=Instant.parse("2026-10-26T00:00:00Z");
        Object[] values={new IdentityDtos.Login("user",secret),new IdentityDtos.PasswordChange(secret,secret,secret),
                new IdentityDtos.Session("s","a","e","ADMIN","NORMAL",secret,"Bearer",expires),
                new AccountDtos.Create("e","user",secret,secret,"reason"),new AccountDtos.Reset(1L,secret,secret,"reason"),
                new IdentityService.LoginResult(new IdentityService.SessionView("s","a","e","ADMIN","NORMAL",expires),secret),
                new IdentityRepository.Account("a","e",secret,true,true,false,1,1,true),
                new IdempotencyCoordinator.Ticket(new IdempotencyRequest(UUID.randomUUID(),"write","POST","/api/v1/a",UUID.randomUUID(),secret.getBytes()),UUID.randomUUID())};
        for(Object value:values)assertThat(value.toString()).contains("redacted").doesNotContain(secret);
    }

    @Test void identityReadsOutsideTransactionFailBeforeAnyDatabaseAccess() {
        var source=new AbstractDataSource(){
            public Connection getConnection(){throw new AssertionError("must not connect outside transaction");}
            public Connection getConnection(String a,String b){throw new AssertionError("must not connect");}
        };
        var reader=new CurrentIdentityReader(new IdentityRepository(source),Clock.systemUTC());
        var token=Jwt.withTokenValue("fixture").header("alg","RS256").subject("account").build();
        assertThatThrownBy(()->reader.admin(token,true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("transaction");
        assertThatThrownBy(()->reader.service(token,IdentityTokens.Family.OA)).isInstanceOf(IllegalStateException.class).hasMessageContaining("transaction");
    }

    @Test void largeScopePageIsClassifiedWithoutOverflowAndNormalPageStillBuildsJpaWindow() {
        var small=ScopeInput.page("214748365","10");assertThat(small.offset()).isEqualTo(2147483640L);assertThat(small.beyondJpaOffset()).isFalse();
        var large=ScopeInput.page("214748366","10");assertThat(large.offset()).isEqualTo(2147483650L);assertThat(large.beyondJpaOffset()).isTrue();
        assertThat(ScopeInput.page("2","20").pageable().getOffset()).isEqualTo(20L);
    }

    @Test void unsafeBusinessRejectionCannotBeSavedAsIdempotentPublicError() {
        assertThatThrownBy(()->new IdempotencyCoordinator.SafeFailure("DATABASE_PASSWORD_EXPOSED",null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
