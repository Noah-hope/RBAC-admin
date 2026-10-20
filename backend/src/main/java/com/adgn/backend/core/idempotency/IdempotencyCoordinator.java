package com.adgn.backend.core.idempotency;
import com.adgn.backend.core.transaction.*;
import javax.sql.DataSource;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.Duration;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.adgn.backend.core.audit.AuditRecord;

/** Two outer guarded transactions; never a servlet filter that captures arbitrary responses. */
public final class IdempotencyCoordinator {
    public sealed interface Reservation permits Ticket, Outcome { }
    public record Ticket(IdempotencyRequest request,UUID token) implements Reservation {
        @Override public String toString() { return "IdempotencyTicket[redacted]"; }
    }
    public record Outcome(SafeResult result,boolean replayed) implements Reservation { }
    public record Completion(SafeResult result,AuthorizationBasis basis) { }
    @FunctionalInterface public interface Authorize<A> { void check(A current,AuthorizationBasis basis) throws Exception; }
    public static final class SafeFailure extends RuntimeException {
        private final String code; private final AuthorizationBasis basis;
        public SafeFailure(String code,AuthorizationBasis basis) {
            super("Safe idempotent business rejection");
            if (!List.of("VERSION_CONFLICT","CONTEXT_CHANGED","STATE_CONFLICT","VALIDATION_ERROR","PREVIEW_STALE").contains(code))
                throw new IllegalArgumentException("Business error is not replayable");
            this.code=code; this.basis=basis;
        }
    }
    private final CommandBoundary commands;
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final HmacKeyRing keys;
    private final JsonMapper json;
    public IdempotencyCoordinator(CommandBoundary commands,DataSource source,HmacKeyRing keys,JsonMapper json) {
        this.commands=commands; this.source=source; this.jdbc=new JdbcTemplate(source); this.keys=keys; this.json=json;
    }
    public <A> Reservation reserve(IdempotencyRequest request,CommandBoundary.CheckedSupplier<CommandBoundary.Identity<A>> qualify,Authorize<A> authorize) throws Exception {
        return commands.execute(()->qualified(request,qualify),context->{
            context.requireActive(source);
            var row=find(request); Instant now=now();
            if (row==null) {
                UUID token=UUID.randomUUID();
                jdbc.update("INSERT INTO http_idempotency(id,principal_id,http_method,canonical_path,idempotency_key,request_digest,digest_key_id,state,lease_token,lease_until,expires_at,created_at) "
                                +"VALUES(?,?,?,?,?,?,?,'IN_PROGRESS',?,UTC_TIMESTAMP(6)+INTERVAL 60 SECOND,UTC_TIMESTAMP(6)+INTERVAL 7 DAY,UTC_TIMESTAMP(6))",
                        UUID.randomUUID().toString(),request.principalId().toString(),request.method(),request.path(),request.key().toString(),keys.digest(keys.currentId(),request),keys.currentId(),token.toString());
                return new Ticket(request,token);
            }
            if (row.state.equals("COMPLETED")) {
                AuthorizationBasis basis=readBasis(row.basis);
                checkBasis(request,basis,row.status);
                authorize.check(context.authority(),basis);
                match(request,row);
                return new Outcome(readResult(row),true);
            }
            match(request,row);
            if (row.lease==null || row.token==null) throw integrity();
            if (row.lease.isAfter(now)) throw busy(Math.max(1,(Duration.between(now,row.lease).toMillis()+999)/1000));
            UUID token=UUID.randomUUID();
            if (jdbc.update("UPDATE http_idempotency SET lease_token=?,lease_until=UTC_TIMESTAMP(6)+INTERVAL 60 SECOND WHERE id=? AND state='IN_PROGRESS' AND lease_token=?",
                    token.toString(),row.id,row.token)!=1) throw integrity();
            return new Ticket(request,token);
        });
    }
    public <A> Outcome complete(Ticket ticket,CommandBoundary.CheckedSupplier<CommandBoundary.Identity<A>> qualify,Authorize<A> authorize,CommandBoundary.CheckedFunction<WriteContext<A>,Completion> work) throws Exception {
        try {
            return commands.execute(()->qualified(ticket.request,qualify),context->{
                context.requireActive(source);
                Row row=owned(ticket);
                Completion completion;
                try { completion=work.apply(context); }
                catch(SafeFailure failure) { throw new BusinessRejection(failure); }
                save(ticket,row,completion,context.authority(),authorize,false);
                return new Outcome(completion.result,false);
            });
        } catch (BusinessRejection rejection) {
            SafeFailure failure=rejection.failure;
            // The first TransactionTemplate has returned exceptionally and rolled back before this boundary begins.
            return commands.execute(()->qualified(ticket.request,qualify),context->{
                context.requireActive(source);
                Row row=owned(ticket);
                var status=failure.code.equals("VALIDATION_ERROR") ? 422:409;
                var body=json.createObjectNode().put("type","urn:rbac-admin:problem:"+failure.code.toLowerCase(java.util.Locale.ROOT).replace('_','-'))
                        .put("title","Request rejected").put("status",status).put("code",failure.code)
                        .put("detail","请求未通过校验，请检查输入及当前操作条件。").put("requestId","");
                body.putArray("errors");
                var completion=new Completion(new SafeResult(status,json.writeValueAsString(body),null),failure.basis);
                save(ticket,row,completion,context.authority(),authorize,true);
                return new Outcome(completion.result,false);
            });
        }
    }
    public <A> Outcome execute(IdempotencyRequest request,CommandBoundary.CheckedSupplier<CommandBoundary.Identity<A>> qualify,Authorize<A> authorize,CommandBoundary.CheckedFunction<WriteContext<A>,Completion> work) throws Exception {
        var reservation=reserve(request,qualify,authorize);
        return reservation instanceof Outcome result ? result : complete((Ticket)reservation,qualify,authorize,work);
    }
    private <A> CommandBoundary.Identity<A> qualified(IdempotencyRequest request,CommandBoundary.CheckedSupplier<CommandBoundary.Identity<A>> qualify) throws Exception {
        var identity=qualify.get();
        if (identity==null || identity.actor()==null || identity.actor().kind()!=AuditRecord.ActorKind.ACCOUNT
                || !request.principalId().toString().equals(identity.actor().id()))
            throw new IdempotencyFailure(IdempotencyFailure.Code.AUTHENTICATION_FAILED,0);
        return identity;
    }
    private Row owned(Ticket ticket) {
        var row=find(ticket.request);
        if (row==null || !row.state.equals("IN_PROGRESS") || ticket.token==null || !ticket.token.toString().equals(row.token)
                || row.lease==null || !row.lease.isAfter(now())) throw busy(1);
        match(ticket.request,row);return row;
    }
    private <A> void save(Ticket ticket,Row row,Completion completion,A current,Authorize<A> authorize,boolean safeFailure) throws Exception {
        if (completion==null || completion.result==null) throw integrity();
        if (!safeFailure && completion.result.status()>=300) throw integrity();
        checkBasis(ticket.request,completion.basis,completion.result.status());
        authorize.check(current,completion.basis);
        var envelope=json.createObjectNode().put("schemaVersion",1);
        if (completion.result.body()==null) envelope.putNull("body"); else envelope.set("body",json.readTree(completion.result.body()));
        if (jdbc.update("UPDATE http_idempotency SET state='COMPLETED',http_status=?,resource_location=?,safe_response=?,authorization_basis=?,lease_token=NULL,lease_until=NULL,"
                        +"expires_at=GREATEST(expires_at,UTC_TIMESTAMP(6)+INTERVAL 7 DAY) WHERE id=? AND state='IN_PROGRESS' AND lease_token=?",
                completion.result.status(),completion.result.location(),json.writeValueAsString(envelope),json.writeValueAsString(completion.basis),row.id,ticket.token.toString())!=1) throw busy(1);
    }
    private void checkBasis(IdempotencyRequest request,AuthorizationBasis basis,int status) {
        if (basis==null || !request.operationId().equals(basis.operationId()) || basis.policy()==null || basis.targets()==null
                || (basis.policy()==AuthorizationBasis.Policy.SCOPED && basis.targets().isEmpty())
                || (basis.policy()==AuthorizationBasis.Policy.PRINCIPAL_ONLY && (status<400 || !basis.targets().isEmpty()))
                || basis.targets().stream().anyMatch(t->t==null || t.companyId()==null || t.departmentId()==null || t.systemId()==null)) throw integrity();
    }
    private void match(IdempotencyRequest request,Row row) {
        if (!MessageDigest.isEqual(row.digest,keys.digest(row.keyId,request))) throw new IdempotencyFailure(IdempotencyFailure.Code.IDEMPOTENCY_CONFLICT,0);
    }
    private AuthorizationBasis readBasis(String value) {
        try { return json.readValue(value,AuthorizationBasis.class); } catch(RuntimeException bad){throw integrity();}
    }
    private SafeResult readResult(Row row) {
        try {
            var envelope=json.readTree(row.response);
            if(!envelope.isObject() || envelope.path("schemaVersion").intValue()!=1 || !envelope.has("body"))throw integrity();
            return new SafeResult(row.status,row.status==204 ? null:json.writeValueAsString(envelope.get("body")),row.location);
        } catch(RuntimeException bad){throw integrity();}
    }
    private Row find(IdempotencyRequest request) {
        var rows=jdbc.query("SELECT * FROM http_idempotency WHERE principal_id=? AND http_method=? AND canonical_path=? AND idempotency_key=?",
                (rs,n)->new Row(rs.getString("id"),rs.getString("state"),rs.getBytes("request_digest"),rs.getString("digest_key_id"),
                        rs.getString("lease_token"),instant(rs,"lease_until"),rs.getInt("http_status"),rs.getString("resource_location"),rs.getString("safe_response"),rs.getString("authorization_basis")),
                request.principalId().toString(),request.method(),request.path(),request.key().toString());
        if(rows.size()>1)throw integrity();return rows.isEmpty()?null:rows.getFirst();
    }
    private Instant now(){return jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",(rs,n)->instant(rs,1));}
    private static Instant instant(ResultSet rs,String name)throws SQLException {var t=rs.getTimestamp(name,Calendar.getInstance(TimeZone.getTimeZone("UTC")));return t==null?null:t.toInstant();}
    private static Instant instant(ResultSet rs,int n)throws SQLException {return rs.getTimestamp(n,Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant();}
    private static PersistenceFailure integrity(){return new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);}
    private static IdempotencyFailure busy(long seconds){return new IdempotencyFailure(IdempotencyFailure.Code.REQUEST_IN_PROGRESS,seconds);}
    private static final class BusinessRejection extends RuntimeException {
        private final SafeFailure failure;
        private BusinessRejection(SafeFailure failure) { super("Business transaction rejected");this.failure=failure; }
    }
    private record Row(String id,String state,byte[] digest,String keyId,String token,Instant lease,int status,String location,String response,String basis) { }
}
