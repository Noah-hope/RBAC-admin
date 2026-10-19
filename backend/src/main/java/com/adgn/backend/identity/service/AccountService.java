package com.adgn.backend.identity.service;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.repository.*;
import com.adgn.backend.identity.security.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

public final class AccountService {
    private final ReadBoundary reads;
    private final CurrentIdentityReader current;
    private final AccountRepository repository;
    private final IdentityRepository sessions;
    private final PasswordPolicy passwords;
    private final ObjectProvider<IdempotencyCoordinator> idempotency;
    private final AuditAppender audit;
    private final JsonMapper json;
    public AccountService(ReadBoundary reads,CurrentIdentityReader current,AccountRepository repository,IdentityRepository sessions,PasswordPolicy passwords,
                          ObjectProvider<IdempotencyCoordinator> idempotency,AuditAppender audit,JsonMapper json){
        this.reads=reads;this.current=current;this.repository=repository;this.sessions=sessions;this.passwords=passwords;this.idempotency=idempotency;this.audit=audit;this.json=json;
    }
    public AccountRepository.View get(Jwt token,String id)throws Exception{
        identifier(id);
        return reads.read(()->qualify(token,true),ignored->{var value=repository.view(id);if(value==null)throw new IdentityFailure(404,"NOT_FOUND");return value;});
    }
    public AccountRepository.Page list(Jwt token,String pageValue,String sizeValue,String employee,String prefix,String enabledValue)throws Exception{
        long page=number(pageValue,1);long size=number(sizeValue,20);
        if(!Set.of(10L,20L,50L,100L).contains(size))throw IdentityFailure.invalid();
        try{Math.multiplyExact(page-1,size);}catch(ArithmeticException invalid){throw IdentityFailure.invalid();}
        if(employee!=null)identifier(employee);if(prefix!=null)PasswordPolicy.length(prefix,1,100);
        if(enabledValue!=null && !Set.of("true","false").contains(enabledValue))throw IdentityFailure.invalid();
        Boolean enabled=enabledValue==null?null:Boolean.valueOf(enabledValue);
        return reads.read(()->qualify(token,false),ignored->repository.list(page,(int)size,employee,prefix,enabled));
    }
    public IdempotencyCoordinator.Outcome state(Jwt token,String key,String id,Long expected,Boolean enabled,String reason,String requestId)throws Exception{
        identifier(id);version(expected);reason(reason);if(enabled==null)throw IdentityFailure.invalid();
        return mutate(token,key,"setAccountState","PUT","/api/v1/admin/accounts/"+id+"/state",List.of(id,expected,enabled,reason),null,(context,hash)->{
            var old=target(id,expected,"setAccountState");
            if(old.view().enabled()==enabled)return completion(200,old.view(),"setAccountState");
            counters(old,"setAccountState",!enabled,false);
            var result=repository.state(context,old,enabled);
            if(!enabled)sessions.revokeAll(context,id);
            append(context,enabled?"ADMIN_ACCOUNT_ENABLED":"ADMIN_ACCOUNT_DISABLED",result,reason,requestId);
            return completion(200,result,"setAccountState");
        });
    }
    public IdempotencyCoordinator.Outcome reset(Jwt token,String key,String id,Long expected,String password,String confirm,String reason,String requestId)throws Exception{
        identifier(id);version(expected);password(password,confirm);reason(reason);
        return mutate(token,key,"resetAccountPassword","PUT","/api/v1/admin/accounts/"+id+"/password",List.of(id,expected,password,confirm,reason),password,(context,hash)->{
            var old=target(id,expected,"resetAccountPassword");counters(old,"resetAccountPassword",true,true);
            var result=repository.reset(context,old,hash);sessions.revokeAll(context,id);
            append(context,"ADMIN_ACCOUNT_PASSWORD_RESET",result,reason,requestId);
            return completion(200,result,"resetAccountPassword");
        });
    }
    private AccountRepository.Managed target(String id,long expected,String operation){
        var value=repository.managed(id);if(value==null)throw new IdentityFailure(404,"NOT_FOUND");
        if(value.superAdmin())throw new IdentityFailure(403,"FORBIDDEN");
        if(value.view().version()!=expected)throw rejection("VERSION_CONFLICT",operation);return value;
    }
    private static void counters(AccountRepository.Managed value,String operation,boolean epoch,boolean credentials){
        if(value.view().version()>=9007199254740991L || value.rowVersion()>=9007199254740991L
                || (epoch && value.epoch()>=9007199254740991L) || (credentials && value.credentials()>=9007199254740991L))throw rejection("STATE_CONFLICT",operation);
    }
    private IdempotencyCoordinator.Completion completion(int status,AccountRepository.View value,String operation){return new IdempotencyCoordinator.Completion(new SafeResult(status,json.writeValueAsString(value),null),basis(operation));}
    private static long number(String value,long fallback){
        if(value==null)return fallback;
        try{if(!value.matches("[0-9]+"))throw new IllegalArgumentException();long n=Long.parseLong(value);if(n<1)throw new IllegalArgumentException();return n;}
        catch(RuntimeException invalid){throw IdentityFailure.invalid();}
    }
    private static void version(Long value){if(value==null || value<0 || value>9007199254740991L)throw IdentityFailure.invalid();}
    public IdempotencyCoordinator.Outcome create(Jwt token,String key,String employee,String login,String password,String confirm,String reason,String requestId) throws Exception {
        identifier(employee);PasswordPolicy.length(login,1,100);
        if(!login.matches("[A-Za-z0-9][A-Za-z0-9_.@-]*"))throw IdentityFailure.invalid();
        password(password,confirm);reason(reason);
        return mutate(token,key,"createAccount","POST","/api/v1/admin/accounts",List.of(employee,login,password,confirm,reason),password,(context,hash)->{
            if(!repository.activeEmployee(employee))throw rejection("VALIDATION_ERROR","createAccount");
            if(repository.duplicate(login,employee))throw rejection("STATE_CONFLICT","createAccount");
            var result=repository.create(context,employee,login,hash);
            append(context,"ADMIN_ACCOUNT_CREATED",result,reason,requestId);
            return new IdempotencyCoordinator.Completion(new SafeResult(201,json.writeValueAsString(result),"/api/v1/admin/accounts/"+result.id()),basis("createAccount"));
        });
    }
    @FunctionalInterface private interface Work { IdempotencyCoordinator.Completion apply(WriteContext<IdentityRepository.Account> context,String hash)throws Exception; }
    private IdempotencyCoordinator.Outcome mutate(Jwt token,String key,String operation,String method,String path,List<?> fields,String password,Work work)throws Exception{
        UUID parsed;
        try { parsed=UUID.fromString(key);if(!parsed.toString().equalsIgnoreCase(key))throw new IllegalArgumentException(); }
        catch(RuntimeException invalid){throw IdentityFailure.invalid();}
        reads.read(()->qualify(token,false),ignored->null);
        var coordinator=idempotency.getIfAvailable();
        if(coordinator==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        // Fixed typed field order includes all semantic input, including password confirmation.
        // This is an internal request codec, not RFC8785. Only its HMAC is persisted.
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),operation,method,path,parsed,json.writeValueAsBytes(fields));
        var reserved=coordinator.reserve(request,()->identity(qualify(token,false)),AccountService::authorize);
        if(reserved instanceof IdempotencyCoordinator.Outcome outcome)return outcome;
        String hash=password==null?null:passwords.encode(password); // outside every guard transaction
        return coordinator.complete((IdempotencyCoordinator.Ticket)reserved,()->identity(qualify(token,false)),AccountService::authorize,context->work.apply(context,hash));
    }
    private IdentityRepository.Account qualify(Jwt token,boolean detail){
        var account=current.admin(token,false).account();
        if(!account.superAdmin())throw new IdentityFailure(detail?404:403,detail?"NOT_FOUND":"FORBIDDEN");
        return account;
    }
    private static CommandBoundary.Identity<IdentityRepository.Account> identity(IdentityRepository.Account a){return new CommandBoundary.Identity<>(a,new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,a.id()));}
    private static void authorize(IdentityRepository.Account a,AuthorizationBasis b){
        if(!a.superAdmin() || b.policy()!=AuthorizationBasis.Policy.GLOBAL_ONLY || !Set.of("createAccount","setAccountState","resetAccountPassword").contains(b.operationId()))throw new IdentityFailure(403,"FORBIDDEN");
    }
    private static AuthorizationBasis basis(String operation){return new AuthorizationBasis(operation,AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());}
    private static IdempotencyCoordinator.SafeFailure rejection(String code,String operation){return new IdempotencyCoordinator.SafeFailure(code,basis(operation));}
    private static void identifier(String value){PasswordPolicy.length(value,1,64);if(!value.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))throw IdentityFailure.invalid();}
    private static void reason(String value){PasswordPolicy.length(value,1,500);if(value.isBlank())throw IdentityFailure.invalid();}
    private static void password(String value,String confirm){PasswordPolicy.length(value,12,128);PasswordPolicy.length(confirm,12,128);if(!value.equals(confirm))throw IdentityFailure.invalid();}
    private void append(WriteContext<?> context,String type,AccountRepository.View target,String reason,String requestId){
        audit.append(context,new AuditRecord(type,target.id(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",requestId,reason,
                json.writeValueAsString(Map.of("schemaVersion",1,"accountId",target.id(),"employeeId",target.employeeId(),"enabled",target.enabled(),"version",target.version())),
                List.of(new AuditRecord.Subject("ACCOUNT",target.id()),new AuditRecord.Subject("EMPLOYEE",target.employeeId()))));
    }
}
