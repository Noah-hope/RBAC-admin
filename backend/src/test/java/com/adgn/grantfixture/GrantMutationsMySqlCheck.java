package com.adgn.grantfixture;

import com.adgn.backend.BackendApplication;
import com.adgn.backend.grant.*;
import com.adgn.backend.grant.GrantReader.*;
import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.people.OrganizationReader;
import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Synthetic reliable-approval fixtures exercise M06, not real OA or C's canonical validator. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrantMutationsMySqlCheck {
    @Test void missingActiveGrantReadRejectsClosureWithoutAdvancingSourceOrBaseline()throws Exception{
        var f=data();String grant=apply(approved("ROLE_GRANT",target(f))).relations().getFirst().grantId();
        var damaged=org.mockito.Mockito.spy(reader);org.mockito.Mockito.doReturn(Optional.empty()).when(damaged).grant(grant);
        var service=new SourceCloser(app.getBean(DataSource.class),damaged,baselines);
        assertThatThrownBy(()->write(c->service.close(c,List.of(new SourceCloser.Selection(f.sourceKey(),0,0)),"Fixture storage mismatch")))
                .isInstanceOfSatisfying(GrantMutationFailure.class,e->assertThat(e.code()).isEqualTo("RELATION_VERSION_CHANGED"));
        assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isZero();
        assertThat(scalar("SELECT relation_version FROM grant_baseline WHERE id=?",f.baseline())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,grant)).isEqualTo("ACTIVE");
    }
    private IsolatedMySqlDatabase db;private ConfigurableApplicationContext app;private JdbcTemplate jdbc;
    private CommandBoundary commands;private GrantReader reader;private BaselineWriter baselines;private GrantApplier applier;private SourceCloser closer;private String account;
    private final JsonMapper json=JsonMapper.builder().build();
    private String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        db=new IsolatedMySqlDatabase();app=db.start(BackendApplication.class);DataSource ds=app.getBean(DataSource.class);jdbc=new JdbcTemplate(ds);
        commands=app.getBean(CommandBoundary.class);reader=new GrantReader(ds,app.getBean(OrganizationReader.class));
        baselines=app.getBean(BaselineWriter.class);applier=app.getBean(GrantApplier.class);closer=app.getBean(SourceCloser.class);
        account=id();jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,'fixture-only',1,0,UTC_TIMESTAMP(6))",account,account);
    }
    @AfterAll void stop()throws Exception{try{if(app!=null)app.close();}finally{if(db!=null)db.close();}}
    private <T> T write(CommandBoundary.CheckedFunction<WriteContext<String>,T> work)throws Exception{
        return commands.execute(()->new CommandBoundary.Identity<>("INTERNAL_EXECUTOR_FIXTURE",new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account)),work);
    }
    private record Data(String employee,String company,String department,String otherDepartment,String system,String role,String employment,String source,String baseline){
        Key key(){return new Key(employee,role,company,department);}
        SourceKey sourceKey(){return new SourceKey(employee,company,department,system);}
    }
    private record Target(Key key,String system,String source,String baseline,long relation,long config,long epoch,String grant) { }
    private Data data(){return data(false);}
    private Data data(boolean departmentRole){
        String e=id(),c=id(),d=id(),d2=id(),s=id(),r=id(),src=id(),b=id();
        jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",c,c);
        for(String dep:List.of(d,d2))jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Fixture',UTC_TIMESTAMP(6))",dep,c,dep);
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",e,e);
        jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",s,s);
        jdbc.update("INSERT INTO business_role(id,code,name,company_id,department_id,system_id,state,configuration_version,created_at) VALUES(?,?,'Fixture',?,?,?,'EFFECTIVE',4,UTC_TIMESTAMP(6))",r,r,c,departmentRole?d2:null,s);
        String permission=id();jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,created_at) VALUES(?,?,?,'Fixture','SYSTEM_ACCESS',UTC_TIMESTAMP(6))",permission,s,permission);
        jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",r,permission,s);
        jdbc.update("INSERT INTO authorization_source(id,employee_id,company_id,department_id,system_id) VALUES(?,?,?,?,?)",src,e,c,d,s);
        jdbc.update("INSERT INTO grant_baseline(id,source_id,role_id,company_id,system_id) VALUES(?,?,?,?,?)",b,src,r,c,s);
        return new Data(e,c,d,d2,s,r,employment(e,c,d),src,b);
    }
    private String employment(String e,String c,String d){String p=id(),job=id();jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'Fixture',UTC_TIMESTAMP(6))",p,d,p);jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",job,e,c,d,p);return job;}
    private void endJob(String job){jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6) WHERE id=?",job);}
    private Target target(Data f){return new Target(f.key(),f.system(),f.source(),f.baseline(),0,4,0,null);}
    private Target current(Data f,String grant){return new Target(f.key(),f.system(),f.source(),f.baseline(),scalar("SELECT relation_version FROM grant_baseline WHERE id=?",f.baseline()),4,scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source()),grant);}
    private long scalar(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    private String approved(String kind,Target...targets){return approvedInScope(kind,null,null,targets);}
    private String approvedInScope(String kind,String scope,String appointment,Target...targets){
        String request=id();String snapshot=json.writeValueAsString(Map.of("schemaVersion",1,"items",Arrays.asList(targets)));
        jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,?,?,?, 'Fixture approval',UTC_TIMESTAMP(6))",request,kind,targets[0].key().sourceCompanyId(),snapshot);
        for(int n=0;n<targets.length;n++){Target t=targets[n];Key k=t.key();boolean grant=kind.equals("ROLE_GRANT");
            jdbc.update("INSERT INTO change_target(request_id,line_no,line_kind,company_id,system_id,department_id,employee_id,role_id,source_id,baseline_id,grant_id,expected_configuration_version,expected_source_epoch,expected_relation_version,target_snapshot) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",request,n,kind,k.sourceCompanyId(),t.system(),k.sourceDepartmentId(),k.employeeId(),k.roleId(),t.source(),t.baseline(),t.grant(),grant?t.config():null,grant?t.epoch():null,t.relation(),json.writeValueAsString(Map.of("schemaVersion",1,"employmentIds",List.of("fixed-old-evidence"))));
            jdbc.update("INSERT INTO relation_change_claim(baseline_id,request_id) VALUES(?,?)",t.baseline(),request);
        }
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=1,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type=?,scope_id=?,appointment_id=?,original_authority='{}',fixed_payload_bytes=?,content_digest=UNHEX(SHA2(?,256)) WHERE id=?",account,scope==null?"GLOBAL":"SCOPE",scope,appointment,snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8),snapshot,request);
        String oa=id(),event=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oa,oa);
        jdbc.update("INSERT INTO oa_link(request_id,oa_instance_id,oa_approval_id,accepted_at,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",request,oa,id());
        jdbc.update("INSERT INTO oa_callback_event(id,oa_instance_id,event_id,request_id,payload_digest,safe_payload,received_at) VALUES(?,?,?,?,UNHEX(REPEAT('00',32)),'{}',UTC_TIMESTAMP(6))",event,oa,id(),request);
        jdbc.update("INSERT INTO oa_final_result(request_id,first_event_id,decision,decided_at,comment,received_at) VALUES(?,?,'APPROVED',UTC_TIMESTAMP(6),'Fixture',UTC_TIMESTAMP(6))",request,event);
        jdbc.update("UPDATE change_request SET state='APPROVED',delivery_status='LINKED',approval_result='APPROVED',execution_status='PENDING' WHERE id=?",request);return request;
    }
    private AffectedFacts apply(String request)throws Exception{return write(c->{var facts=applier.apply(c,request);complete(c,request);return facts;});}
    private void complete(WriteContext<?> c,String request){
        UUID audit=app.getBean(AuditAppender.class).append(c,new AuditRecord("FIXTURE_EXECUTED",request,UUID.fromString(request),UUID.fromString(account),new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",id(),"Fixture","{\"schemaVersion\":1}",List.of()));
        jdbc.update("INSERT INTO execution_success(request_id,audit_event_id,executed_at,result_snapshot) VALUES(?,?,UTC_TIMESTAMP(6),'{}')",request,audit.toString());
        jdbc.update("UPDATE change_request SET state='EXECUTED',execution_status='SUCCEEDED',failure_code=NULL,next_retry_at=NULL WHERE id=?",request);
        jdbc.update("DELETE FROM relation_change_claim WHERE request_id=?",request);
    }
    private SourceCloser.Selection selection(Data f,long epoch,long version){return new SourceCloser.Selection(f.sourceKey(),epoch,version);}
    private void rejected(String request,String code){assertThatThrownBy(()->apply(request)).isInstanceOfSatisfying(GrantMutationFailure.class,e->assertThat(e.code()).isEqualTo(code));}
    @Test void zeroMaterializationKeepsExistingHistoryAndNeverAdvancesVersions()throws Exception{
        Data f=data();SourceKey newSource=new SourceKey(f.employee(),f.company(),f.otherDepartment(),f.system());Key newKey=new Key(f.employee(),f.role(),f.company(),f.otherDepartment());
        var first=write(c->baselines.baseline(c,newKey));assertThat(first.id()).isNotNull();assertThat(first.relationVersion()).isZero();assertThat(first.source().sourceEpoch()).isZero();assertThat(first.source().version()).isZero();
        write(c->closer.close(c,List.of(new SourceCloser.Selection(newSource,0,0)),"Zero close"));
        var again=write(c->baselines.baseline(c,newKey));assertThat(again.id()).isEqualTo(first.id());assertThat(again.source().id()).isEqualTo(first.source().id());assertThat(again.source().sourceEpoch()).isEqualTo(1);assertThat(again.source().version()).isEqualTo(1);assertThat(again.relationVersion()).isZero();
    }
    @Test void grantUsesAllCurrentJobsAndKeepsFixedSubmissionEvidenceUnchanged()throws Exception{
        Data f=data();String request=approved("ROLE_GRANT",target(f));String fixed=jdbc.queryForObject("SELECT target_snapshot FROM change_target WHERE request_id=?",String.class,request);
        endJob(f.employment());var jobs=new ArrayList<String>();for(int n=0;n<101;n++)jobs.add(employment(f.employee(),f.company(),f.department()));
        var facts=apply(request);assertThat(facts.relations()).hasSize(1);String grant=facts.relations().getFirst().grantId();
        assertThat(write(c->reader.grant(grant).orElseThrow()).employmentIds()).containsExactlyInAnyOrderElementsOf(jobs);
        assertThat(scalar("SELECT relation_version FROM grant_baseline WHERE id=?",f.baseline())).isEqualTo(1);assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isZero();assertThat(scalar("SELECT source_version FROM authorization_source WHERE id=?",f.source())).isZero();
        assertThat(jdbc.queryForObject("SELECT target_snapshot FROM change_target WHERE request_id=?",String.class,request)).isEqualTo(fixed);
        assertThat(scalar("SELECT COUNT(*) FROM execution_success WHERE request_id=?",request)).isEqualTo(1);rejected(request,"STATE_CONFLICT");
    }
    @Test void revokeIgnoresCurrentQualificationAndRegrantGetsNewIdAndPermanentVersion()throws Exception{
        Data f=data();String first=apply(approved("ROLE_GRANT",target(f))).relations().getFirst().grantId();String revoke=approved("ROLE_REVOCATION",current(f,first));
        endJob(f.employment());jdbc.update("UPDATE business_system SET enabled=0 WHERE id=?",f.system());jdbc.update("UPDATE employee SET status='INACTIVE' WHERE id=?",f.employee());jdbc.update("UPDATE business_role SET configuration_version=5 WHERE id=?",f.role());
        apply(revoke);assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,first)).isEqualTo("REVOKED");assertThat(scalar("SELECT relation_version FROM role_grant WHERE id=?",first)).isEqualTo(2);
        jdbc.update("UPDATE employee SET status='ACTIVE' WHERE id=?",f.employee());jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",f.system());jdbc.update("UPDATE business_role SET configuration_version=4 WHERE id=?",f.role());employment(f.employee(),f.company(),f.department());
        String second=apply(approved("ROLE_GRANT",current(f,null))).relations().getFirst().grantId();assertThat(second).isNotEqualTo(first);assertThat(scalar("SELECT relation_version FROM role_grant WHERE id=?",second)).isEqualTo(3);
        String stale=approved("ROLE_REVOCATION",current(f,first));rejected(stale,"RELATION_VERSION_CHANGED");assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,second)).isEqualTo("ACTIVE");
    }
    @Test void sameSourceCannotDuplicateButOtherSourceCoexistsAndClosePreservesIt()throws Exception{
        Data f=data();String first=apply(approved("ROLE_GRANT",target(f))).relations().getFirst().grantId();
        String duplicate=approved("ROLE_GRANT",current(f,null));rejected(duplicate,"ALREADY_GRANTED");jdbc.update("DELETE FROM relation_change_claim WHERE request_id=?",duplicate);
        employment(f.employee(),f.company(),f.otherDepartment());Key key=new Key(f.employee(),f.role(),f.company(),f.otherDepartment());var other=write(c->baselines.baseline(c,key));
        String second=apply(approved("ROLE_GRANT",new Target(key,f.system(),other.source().id(),other.id(),0,4,0,null))).relations().getFirst().grantId();
        var closed=write(c->closer.close(c,List.of(selection(f,0,0)),"Leave source"));assertThat(closed.sources()).hasSize(1);assertThat(closed.relations()).extracting(AffectedFacts.RelationChange::grantId).containsExactly(first);
        assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,first)).isEqualTo("INVALIDATED");assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,second)).isEqualTo("ACTIVE");assertThat(scalar("SELECT COUNT(*) FROM grant_employment_evidence WHERE grant_id=?",first)).isEqualTo(1);
    }
    @Test void sourceCloseWithoutGrantsPermanentlyBlocksOldApprovalEvenAfterReturn()throws Exception{
        Data f=data();String request=approved("ROLE_GRANT",target(f));var closed=write(c->closer.close(c,List.of(selection(f,0,0)),"Leave"));assertThat(closed.relations()).isEmpty();assertThat(closed.sources().getFirst().after().sourceEpoch()).isEqualTo(1);
        endJob(f.employment());employment(f.employee(),f.company(),f.department());rejected(request,"SOURCE_EPOCH_CHANGED");assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE baseline_id=?",f.baseline())).isZero();
        assertThatThrownBy(()->write(c->closer.close(c,List.of(selection(f,0,0)),"Old preview"))).isInstanceOf(GrantMutationFailure.class);
        var next=write(c->closer.close(c,List.of(selection(f,1,1)),"New event"));assertThat(next.sources().getFirst().after().sourceEpoch()).isEqualTo(2);
    }
    @Test void staleMemberRejectsWholeApprovedBatchBeforeAnyGrant()throws Exception{
        Data a=data();String r2=id(),b2=id();jdbc.update("INSERT INTO business_role(id,code,name,company_id,system_id,state,configuration_version,created_at) VALUES(?,?,'Fixture',?,?,'EFFECTIVE',4,UTC_TIMESTAMP(6))",r2,r2,a.company(),a.system());jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) SELECT ?,permission_id,system_id FROM role_permission WHERE role_id=?",r2,a.role());jdbc.update("INSERT INTO grant_baseline(id,source_id,role_id,company_id,system_id) VALUES(?,?,?,?,?)",b2,a.source(),r2,a.company(),a.system());
        Target second=new Target(new Key(a.employee(),r2,a.company(),a.department()),a.system(),a.source(),b2,0,4,0,null);String request=approved("ROLE_GRANT",target(a),second);jdbc.update("UPDATE business_role SET configuration_version=5 WHERE id=?",r2);
        rejected(request,"ROLE_CONFIGURATION_CHANGED");assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE baseline_id IN (?,?)",a.baseline(),b2)).isZero();assertThat(scalar("SELECT relation_version FROM grant_baseline WHERE id=?",a.baseline())).isZero();assertThat(scalar("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",request)).isEqualTo(2);
    }
    @Test void missingClaimAndUnapprovedStateCannotChangeAnything()throws Exception{
        Data f=data();String request=approved("ROLE_GRANT",target(f));jdbc.update("DELETE FROM relation_change_claim WHERE request_id=?",request);rejected(request,"STATE_CONFLICT");
        String draft=id();jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,'ROLE_GRANT',?,'{}','Draft',UTC_TIMESTAMP(6))",draft,f.company());rejected(draft,"STATE_CONFLICT");assertThat(scalar("SELECT relation_version FROM grant_baseline WHERE id=?",f.baseline())).isZero();
    }
    @Test void sourceAndRoleDepartmentQualificationsAreIndependent()throws Exception{
        Data f=data(true);String request=approved("ROLE_GRANT",target(f));rejected(request,"QUALIFICATION_LOST");
        String roleJob=employment(f.employee(),f.company(),f.otherDepartment());assertThat(write(c->reader.baseline(f.key())).eligibleForGrant()).isTrue();
        String grant=apply(request).relations().getFirst().grantId();assertThat(write(c->reader.grant(grant).orElseThrow()).employmentIds()).containsExactly(f.employment());
        String sibling=apply(approved("ROLE_GRANT",secondTarget(f))).relations().getFirst().grantId();
        endJob(roleJob);var facts=write(c->closer.invalidateRelations(c,List.of(new SourceCloser.RelationSelection(f.key(),1)),"Role department qualification lost"));
        assertThat(facts.sources()).isEmpty();assertThat(facts.relations().getFirst().afterVersion()).isEqualTo(2);assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isZero();assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,grant)).isEqualTo("INVALIDATED");assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,sibling)).isEqualTo("ACTIVE");
    }
    @Test void disabledRetainedPermissionsAreAllowedButEmptyConfigurationIsNot()throws Exception{
        Data f=data();jdbc.update("UPDATE permission SET enabled=0 WHERE system_id=?",f.system());apply(approved("ROLE_GRANT",target(f)));
        Data bad=data();jdbc.update("DELETE FROM role_permission WHERE role_id=?",bad.role());String request=approved("ROLE_GRANT",target(bad));rejected(request,"INVALID_ROLE_CONFIGURATION");
    }
    @Test void evidenceFailurePoisonsTransactionEvenWhenCallerCatches()throws Exception{
        Data f=data();String request=approved("ROLE_GRANT",target(f));long revision=scalar("SELECT revision FROM mutation_guard WHERE id=1");
        db.execute("CREATE TRIGGER fail_be16_evidence BEFORE INSERT ON grant_employment_evidence FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected evidence failure'");
        try{assertThatThrownBy(()->write(c->{jdbc.update("UPDATE employee SET name='Outer change' WHERE id=?",f.employee());try{applier.apply(c,request);}catch(RuntimeException ignored){}return null;})).isInstanceOf(RuntimeException.class);}finally{db.execute("DROP TRIGGER fail_be16_evidence");}
        assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE baseline_id=?",f.baseline())).isZero();assertThat(scalar("SELECT relation_version FROM grant_baseline WHERE id=?",f.baseline())).isZero();assertThat(jdbc.queryForObject("SELECT name FROM employee WHERE id=?",String.class,f.employee())).isEqualTo("Fixture");assertThat(scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo(revision);
    }
    @Test void successfulAuditFailureRollsBackGrantVersionAndReceiptTogether()throws Exception{
        Data f=data();String request=approved("ROLE_GRANT",target(f));db.execute("CREATE TRIGGER fail_be16_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected audit failure'");
        try{assertThatThrownBy(()->apply(request)).isInstanceOf(RuntimeException.class);}finally{db.execute("DROP TRIGGER fail_be16_audit");}
        assertThat(scalar("SELECT relation_version FROM grant_baseline WHERE id=?",f.baseline())).isZero();assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE baseline_id=?",f.baseline())).isZero();assertThat(scalar("SELECT COUNT(*) FROM execution_success WHERE request_id=?",request)).isZero();assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,request)).isEqualTo("APPROVED");
    }
    @Test void sourceAndRelationOverflowRollBackAllSelections()throws Exception{
        Data a=data(),b=data();jdbc.update("UPDATE authorization_source SET source_version=9007199254740991 WHERE id=?",b.source());
        assertThatThrownBy(()->write(c->closer.close(c,List.of(selection(a,0,0),selection(b,0,9007199254740991L)),"Overflow"))).isInstanceOf(GrantMutationFailure.class);assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",a.source())).isZero();
        jdbc.update("UPDATE grant_baseline SET relation_version=9007199254740991 WHERE id=?",a.baseline());String request=approved("ROLE_GRANT",current(a,null));rejected(request,"VERSION_EXHAUSTED");
    }
    @Test void sourceFactsRequireBothIndependentDepartmentsRatherThanEquality()throws Exception{
        Data f=data(true);employment(f.employee(),f.company(),f.otherDepartment());
        var baseline=write(c->reader.baseline(f.key()));assertThat(baseline.eligibleForGrant()).isTrue();assertThat(baseline.employments()).extracting(OrganizationReader.Employment::id).containsExactly(UUID.fromString(f.employment()));
        endJob(f.employment());assertThat(write(c->reader.baseline(f.key())).eligibleForGrant()).isFalse();
    }
    private Target secondTarget(Data f){
        String role=id(),baseline=id();jdbc.update("INSERT INTO business_role(id,code,name,company_id,system_id,state,configuration_version,created_at) VALUES(?,?,'Fixture',?,?,'EFFECTIVE',4,UTC_TIMESTAMP(6))",role,role,f.company(),f.system());jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) SELECT ?,permission_id,system_id FROM role_permission WHERE role_id=?",role,f.role());jdbc.update("INSERT INTO grant_baseline(id,source_id,role_id,company_id,system_id) VALUES(?,?,?,?,?)",baseline,f.source(),role,f.company(),f.system());return new Target(new Key(f.employee(),role,f.company(),f.department()),f.system(),f.source(),baseline,0,4,0,null);
    }
    @Test void fullBatchWritesAllTargetsAndNthStorageFailureRollsEverythingBack()throws Exception{
        Data f=data();Target second=secondTarget(f);String request=approved("ROLE_GRANT",target(f),second);
        db.execute("CREATE TRIGGER fail_be16_second BEFORE INSERT ON role_grant FOR EACH ROW BEGIN IF NEW.baseline_id='"+second.baseline()+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected second grant failure'; END IF; END");
        try{assertThatThrownBy(()->apply(request)).isInstanceOf(PersistenceFailure.class);}finally{db.execute("DROP TRIGGER fail_be16_second");}
        assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE baseline_id IN (?,?)",f.baseline(),second.baseline())).isZero();assertThat(scalar("SELECT SUM(relation_version) FROM grant_baseline WHERE id IN (?,?)",f.baseline(),second.baseline())).isZero();
        var facts=apply(request);assertThat(facts.relations()).hasSize(2);assertThat(facts.relations()).extracting(AffectedFacts.RelationChange::status).containsOnly("ACTIVE");assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=?",request)).isEqualTo(2);assertThat(scalar("SELECT COUNT(*) FROM execution_success WHERE request_id=?",request)).isEqualTo(1);
        var revokes=facts.relations().stream().map(r->new Target(r.key(),f.system(),r.sourceId(),r.baselineId(),1,4,0,r.grantId())).toArray(Target[]::new);String revoke=approved("ROLE_REVOCATION",revokes);apply(revoke);assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=? AND status='REVOKED' AND relation_version=2",request)).isEqualTo(2);
    }
    @Test void closeFailureRollsBackAllEpochsGrantsAndHistoryEvidence()throws Exception{
        Data f=data();Target second=secondTarget(f);String request=approved("ROLE_GRANT",target(f),second);var granted=apply(request);String failing=granted.relations().getLast().grantId();
        db.execute("CREATE TRIGGER fail_be16_end BEFORE UPDATE ON role_grant FOR EACH ROW BEGIN IF NEW.id='"+failing+"' AND NEW.status='INVALIDATED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected end failure'; END IF; END");
        try{assertThatThrownBy(()->write(c->closer.close(c,List.of(selection(f,0,0)),"Close all"))).isInstanceOf(PersistenceFailure.class);}finally{db.execute("DROP TRIGGER fail_be16_end");}
        assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isZero();assertThat(scalar("SELECT source_version FROM authorization_source WHERE id=?",f.source())).isZero();assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=? AND status='ACTIVE' AND relation_version=1",request)).isEqualTo(2);assertThat(scalar("SELECT SUM(relation_version) FROM grant_baseline WHERE id IN (?,?)",f.baseline(),second.baseline())).isEqualTo(2);
        assertThatThrownBy(()->jdbc.update("DELETE FROM grant_employment_evidence WHERE grant_id=?",failing)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(scalar("SELECT COUNT(*) FROM grant_employment_evidence WHERE grant_id=?",failing)).isEqualTo(1);
    }
    @Test void relationOnlyQualificationLossBlocksPendingGrantWithoutClosingItsSource()throws Exception{
        Data f=data(true);String job=employment(f.employee(),f.company(),f.otherDepartment()),request=approved("ROLE_GRANT",target(f));endJob(job);
        var result=write(c->closer.invalidateRelations(c,List.of(new SourceCloser.RelationSelection(f.key(),0)),"Lost role qualification"));assertThat(result.relations().getFirst().grantId()).isNull();assertThat(result.relations().getFirst().afterVersion()).isEqualTo(1);employment(f.employee(),f.company(),f.otherDepartment());rejected(request,"RELATION_VERSION_CHANGED");assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isZero();
    }
    @Test void originalScopeCoverageIsRecheckedWithoutRequiringItsOldAppointment()throws Exception{
        Data f=data();String scope=id(),appointment=id();jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,f.company(),f.department());jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,f.system());jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",appointment,scope,account);
        String request=approvedInScope("ROLE_GRANT",scope,appointment,target(f));jdbc.update("UPDATE admin_appointment SET status='REVOKED',ended_at=UTC_TIMESTAMP(6),ended_reason='Left' WHERE id=?",appointment);jdbc.update("UPDATE management_scope SET scope_version=2 WHERE id=?",scope);
        String grant=apply(request).relations().getFirst().grantId();String revoke=approvedInScope("ROLE_REVOCATION",scope,appointment,current(f,grant));jdbc.update("DELETE FROM scope_system WHERE scope_id=?",scope);rejected(revoke,"ORIGINAL_SCOPE_LOST");assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,grant)).isEqualTo("ACTIVE");
    }
    @Test void portsRejectExpiredContextAndWrongDatasourceBeforeAnyWrites()throws Exception{
        Data f=data();var expired=write(c->c);
        assertThatThrownBy(()->baselines.baseline(expired,f.key())).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->applier.apply(expired,id())).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->closer.close(expired,List.of(selection(f,0,0)),"Expired")).isInstanceOf(IllegalStateException.class);
        var otherSource=new org.springframework.jdbc.datasource.DriverManagerDataSource();var otherReader=new GrantReader(otherSource,new OrganizationReader(otherSource));var otherWriter=new BaselineWriter(otherSource,otherReader);
        assertThatThrownBy(()->write(c->otherWriter.baseline(c,f.key()))).isInstanceOf(IllegalStateException.class);assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isZero();
    }
    private void race(boolean closeFirst)throws Exception{
        Data f=data();String request=approved("ROLE_GRANT",target(f));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var secondStarted=new CountDownLatch(1);var secondEntered=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->write(c->{
                if(closeFirst)closer.close(c,List.of(selection(f,0,0)),"First close");else{applier.apply(c,request);complete(c,request);}
                entered.countDown();assertThat(release.await(20,TimeUnit.SECONDS)).isTrue();return null;
            }));
            assertThat(entered.await(20,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->{secondStarted.countDown();return write(c->{secondEntered.countDown();if(closeFirst)return applier.apply(c,request);return closer.close(c,List.of(selection(f,0,0)),"Second close");});});
            assertThat(secondStarted.await(10,TimeUnit.SECONDS)).isTrue();assertThat(secondEntered.await(250,TimeUnit.MILLISECONDS)).as("second guarded work waits for first transaction").isFalse();
            // A separate autocommit connection cannot see the first transaction's provisional writes.
            assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isZero();assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE baseline_id=?",f.baseline())).isZero();release.countDown();first.get(20,TimeUnit.SECONDS);
            if(closeFirst)assertThatThrownBy(()->second.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(GrantMutationFailure.class);else assertThat(second.get(20,TimeUnit.SECONDS).relations()).hasSize(1);
            assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isEqualTo(1);assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE baseline_id=? AND status='ACTIVE'",f.baseline())).isZero();assertThat(scalar("SELECT COUNT(*) FROM role_grant WHERE baseline_id=?",f.baseline())).isEqualTo(closeFirst?0:1);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(20,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void executionThenConcurrentCloseEndsTheNewGrant()throws Exception{race(false);}
    @Test void closeThenConcurrentExecutionCannotCreateAnOldEpochGrant()throws Exception{race(true);}
    @Test void endThenAddInOneTransactionDoesNotEraseTheIntermediateClose()throws Exception{
        Data f=data();String grant=apply(approved("ROLE_GRANT",target(f))).relations().getFirst().grantId();
        write(c->{endJob(f.employment());closer.close(c,List.of(selection(f,0,0)),"Explicit last employment ended");employment(f.employee(),f.company(),f.department());return null;});
        assertThat(scalar("SELECT source_epoch FROM authorization_source WHERE id=?",f.source())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,grant)).isEqualTo("INVALIDATED");assertThat(write(c->reader.baseline(f.key())).eligibleForGrant()).isTrue();assertThat(write(c->reader.baseline(f.key())).activeGrantId()).isNull();
    }
    @Test void badSelectionRollsBackNewZeroAnchorsAndRowVersionExhaustionIsChecked()throws Exception{
        Data f=data();var missing=new SourceKey(f.employee(),f.company(),f.otherDepartment(),f.system());
        assertThatThrownBy(()->write(c->closer.close(c,List.of(new SourceCloser.Selection(missing,0,0),selection(f,1,1)),"Stale member"))).isInstanceOf(GrantMutationFailure.class);assertThat(scalar("SELECT COUNT(*) FROM authorization_source WHERE employee_id=? AND department_id=?",f.employee(),f.otherDepartment())).isZero();
        jdbc.update("UPDATE grant_baseline SET row_version=9007199254740991 WHERE id=?",f.baseline());String request=approved("ROLE_GRANT",target(f));rejected(request,"VERSION_EXHAUSTED");assertThat(scalar("SELECT relation_version FROM grant_baseline WHERE id=?",f.baseline())).isZero();
        jdbc.update("UPDATE authorization_source SET row_version=9007199254740991 WHERE id=?",f.source());assertThatThrownBy(()->write(c->closer.close(c,List.of(selection(f,0,0)),"Overflow"))).isInstanceOfSatisfying(GrantMutationFailure.class,e->assertThat(e.code()).isEqualTo("VERSION_EXHAUSTED"));
    }
}
