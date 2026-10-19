package com.adgn.backend.identity.service;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.security.*;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;
import java.util.*;

public final class IdentityService {
    public record SessionView(String sessionId,String accountId,String employeeId,String identity,String mode,Instant expiresAt) { }
    public record LoginResult(SessionView session,String accessToken) {
        @Override public String toString(){return "LoginResult[redacted]";}
    }
    private final CommandBoundary commands;
    private final ReadBoundary reads;
    private final IdentityRepository repository;
    private final CurrentIdentityReader current;
    private final PasswordPolicy passwords;
    private final IdentityTokens tokens;
    private final LoginRateLimiter limiter;
    private final AuditAppender audit;
    public IdentityService(CommandBoundary commands,ReadBoundary reads,IdentityRepository repository,
            CurrentIdentityReader current,PasswordPolicy passwords,IdentityTokens tokens,LoginRateLimiter limiter,AuditAppender audit) {
        this.commands=commands;this.reads=reads;this.repository=repository;this.current=current;
        this.passwords=passwords;this.tokens=tokens;this.limiter=limiter;this.audit=audit;
    }
    public LoginResult login(String login,String password,String address,String requestId) throws Exception {
        PasswordPolicy.length(login,1,100); PasswordPolicy.length(password,1,128); limiter.admit(address,login);
        var before=reads.read(()->repository.byLogin(login),account->account);
        if(!passwords.matches(password,before==null ? null : before.hash()) || before==null || !before.qualified())throw IdentityFailure.authentication();
        return commands.execute(()->{
            var fresh=repository.byId(before.id());
            if(fresh==null || !fresh.qualified() || fresh.credentials()!=before.credentials() || fresh.epoch()!=before.epoch()
                    || !fresh.hash().equals(before.hash()))throw IdentityFailure.authentication();
            return identity(fresh);
        },context->{
            var account=context.authority(); String sid=context.newId().toString(),jti=context.newId().toString();
            String mode=account.restricted() ? "PASSWORD_CHANGE_ONLY" : "NORMAL";
            // JWT NumericDate uses whole seconds; persist exactly the expiry that is signed.
            Instant expires=context.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(account.restricted()?300:900);
            String token=tokens.issue(account.id(),sid,jti,mode,account.credentials(),account.epoch(),expires);
            repository.createSession(context,account,sid,jti,mode,expires);
            append(context,"ADMIN_LOGIN",requestId);
            return new LoginResult(view(account,new IdentityRepository.Session(sid,account.id(),jti,mode,account.credentials(),account.epoch(),expires,false)),token);
        });
    }
    public SessionView current(Jwt token) throws Exception {
        return reads.read(()->current.admin(token,false),a->view(a.account(),a.session()));
    }
    public void check(Jwt token,IdentityTokens.Family family,boolean allowRestricted) throws Exception {
        reads.read(()-> family==IdentityTokens.Family.ADMIN ? current.admin(token,allowRestricted) : current.service(token,family),ignored->null);
    }
    public Jwt verify(String raw,IdentityTokens.Family family){return tokens.verify(raw,family);}
    public void logout(String raw,String requestId) throws Exception {
        Jwt token;
        try { token=tokens.verify(raw,IdentityTokens.Family.ADMIN); }
        catch(IdentityFailure invalid){return;}
        commands.execute(()->{
            var account=repository.byId(token.getSubject());
            return new CommandBoundary.Identity<>(account,new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,token.getSubject()));
        },context->{
            var session=repository.session(token.getClaimAsString("sid"));
            // A correctly signed token can revoke only its exact stored sid/jti/account binding.
            if(session!=null && session.accountId().equals(token.getSubject()) && session.jti().equals(token.getId())
                    && repository.revokeSession(context,session.id(),session.accountId())) append(context,"ADMIN_LOGOUT",requestId);
            return null;
        });
    }
    public void changePassword(Jwt token,String old,String next,String confirmation,String requestId) throws Exception {
        PasswordPolicy.length(old,1,128); PasswordPolicy.length(next,12,128); PasswordPolicy.length(confirmation,12,128);
        if(!next.equals(confirmation) || next.equals(old))throw IdentityFailure.invalid();
        var before=reads.read(()->current.admin(token,true),a->a.account());
        if(!passwords.matches(old,before.hash()))throw IdentityFailure.authentication();
        String encoded=passwords.encode(next);
        commands.execute(()->{
            var fresh=current.admin(token,true).account();
            if(!fresh.hash().equals(before.hash()) || fresh.credentials()!=before.credentials()
                    || fresh.credentials()>=9007199254740991L || fresh.epoch()>=9007199254740991L)throw IdentityFailure.revoked();
            return identity(fresh);
        },context->{repository.changePassword(context,context.authority(),encoded);append(context,"ADMIN_PASSWORD_CHANGED",requestId);return null;});
    }
    private static CommandBoundary.Identity<IdentityRepository.Account> identity(IdentityRepository.Account a){
        return new CommandBoundary.Identity<>(a,new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,a.id()));
    }
    private static SessionView view(IdentityRepository.Account a,IdentityRepository.Session s){
        return new SessionView(s.id(),a.id(),a.employeeId(),a.superAdmin()?"SUPER_ADMIN":"PERMISSION_ADMIN",s.mode(),s.expires());
    }
    private void append(WriteContext<?> context,String type,String requestId){
        audit.append(context,new AuditRecord(type,context.actor().id(),null,null,
                new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",requestId,"后台会话操作",
                "{\"schemaVersion\":1}",List.of(new AuditRecord.Subject("ACCOUNT",context.actor().id()))));
    }
}
