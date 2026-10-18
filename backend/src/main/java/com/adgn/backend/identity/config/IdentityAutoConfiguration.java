package com.adgn.backend.identity.config;

import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.repository.AccountRepository;
import com.adgn.backend.core.idempotency.IdempotencyCoordinator;
import tools.jackson.databind.json.JsonMapper;
import com.adgn.backend.identity.service.*;
import com.adgn.backend.identity.security.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.ObjectProvider;
import javax.sql.DataSource;
import java.time.Clock;

@AutoConfiguration(after=TransactionBoundaryAutoConfiguration.class)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityAutoConfiguration {
    @Bean PasswordPolicy passwordPolicy(){return new PasswordPolicy();}
    @Bean IdentityTokens identityTokens(IdentityProperties p,ObjectProvider<Clock> clocks){return new IdentityTokens(p,clocks.getIfAvailable(Clock::systemUTC));}
    @Bean LoginRateLimiter loginRateLimiter(IdentityProperties p,ObjectProvider<Clock> clocks){return new LoginRateLimiter(clocks.getIfAvailable(Clock::systemUTC),p.getLoginIpLimit(),p.getLoginAccountLimit());}
    @Bean IdentityRepository identityRepository(DataSource source){return new IdentityRepository(source);}
    @Bean AccountRepository accountRepository(DataSource source){return new AccountRepository(source);}
    @Bean AccountService accountService(ReadBoundary reads,CurrentIdentityReader current,AccountRepository repository,
            IdentityRepository sessions,PasswordPolicy passwords,ObjectProvider<IdempotencyCoordinator> idempotency,AuditAppender audit,JsonMapper json){
        return new AccountService(reads,current,repository,sessions,passwords,idempotency,audit,json);
    }
    @Bean CurrentIdentityReader currentIdentityReader(IdentityRepository r,ObjectProvider<Clock> clocks){return new CurrentIdentityReader(r,clocks.getIfAvailable(Clock::systemUTC));}
    @Bean IdentityService identityService(CommandBoundary commands,ReadBoundary reads,IdentityRepository repository,
            CurrentIdentityReader current,PasswordPolicy passwords,IdentityTokens tokens,LoginRateLimiter limiter,AuditAppender audit){
        return new IdentityService(commands,reads,repository,current,passwords,tokens,limiter,audit);
    }
}
