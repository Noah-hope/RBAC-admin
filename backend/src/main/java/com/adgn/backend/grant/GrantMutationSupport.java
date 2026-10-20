package com.adgn.backend.grant;

import com.adgn.backend.core.transaction.*;
import com.adgn.backend.query.management.ManagementInput;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Supplier;

/** Shared guarded DML; no authorization policy, transaction creation or upward domain calls. */
final class GrantMutationSupport {
    static final long MAX=ManagementInput.MAX_VERSION;
    final DataSource source;
    final JdbcTemplate jdbc;
    final GrantReader reader;
    GrantMutationSupport(DataSource source,GrantReader reader){this.source=Objects.requireNonNull(source);this.jdbc=new JdbcTemplate(source);this.reader=Objects.requireNonNull(reader);}
    <T> T checked(WriteContext<?> c,Supplier<T> work){
        Objects.requireNonNull(c,"A guarded write context is required").requireActive(source);
        try{return work.get();}catch(RuntimeException failure){c.markFailed(failure);throw failure;}
    }
    static GrantMutationFailure reject(String code){return new GrantMutationFailure(code);}
    static void require(boolean condition,String code){if(!condition)throw reject(code);}
    static void id(String value){ManagementInput.key(value);}
    static void key(GrantReader.Key key){require(key!=null,"VALIDATION_ERROR");id(key.employeeId());id(key.roleId());id(key.sourceCompanyId());id(key.sourceDepartmentId());}
    static void key(GrantReader.SourceKey key){require(key!=null,"VALIDATION_ERROR");id(key.employeeId());id(key.companyId());id(key.departmentId());id(key.systemId());}
    static void version(long value){ManagementInput.version(value);}
    static long next(long value){require(value>=0 && value<MAX,"VERSION_EXHAUSTED");return value+1;}
    static String reason(String reason){return ManagementInput.text(reason,1,500);}
    void canAdvance(GrantReader.Baseline b){next(b.relationVersion());next(jdbc.queryForObject("SELECT row_version FROM grant_baseline WHERE id=?",Long.class,b.id()));}
    void advance(GrantReader.Baseline b){
        int changed=jdbc.update("UPDATE grant_baseline SET relation_version=relation_version+1,row_version=row_version+1 WHERE id=? AND relation_version=? AND row_version<?",b.id(),b.relationVersion(),MAX);
        require(changed==1,"RELATION_VERSION_CHANGED");
    }
    GrantReader.Grant active(GrantReader.Baseline b){
        if(b.activeGrantId()==null)return null;
        var grant=reader.grant(b.activeGrantId()).orElseThrow(()->reject("RELATION_VERSION_CHANGED"));
        require(grant.status().equals("ACTIVE") && grant.relationVersion()==b.relationVersion() && grant.sourceEpoch()==b.source().sourceEpoch(),"RELATION_VERSION_CHANGED");return grant;
    }
    AffectedFacts.RelationChange fact(GrantReader.Baseline b,String grant,String status){return new AffectedFacts.RelationChange(b.key(),b.source().id(),b.id(),b.relationVersion(),b.relationVersion()+1,grant,status);}
    AffectedFacts.RelationChange end(WriteContext<?> c,GrantReader.Baseline b,GrantReader.Grant grant,String status,String request,String reason){
        advance(b);
        if(grant!=null){
            int changed=jdbc.update("UPDATE role_grant SET status=?,relation_version=?,ended_by_request_id=?,ended_at=?,ended_reason=? WHERE id=? AND baseline_id=? AND status='ACTIVE' AND relation_version=?",ps->{
                ps.setString(1,status);ps.setLong(2,b.relationVersion()+1);ps.setString(3,request);ps.setTimestamp(4,Timestamp.from(c.now()),Calendar.getInstance(TimeZone.getTimeZone("UTC")));ps.setString(5,reason);ps.setString(6,grant.id());ps.setString(7,b.id());ps.setLong(8,b.relationVersion());
            });require(changed==1,"RELATION_VERSION_CHANGED");
        }
        return fact(b,grant==null?null:grant.id(),grant==null?null:status);
    }
}
