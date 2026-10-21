package com.adgn.changefixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.PasswordPolicy;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChangeDraftMySqlCheck {
    IsolatedMySqlDatabase db;
    ConfigurableApplicationContext app;
    JdbcTemplate jdbc;
    Path keys;
    String base,token,otherToken,restrictedToken,superAccount,company,department,otherDepartment,system,account,employee,passwordHash;
    final JsonMapper json=JsonMapper.builder().build();
    final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    final String password="Fixture-Scope-123456";
    static String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"be20-keys-");
        for(String family:List.of("admin","business","oa")){
            var key=new RSAKeyGenerator(2048).keyID(family).generate();
            Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));
        }
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        db=new IsolatedMySqlDatabase();
        app=db.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
                "--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),
                "--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),
                "--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),
                "--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000",
                "--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture",
                "--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(secret))));
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));
        base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/admin/";
        passwordHash=new PasswordPolicy().encode(password);
        superAccount=account(null,true,false);token=login(superAccount);
        otherToken=login(account(null,true,false));restrictedToken=login(account(null,true,true));
    }
    @BeforeEach void fixture(){
        company=id();jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'Company',UTC_TIMESTAMP(6))",company,company);
        department=department(company);otherDepartment=department(company);
        system=id();jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,'System',UTC_TIMESTAMP(6))",system,system);
        employee=employee();account=account(employee,false,false);employ(employee,department);
    }
    String department(String c){String d=id();jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Dept',UTC_TIMESTAMP(6))",d,c,d);return d;}
    String employee(){String e=id();jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Employee',UTC_TIMESTAMP(6))",e,e);return e;}
    void employ(String e,String d){String p=id();jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'Position',UTC_TIMESTAMP(6))",p,d,p);
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,status,created_at) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6))",id(),e,company,d,p);}
    String account(String e,boolean global,boolean restricted){String a=id();jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",a,e,a,passwordHash,global,restricted);return a;}
    HttpResponse<String> request(String method,String path,Object input,String bearer,String key)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30));
        if(bearer!=null)b.header("Authorization","Bearer "+bearer);if(key!=null)b.header("Idempotency-Key",key);
        if(input!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,input==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode body(HttpResponse<String> r){return json.readTree(r.body());}
    String login(String a)throws Exception{var r=request("POST","sessions",Map.of("loginName",a,"password",password),null,null);assertThat(r.statusCode()).isEqualTo(201);return body(r).path("accessToken").stringValue();}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    void failure(HttpResponse<String> r,int status,String code){assertThat(r.statusCode()).as("response: %s",r.body()).isEqualTo(status);assertThat(body(r).path("code").stringValue()).isEqualTo(code);}
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(db!=null)db.close();}finally{if(keys!=null){for(String f:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(f+".json"));Files.deleteIfExists(keys);}}}}


    Map<String,Object> roleInput(){return new TreeMap<>(Map.of("name","草稿角色","companyId",company,"departmentId",department,"systemId",system,"reason","创建草稿"));}
    JsonNode createRole()throws Exception{var r=request("POST","roles",roleInput(),token,id());assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return body(r);}
    Map<String,Object> configuration(String role,long version,List<String> permissions){return Map.of("kind","ROLE_CONFIGURATION","companyId",company,"roleId",role,"expectedConfigurationVersion",version,"permissionIds",permissions);}
    HttpResponse<String> create(Object content)throws Exception{return request("POST","change-requests",Map.of("content",content,"reason","保存草稿"),token,id());}
    @Test void newRoleCreatesOnlyDraftAndClaimAndCanBeDeletedAndReplayed()throws Exception{
        long roles=count("business_role"),drafts=count("change_request"),audits=count("audit_event");
        var created=createRole();String role=created.path("role").path("id").stringValue(),draft=created.path("draftId").stringValue();
        assertThat(created.path("role").path("state").stringValue()).isEqualTo("UNCONFIGURED");
        assertThat(created.path("role").path("configurationVersion").longValue()).isZero();
        assertThat(created.path("role").path("code").stringValue()).matches("ROLE_[0-9a-f]{32}");
        assertThat(created.path("draftVersion").longValue()).isEqualTo(1);
        assertThat(count("business_role")).isEqualTo(roles+1);assertThat(count("change_request")).isEqualTo(drafts+1);
        assertThat(jdbc.queryForObject("SELECT request_id FROM role_change_claim WHERE role_id=?",String.class,role)).isEqualTo(draft);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM change_target WHERE request_id=?",Long.class,draft)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role_id=?",Long.class,role)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id WHERE b.role_id=?",Long.class,role)).isZero();
        String key=id(),path="change-requests/"+draft+"?expectedDraftVersion=1";
        var deleted=request("DELETE",path,null,token,key);assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(204);
        assertThat(count("business_role")).isEqualTo(roles);assertThat(count("change_request")).isEqualTo(drafts);
        var replay=request("DELETE",path,null,token,key);assertThat(replay.statusCode()).as(replay.body()).isEqualTo(204);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(count("audit_event")).isEqualTo(audits+2);
    }
    @Test void collaborativeEditChecksVersionAndPreservesEffectivePermissions()throws Exception{
        var made=createRole();String role=made.path("role").path("id").stringValue(),draft=made.path("draftId").stringValue();
        var content=configuration(role,0,List.of());
        var edit=Map.of("expectedDraftVersion",1,"content",content,"reason","协作修改原因");
        var result=request("PUT","change-requests/"+draft,edit,otherToken,id());assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        assertThat(body(result).path("draftVersion").longValue()).isEqualTo(2);
        assertThat(body(result).path("state").stringValue()).isEqualTo("DRAFT");assertThat(body(result).path("submittedVersion").isNull()).isTrue();
        assertThat(body(result).propertyNames()).containsExactlyInAnyOrder("id","content","reason","draftVersion","submittedVersion","contentDigest","statusVersion","state","deliveryStatus","approvalResult","executionStatus","originalAuthority","submittedByAccountId","oaApprovalId","retryable","failureCode","nextRetryAt","createdAt","submittedAt");
        failure(request("PUT","change-requests/"+draft,edit,token,id()),409,"VERSION_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role_id=?",Long.class,role)).isZero();
        failure(create(content),409,"CHANNEL_OCCUPIED");
    }
    @Test void failedAuditRollsBackNewRoleDraftClaimAndIdempotentCompletion()throws Exception{
        long roles=count("business_role"),drafts=count("change_request"),claims=count("role_change_claim"),audit=count("audit_event");String key=id();
        db.execute("CREATE TRIGGER be20_fail_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='BE20 injected audit failure'");
        try{assertThat(request("POST","roles",roleInput(),token,key).statusCode()).isEqualTo(500);}finally{db.execute("DROP TRIGGER be20_fail_audit");}
        assertThat(count("business_role")).isEqualTo(roles);assertThat(count("change_request")).isEqualTo(drafts);assertThat(count("role_change_claim")).isEqualTo(claims);assertThat(count("audit_event")).isEqualTo(audit);
        assertThat(jdbc.queryForObject("SELECT state FROM http_idempotency WHERE idempotency_key=?",String.class,key)).isEqualTo("IN_PROGRESS");
    }

    String effectiveRole(){return effectiveRoleAt(department);}
    String effectiveRoleAt(String ownerDepartment){
        String role=id(),permission=id();
        jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,menu_eligible,created_at) VALUES(?,?,?,'Access','SYSTEM_ACCESS',0,UTC_TIMESTAMP(6))",permission,system,"SYSTEM_ACCESS");
        jdbc.update("INSERT INTO business_role(id,code,name,company_id,department_id,system_id,state,configuration_version,created_at) VALUES(?,?,'Effective',?,?,?,'EFFECTIVE',1,UTC_TIMESTAMP(6))",role,"ROLE_"+role.replace("-",""),company,ownerDepartment,system);
        jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",role,permission,system);return role;
    }
    Map<String,Object> grantLine(String role,String e,String dept){
        return new TreeMap<>(Map.of("employeeId",e,"roleId",role,"sourceCompanyId",company,"sourceDepartmentId",dept,"expectedRoleConfigurationVersion",1,"expectedSourceEpoch",0,"expectedRelationVersion",0,"employmentIds",jdbc.queryForList("SELECT id FROM employment WHERE employee_id=? AND department_id=? AND status='ACTIVE' ORDER BY id",String.class,e,dept)));
    }
    Map<String,Object> grants(List<?> lines){return Map.of("kind","ROLE_GRANT","companyId",company,"items",lines);}
    @Test void grantDraftMaterializesLogicalZeroAndClaimsButDoesNotGrant()throws Exception{
        String role=effectiveRole();employ(employee,department);var line=grantLine(role,employee,department);
        var r=create(grants(List.of(line)));assertThat(r.statusCode()).as(r.body()).isEqualTo(201);String draft=body(r).path("id").stringValue();
        assertThat(body(r).path("content").path("items").get(0).path("employmentIds").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",Long.class,draft)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM change_target WHERE request_id=? AND expected_source_epoch=0 AND expected_relation_version=0",Long.class,draft)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id WHERE b.role_id=?",Long.class,role)).isZero();assertThat(count("oa_outbox")).isZero();
        failure(create(grants(List.of(line))),409,"RELATION_OCCUPIED");
        assertThat(request("DELETE","change-requests/"+draft+"?expectedDraftVersion=1",null,token,id()).statusCode()).isEqualTo(204);
        assertThat(create(grants(List.of(line))).statusCode()).isEqualTo(201);
    }
    @Test void missingEmploymentStaleVersionDuplicateKeyAndWrongOwnershipAreRejected()throws Exception{
        String role=effectiveRole();employ(employee,department);var line=grantLine(role,employee,department);
        var incomplete=new TreeMap<>(line);incomplete.put("employmentIds",List.of(((List<?>)line.get("employmentIds")).get(0)));
        failure(create(grants(List.of(incomplete))),409,"VERSION_CONFLICT");
        var stale=new TreeMap<>(line);stale.put("expectedSourceEpoch",7);failure(create(grants(List.of(stale))),409,"VERSION_CONFLICT");
        failure(create(grants(List.of(line,line))),422,"VALIDATION_ERROR");
        var bad=new TreeMap<>(line);bad.put("unknown",true);failure(create(grants(List.of(bad))),422,"VALIDATION_ERROR");
        var wrong=grantLine(role,employee,otherDepartment);wrong.put("employmentIds",line.get("employmentIds"));failure(create(grants(List.of(wrong))),422,"VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim c JOIN grant_baseline b ON b.id=c.baseline_id WHERE b.role_id=?",Long.class,role)).isZero();
    }
    @Test void failedEditAcquiringAnotherClaimKeepsOriginalContentAndClaims()throws Exception{
        String role=effectiveRole(),e2=employee();employ(e2,department);var firstLine=grantLine(role,employee,department);var secondLine=grantLine(role,e2,department);
        var a=create(grants(List.of(firstLine)));var b=create(grants(List.of(secondLine)));assertThat(a.statusCode()).as(a.body()).isEqualTo(201);assertThat(b.statusCode()).as(b.body()).isEqualTo(201);
        String draft=body(a).path("id").stringValue();String old=jdbc.queryForObject("SELECT content FROM change_request WHERE id=?",String.class,draft);
        failure(request("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",1,"content",grants(List.of(secondLine)),"reason","替换明细"),token,id()),409,"RELATION_OCCUPIED");
        assertThat(jdbc.queryForObject("SELECT content FROM change_request WHERE id=?",String.class,draft)).isEqualTo(old);
        assertThat(jdbc.queryForObject("SELECT draft_version FROM change_request WHERE id=?",Long.class,draft)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",Long.class,draft)).isEqualTo(1);
    }
    @Test void configurationCannotChangeKindCompanyOrRoleAndStrictInputRejectsUnknowns()throws Exception{
        var made=createRole();String role=made.path("role").path("id").stringValue(),draft=made.path("draftId").stringValue();
        var bad=new TreeMap<>(configuration(role,0,List.of()));bad.put("kind","ROLE_GRANT");
        failure(create(bad),422,"VALIDATION_ERROR");
        bad=new TreeMap<>(configuration(role,0,List.of()));bad.put("roleId",id());
        failure(request("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",1,"content",bad,"reason","change"),token,id()),422,"VALIDATION_ERROR");
        var roleBody=roleInput();roleBody.put("code","CUSTOM");failure(request("POST","roles",roleBody,token,id()),422,"VALIDATION_ERROR");
        roleBody=roleInput();roleBody.remove("departmentId");failure(request("POST","roles",roleBody,token,id()),422,"VALIDATION_ERROR");
        failure(request("POST","roles",roleInput(),null,id()),401,"AUTHENTICATION_FAILED");
    }
    String scopedToken,appointment,scope;
    void scoped()throws Exception{
        scope=id();appointment=id();jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,company,department);
        jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,system);
        jdbc.update("INSERT INTO admin_appointment(id,account_id,scope_id,status,created_at) VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6))",appointment,account,scope);scopedToken=login(account);
    }
    HttpResponse<String> scopedRequest(String method,String path,Object input,String key)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+scopedToken).header("Idempotency-Key",key)
          .header("X-Admin-Appointment-Id",appointment).header("X-Admin-Appointment-Version","1").header("X-Admin-Scope-Version","1");
        if(input!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,input==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void scopeBoundariesSharedRoleMaintenanceAndDeleteReplayRecheckCurrentCoverage()throws Exception{
        scoped();var input=roleInput();var made=scopedRequest("POST","roles",input,id());assertThat(made.statusCode()).as(made.body()).isEqualTo(201);
        String draft=body(made).path("draftId").stringValue();input.put("departmentId",null);failure(scopedRequest("POST","roles",input,id()),403,"FORBIDDEN");
        input.put("departmentId",otherDepartment);failure(scopedRequest("POST","roles",input,id()),403,"FORBIDDEN");
        String key=id(),path="change-requests/"+draft+"?expectedDraftVersion=1";assertThat(scopedRequest("DELETE",path,null,key).statusCode()).isEqualTo(204);
        assertThat(scopedRequest("DELETE",path,null,key).statusCode()).isEqualTo(204);
        jdbc.update("DELETE FROM scope_system WHERE scope_id=?",scope);
        failure(scopedRequest("DELETE",path,null,key),403,"FORBIDDEN");
    }
    @Test void differentKeysCompetingForSameRelationProduceOnlyOneDraft()throws Exception{
        String role=effectiveRole();var content=grants(List.of(grantLine(role,employee,department)));long drafts=count("change_request");
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return create(content);});var b=pool.submit(()->{start.await();return create(content);});start.countDown();
            var responses=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201,409);
            for(var r:responses)if(r.statusCode()==409)failure(r,409,"RELATION_OCCUPIED");
        }assertThat(count("change_request")).isEqualTo(drafts+1);
    }

    String fixtureGrant(String role,String status,long version){
        String source=id(),baseline=id(),request=id(),grant=id();
        jdbc.update("INSERT INTO authorization_source(id,employee_id,company_id,department_id,system_id) VALUES(?,?,?,?,?)",source,employee,company,department,system);
        jdbc.update("INSERT INTO grant_baseline(id,source_id,role_id,company_id,system_id,relation_version) VALUES(?,?,?,?,?,?)",baseline,source,role,company,system,version);
        jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,'ROLE_GRANT',?,'{}','Test-only historical fixture',UTC_TIMESTAMP(6))",request,company);
        jdbc.update("INSERT INTO role_grant(id,baseline_id,relation_version,source_epoch,status,granted_by_request_id,ended_at,ended_reason,created_at) VALUES(?,?,?,0,?,?,?,?,UTC_TIMESTAMP(6))",grant,baseline,version,status,request,status.equals("ACTIVE")?null:java.sql.Timestamp.from(java.time.Instant.now()),status.equals("ACTIVE")?null:"Fixture ended");
        for(String employment:jdbc.queryForList("SELECT id FROM employment WHERE employee_id=? AND department_id=?",String.class,employee,department))jdbc.update("INSERT INTO grant_employment_evidence(grant_id,employment_id) VALUES(?,?)",grant,employment);
        return grant;
    }
    Map<String,Object> revokes(String grant,long version){return Map.of("kind","ROLE_REVOCATION","companyId",company,"items",List.of(Map.of("grantId",grant,"expectedRelationVersion",version)));}
    @Test void activeRevocationIsDraftOnlyAndEndedExactGrantIsNoChangeWithoutClaim()throws Exception{
        String role=effectiveRole(),grant=fixtureGrant(role,"ACTIVE",1);var r=create(revokes(grant,1));assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        String draft=body(r).path("id").stringValue();assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,grant)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",Long.class,draft)).isEqualTo(1);
        assertThat(request("DELETE","change-requests/"+draft+"?expectedDraftVersion=1",null,token,id()).statusCode()).isEqualTo(204);
        jdbc.update("UPDATE role_grant SET status='REVOKED',ended_at=UTC_TIMESTAMP(6),ended_reason='Fixture ended',relation_version=2 WHERE id=?",grant);
        jdbc.update("UPDATE grant_baseline SET relation_version=2 WHERE id=(SELECT baseline_id FROM role_grant WHERE id=?)",grant);
        var ended=create(revokes(grant,0));assertThat(ended.statusCode()).as(ended.body()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",Long.class,body(ended).path("id").stringValue())).isZero();
    }
    @Test void alreadyActiveGrantIgnoresObsoleteVersionsAndDoesNotClaimOrDuplicate()throws Exception{
        String role=effectiveRole();fixtureGrant(role,"ACTIVE",1);var line=grantLine(role,employee,department);line.put("expectedRoleConfigurationVersion",0);line.put("expectedSourceEpoch",100);line.put("expectedRelationVersion",900);
        long grants=count("role_grant");var a=create(grants(List.of(line)));var b=create(grants(List.of(line)));assertThat(a.statusCode()).as(a.body()).isEqualTo(201);assertThat(b.statusCode()).as(b.body()).isEqualTo(201);
        for(var r:List.of(a,b))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",Long.class,body(r).path("id").stringValue())).isZero();
        assertThat(count("role_grant")).isEqualTo(grants);
    }
    void fixed(String request,String state){
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=draft_version,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type='GLOBAL',original_authority='{}',fixed_payload_bytes=?,content_digest=UNHEX(SHA2('fixture',256)) WHERE id=?",superAccount,new byte[]{1},request);
        if(state.equals("REJECTED"))rejectFixture(request);
    }
    @Test void submittedDraftCannotEditOrDeleteAndRejectedCopyCreatesNewRequest()throws Exception{
        var made=createRole();String role=made.path("role").path("id").stringValue(),draft=made.path("draftId").stringValue();fixed(draft,"PENDING");
        failure(request("DELETE","change-requests/"+draft+"?expectedDraftVersion=1",null,token,id()),409,"STATE_CONFLICT");
        failure(request("PUT","change-requests/"+draft,Map.of("content",configuration(role,0,List.of()),"reason","change","expectedDraftVersion",1),token,id()),409,"STATE_CONFLICT");
        rejectFixture(draft);jdbc.update("DELETE FROM role_change_claim WHERE request_id=?",draft);
        var copied=request("POST","change-requests",Map.of("content",configuration(role,0,List.of()),"reason","驳回后修改","copiedFromRequestId",draft),token,id());assertThat(copied.statusCode()).as(copied.body()).isEqualTo(201);
        assertThat(body(copied).path("id").stringValue()).isNotEqualTo(draft);assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft)).isEqualTo("REJECTED");
        failure(request("POST","change-requests",Map.of("content",configuration(role,0,List.of()),"reason","copy","copiedFromRequestId",body(copied).path("id").stringValue()),token,id()),409,"STATE_CONFLICT");
    }
    @Test void sameInputDoesNotAdvanceVersionButChangedKeyContentConflicts()throws Exception{
        var made=createRole();String role=made.path("role").path("id").stringValue(),draft=made.path("draftId").stringValue();var content=configuration(role,0,List.of());String key=id();
        var edit=Map.of("expectedDraftVersion",1,"content",content,"reason","创建草稿");
        var result=request("PUT","change-requests/"+draft,edit,token,key);assertThat(result.statusCode()).as(result.body()).isEqualTo(200);assertThat(body(result).path("draftVersion").longValue()).isEqualTo(1);
        var changed=Map.of("expectedDraftVersion",1,"content",content,"reason","different");failure(request("PUT","change-requests/"+draft,changed,token,key),409,"IDEMPOTENCY_CONFLICT");
    }
    @Test void grantMustBeEligibleInBothRoleDepartmentAndSourceDepartment()throws Exception{
        String role=effectiveRoleAt(otherDepartment);
        var content=grants(List.of(grantLine(role,employee,department)));failure(create(content),422,"VALIDATION_ERROR");
        employ(employee,otherDepartment);var valid=create(content);assertThat(valid.statusCode()).as(valid.body()).isEqualTo(201);
        scoped();failure(scopedRequest("POST","change-requests",Map.of("content",content,"reason","越范围角色"),id()),404,"NOT_FOUND");
    }
    @Test void unrelatedOccupancyDoesNotLeakAndLostScopeDraftRequiresGlobalCleanup()throws Exception{
        scoped();var made=scopedRequest("POST","roles",roleInput(),id());assertThat(made.statusCode()).as(made.body()).isEqualTo(201);String draft=body(made).path("draftId").stringValue();
        jdbc.update("DELETE FROM scope_system WHERE scope_id=?",scope);failure(scopedRequest("DELETE","change-requests/"+draft+"?expectedDraftVersion=1",null,id()),404,"NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft)).isEqualTo("DRAFT");
        assertThat(request("DELETE","change-requests/"+draft+"?expectedDraftVersion=1",null,token,id()).statusCode()).isEqualTo(204);
    }
    @Test void allTargetsAreAuthorizedBeforeNoChangeClassification()throws Exception{
        String role=effectiveRoleAt(otherDepartment),grant=fixtureGrant(role,"REVOKED",2);scoped();
        failure(scopedRequest("POST","change-requests",Map.of("content",revokes(grant,0),"reason","ended hidden"),id()),404,"NOT_FOUND");
    }
    @Test void malformedVersionRangesBatchLimitAndDuplicateEmploymentAreRejected()throws Exception{
        String role=effectiveRole();var line=grantLine(role,employee,department);line.put("expectedSourceEpoch",9007199254740992L);failure(create(grants(List.of(line))),422,"VALIDATION_ERROR");
        line=grantLine(role,employee,department);line.put("expectedSourceEpoch",0.0);failure(create(grants(List.of(line))),422,"VALIDATION_ERROR");
        line=grantLine(role,employee,department);var employment=((List<?>)line.get("employmentIds")).get(0);line.put("employmentIds",List.of(employment,employment));failure(create(grants(List.of(line))),422,"VALIDATION_ERROR");
        failure(create(grants(Collections.nCopies(101,grantLine(role,employee,department)))),422,"VALIDATION_ERROR");
    }

    void rejectFixture(String draft){
        String oa=id(),event=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oa,oa);
        jdbc.update("INSERT INTO oa_link(request_id,oa_instance_id,oa_approval_id,accepted_at,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",draft,oa,id());
        jdbc.update("INSERT INTO oa_callback_event(id,oa_instance_id,event_id,request_id,payload_digest,safe_payload,received_at) VALUES(?,?,?,?,UNHEX(REPEAT('00',32)),'{}',UTC_TIMESTAMP(6))",event,oa,id(),draft);
        jdbc.update("INSERT INTO oa_final_result(request_id,first_event_id,decision,decided_at,comment,received_at) VALUES(?,?,'REJECTED',UTC_TIMESTAMP(6),'Fixture',UTC_TIMESTAMP(6))",draft,event);
        jdbc.update("UPDATE change_request SET state='REJECTED',delivery_status='LINKED',approval_result='REJECTED' WHERE id=?",draft);
    }
    @Test void twoHistoricalGrantIdsFromOneBaselineAreRejectedAsDuplicateBusinessTarget()throws Exception{
        String role=effectiveRole(),old=fixtureGrant(role,"REVOKED",1),active=id();String baseline=jdbc.queryForObject("SELECT baseline_id FROM role_grant WHERE id=?",String.class,old),request=jdbc.queryForObject("SELECT granted_by_request_id FROM role_grant WHERE id=?",String.class,old);
        jdbc.update("UPDATE grant_baseline SET relation_version=2 WHERE id=?",baseline);
        jdbc.update("INSERT INTO role_grant(id,baseline_id,relation_version,source_epoch,status,granted_by_request_id,created_at) VALUES(?,?,2,0,'ACTIVE',?,UTC_TIMESTAMP(6))",active,baseline,request);
        jdbc.update("INSERT INTO grant_employment_evidence(grant_id,employment_id) SELECT ?,id FROM employment WHERE employee_id=? AND department_id=?",active,employee,department);
        var content=Map.of("kind","ROLE_REVOCATION","companyId",company,"items",List.of(Map.of("grantId",old,"expectedRelationVersion",1),Map.of("grantId",active,"expectedRelationVersion",2)));
        failure(create(content),422,"VALIDATION_ERROR");
    }
    @Test void configurationAddsOnlyAvailablePermissionsAndAllowsRetainedDisabledOnes()throws Exception{
        String role=effectiveRole(),permission=jdbc.queryForObject("SELECT permission_id FROM role_permission WHERE role_id=?",String.class,role);
        var made=create(configuration(role,1,List.of(permission)));assertThat(made.statusCode()).as(made.body()).isEqualTo(201);String draft=body(made).path("id").stringValue();
        jdbc.update("UPDATE permission SET enabled=0 WHERE id=?",permission);
        var edit=request("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",1,"content",configuration(role,1,List.of(permission)),"reason","保留停用权限"),token,id());assertThat(edit.statusCode()).as(edit.body()).isEqualTo(200);
        var newRole=createRole();String newId=newRole.path("role").path("id").stringValue(),newDraft=newRole.path("draftId").stringValue();
        failure(request("PUT","change-requests/"+newDraft,Map.of("expectedDraftVersion",1,"content",configuration(newId,0,List.of(permission)),"reason","新增停用权限"),token,id()),422,"VALIDATION_ERROR");
    }

    @Test void deleteReplayMayUseAnotherCurrentAppointmentCoveringSavedTargets()throws Exception{
        scoped();var made=scopedRequest("POST","roles",roleInput(),id());assertThat(made.statusCode()).as(made.body()).isEqualTo(201);
        String draft=body(made).path("draftId").stringValue(),key=id(),path="change-requests/"+draft+"?expectedDraftVersion=1";
        assertThat(scopedRequest("DELETE",path,null,key).statusCode()).isEqualTo(204);
        jdbc.update("UPDATE admin_appointment SET status='REVOKED',ended_at=UTC_TIMESTAMP(6),ended_reason='Fixture replacement',appointment_version=appointment_version+1 WHERE id=?",appointment);appointment=id();
        jdbc.update("INSERT INTO admin_appointment(id,account_id,scope_id,status,created_at) VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6))",appointment,account,scope);
        var replay=scopedRequest("DELETE",path,null,key);assertThat(replay.statusCode()).as(replay.body()).isEqualTo(204);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
    }
    @Test void employmentEvidenceMayExceedThe100TargetLimit()throws Exception{
        String role=effectiveRole();for(int i=0;i<100;i++)employ(employee,department);
        var r=create(grants(List.of(grantLine(role,employee,department))));assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        assertThat(body(r).path("content").path("items").get(0).path("employmentIds").size()).isEqualTo(101);
    }

    <T> T guarded(com.adgn.backend.core.transaction.CommandBoundary.CheckedFunction<com.adgn.backend.core.transaction.WriteContext<com.adgn.backend.query.management.ManagementAccess.Authority>,T> work)throws Exception{
        var jwt=app.getBean(com.adgn.backend.identity.security.IdentityTokens.class).verify(token,com.adgn.backend.identity.security.IdentityTokens.Family.ADMIN);
        return app.getBean(com.adgn.backend.core.transaction.CommandBoundary.class).execute(()->app.getBean(com.adgn.backend.query.management.ManagementAccess.class).global(jwt).identity(),work);
    }
    @Test void internalInvalidationPreservesDraftButInvalidatesWholeSubmittedRequestAndStopsLease()throws Exception{
        String role=effectiveRole(),e2=employee();employ(e2,department);
        var r=create(grants(List.of(grantLine(role,employee,department),grantLine(role,e2,department))));assertThat(r.statusCode()).as(r.body()).isEqualTo(201);String draft=body(r).path("id").stringValue();
        var writer=app.getBean(com.adgn.backend.change.WorkflowStateWriter.class);
        assertThat(guarded(c->writer.invalidate(c,draft,1,"SOURCE_CLOSED")).booleanValue()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",Long.class,draft)).isEqualTo(2);
        fixed(draft,"PENDING");String oa=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oa,oa);
        jdbc.update("INSERT INTO oa_outbox(request_id,oa_instance_id,task_state,next_attempt_at,lease_token,lease_until) VALUES(?,?,'LEASED',UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6)+INTERVAL 1 MINUTE)",draft,oa,id());
        String content=jdbc.queryForObject("SELECT content FROM change_request WHERE id=?",String.class,draft);
        assertThat(guarded(c->writer.invalidate(c,draft,1,"SOURCE_CLOSED")).booleanValue()).isTrue();
        assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft)).isEqualTo("INVALIDATED");
        assertThat(jdbc.queryForObject("SELECT status_version FROM change_request WHERE id=?",Long.class,draft)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT draft_version FROM change_request WHERE id=?",Long.class,draft)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM change_request WHERE id=?",String.class,draft)).isEqualTo(content);
        assertThat(jdbc.queryForObject("SELECT task_state FROM oa_outbox WHERE request_id=?",String.class,draft)).isEqualTo("STOPPED");
        assertThat(jdbc.queryForObject("SELECT lease_token FROM oa_outbox WHERE request_id=?",String.class,draft)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",Long.class,draft)).isZero();
        assertThat(guarded(c->writer.invalidate(c,draft,1,"SOURCE_CLOSED")).booleanValue()).isFalse();
    }

    @Test void draftGrantAuditFailureRollsBackMaterializedSourceBaselineAndClaims()throws Exception{
        String role=effectiveRole();var content=grants(List.of(grantLine(role,employee,department)));long sources=count("authorization_source"),baselines=count("grant_baseline"),drafts=count("change_request"),claims=count("relation_change_claim");
        db.execute("CREATE TRIGGER be20_fail_grant_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='BE20 injected grant audit failure'");
        try{assertThat(create(content).statusCode()).isEqualTo(500);}finally{db.execute("DROP TRIGGER be20_fail_grant_audit");}
        assertThat(count("authorization_source")).isEqualTo(sources);assertThat(count("grant_baseline")).isEqualTo(baselines);assertThat(count("change_request")).isEqualTo(drafts);assertThat(count("relation_change_claim")).isEqualTo(claims);
    }
    @Test void lifecycleStateAndClaimReleaseRollBackWithOuterFailure()throws Exception{
        var made=createRole();String draft=made.path("draftId").stringValue();fixed(draft,"PENDING");var writer=app.getBean(com.adgn.backend.change.WorkflowStateWriter.class);
        assertThatThrownBy(()->guarded(c->{assertThat(writer.invalidate(c,draft,1,"SOURCE_CLOSED")).isTrue();throw new IllegalStateException("Injected outer failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft)).isEqualTo("PENDING");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_change_claim WHERE request_id=?",Long.class,draft)).isEqualTo(1);
        assertThatThrownBy(()->guarded(c->writer.invalidate(c,draft,0,"SOURCE_CLOSED"))).isInstanceOf(com.adgn.backend.identity.security.IdentityFailure.class);
        assertThat(jdbc.queryForObject("SELECT status_version FROM change_request WHERE id=?",Long.class,draft)).isEqualTo(1);
        assertThatThrownBy(()->writer.read(null,draft)).isInstanceOf(NullPointerException.class);
    }
    @Test void hiddenMixedDraftOccupancyReturnsOnlyGenericConflict()throws Exception{
        String role=effectiveRole();employ(employee,otherDepartment);
        var mixed=grants(List.of(grantLine(role,employee,department),grantLine(role,employee,otherDepartment)));var made=create(mixed);assertThat(made.statusCode()).as(made.body()).isEqualTo(201);String hidden=body(made).path("id").stringValue();scoped();
        var response=scopedRequest("POST","change-requests",Map.of("content",grants(List.of(grantLine(role,employee,department))),"reason","same target"),id());failure(response,409,"RELATION_OCCUPIED");assertThat(response.body()).doesNotContain(hidden,otherDepartment);
        assertThat(jdbc.queryForObject("SELECT visibility FROM audit_event WHERE target_id=? AND event_type='CHANGE_DRAFT_CREATED'",String.class,hidden)).isEqualTo("GLOBAL");
    }
}
