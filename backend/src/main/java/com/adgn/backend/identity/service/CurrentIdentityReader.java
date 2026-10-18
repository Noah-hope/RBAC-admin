package com.adgn.backend.identity.service;

import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.security.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;

/** Reusable qualification port: always call again inside your own read/write boundary. */
public final class CurrentIdentityReader {
    public record Admin(IdentityRepository.Account account,IdentityRepository.Session session) { }
    private final IdentityRepository repository;
    private final Clock clock;
    public CurrentIdentityReader(IdentityRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    public Admin admin(Jwt token,boolean allowRestricted) {
        requireTransaction();
        var account=repository.byId(token.getSubject());
        var session=repository.session(token.getClaimAsString("sid"));
        if(account==null || !account.qualified() || session==null || session.revoked()
                || !session.accountId().equals(account.id()) || !session.jti().equals(token.getId())
                || !session.expires().isAfter(clock.instant()) || !session.expires().equals(token.getExpiresAt())
                || account.credentials()!=session.credentials() || account.epoch()!=session.epoch()
                || ((Number)token.getClaim("credentialVersion")).longValue()!=account.credentials()
                || ((Number)token.getClaim("sessionEpoch")).longValue()!=account.epoch()
                || !session.mode().equals(token.getClaimAsString("mode"))
                || account.restricted()!=session.mode().equals("PASSWORD_CHANGE_ONLY"))throw IdentityFailure.revoked();
        if(!allowRestricted && account.restricted())throw new IdentityFailure(403,"FORBIDDEN");
        return new Admin(account,session);
    }
    public IdentityRepository.Service service(Jwt token,IdentityTokens.Family family) {
        requireTransaction();
        if(family==IdentityTokens.Family.ADMIN)throw new IllegalArgumentException("Service family required");
        var service=repository.service(token.getSubject(),family==IdentityTokens.Family.BUSINESS ? "BUSINESS" : "OA",
                token.getClaimAsString(family==IdentityTokens.Family.BUSINESS ? "systemId" : "oaInstanceId"));
        if(service==null)throw IdentityFailure.authentication();
        return service;
    }
    private static void requireTransaction(){
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Current identity must be read inside the caller transaction");
    }
}
