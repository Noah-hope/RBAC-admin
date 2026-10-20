package com.adgn.backend.grant;

import com.adgn.backend.core.transaction.WriteContext;
import javax.sql.DataSource;
import java.util.*;
import static com.adgn.backend.grant.GrantMutationSupport.*;

/** Lower-level lifecycle operations. Caller authorizes selections and invalidates requests. */
public final class SourceCloser {
    public record Selection(GrantReader.SourceKey key,long expectedEpoch,long expectedVersion) { }
    public record RelationSelection(GrantReader.Key key,long expectedVersion) { }
    private record Relation(GrantReader.Baseline baseline,GrantReader.Grant active) { }
    private final GrantMutationSupport db;
    private final BaselineWriter baselines;
    public SourceCloser(DataSource source,GrantReader reader,BaselineWriter baselines){db=new GrantMutationSupport(source,reader);this.baselines=Objects.requireNonNull(baselines);}
    public AffectedFacts close(WriteContext<?> c,List<Selection> selections,String reason){return db.checked(c,()->{
        String endedReason=reason(reason);require(selections!=null && !selections.isEmpty(),"VALIDATION_ERROR");
        var sources=new ArrayList<GrantReader.Source>();var relations=new ArrayList<Relation>();var unique=new HashSet<GrantReader.SourceKey>();
        for(var selection:selections){
            require(selection!=null,"VALIDATION_ERROR");key(selection.key());version(selection.expectedEpoch());version(selection.expectedVersion());require(unique.add(selection.key()),"VALIDATION_ERROR");
            var source=baselines.source(c,selection.key());require(source.sourceEpoch()==selection.expectedEpoch() && source.version()==selection.expectedVersion(),"SOURCE_VERSION_CHANGED");
            next(source.sourceEpoch());next(source.version());next(db.jdbc.queryForObject("SELECT row_version FROM authorization_source WHERE id=?",Long.class,source.id()));sources.add(source);
            var keys=db.jdbc.query("SELECT b.role_id FROM grant_baseline b JOIN role_grant g ON g.baseline_id=b.id AND g.status='ACTIVE' WHERE b.source_id=? ORDER BY b.id",(r,n)->new GrantReader.Key(source.key().employeeId(),r.getString("role_id"),source.key().companyId(),source.key().departmentId()),source.id());
            for(var key:keys){var b=db.reader.baseline(key);db.canAdvance(b);relations.add(new Relation(b,db.active(b)));}
        }
        var sourceFacts=new ArrayList<AffectedFacts.SourceChange>();var relationFacts=new ArrayList<AffectedFacts.RelationChange>();
        for(var source:sources){
            int changed=db.jdbc.update("UPDATE authorization_source SET source_epoch=source_epoch+1,source_version=source_version+1,row_version=row_version+1 WHERE id=? AND source_epoch=? AND source_version=? AND row_version<?",source.id(),source.sourceEpoch(),source.version(),MAX);
            require(changed==1,"SOURCE_VERSION_CHANGED");sourceFacts.add(new AffectedFacts.SourceChange(source,db.reader.source(source.key())));
        }
        for(var relation:relations)relationFacts.add(db.end(c,relation.baseline(),relation.active(),"INVALIDATED",null,endedReason));
        return new AffectedFacts(sourceFacts,relationFacts);
    });}
    public AffectedFacts invalidateRelations(WriteContext<?> c,List<RelationSelection> selections,String reason){return db.checked(c,()->{
        String endedReason=reason(reason);require(selections!=null && !selections.isEmpty(),"VALIDATION_ERROR");var unique=new HashSet<GrantReader.Key>();var plans=new ArrayList<Relation>();
        for(var selection:selections){
            require(selection!=null,"VALIDATION_ERROR");key(selection.key());version(selection.expectedVersion());require(unique.add(selection.key()),"VALIDATION_ERROR");
            var b=baselines.baseline(c,selection.key());require(b.relationVersion()==selection.expectedVersion(),"RELATION_VERSION_CHANGED");db.canAdvance(b);plans.add(new Relation(b,db.active(b)));
        }
        var facts=new ArrayList<AffectedFacts.RelationChange>();for(var plan:plans)facts.add(db.end(c,plan.baseline(),plan.active(),"INVALIDATED",null,endedReason));return new AffectedFacts(List.of(),facts);
    });}
}
