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
class SubmissionWorkflowMySqlCheck {
    @Test void missingFixedRequestIsNotFoundAndCreatesNoWorkerWrite()throws Exception{
        long revision=jdbc.queryForObject("SELECT revision FROM mutation_guard WHERE id=1",Long.class);
        assertThatThrownBy(()->app.getBean(com.adgn.backend.core.transaction.CommandBoundary.class).execute(
                com.adgn.backend.change.FixedRequestStore::worker,c->app.getBean(com.adgn.backend.change.FixedRequestStore.class).load(id())))
                .isInstanceOfSatisfying(com.adgn.backend.identity.security.IdentityFailure.class,e->{assertThat(e.status()).isEqualTo(404);assertThat(e.code()).isEqualTo("NOT_FOUND");});
        assertThat(jdbc.queryForObject("SELECT revision FROM mutation_guard WHERE id=1",Long.class)).isEqualTo(revision);
    }
    @Test void revocationOfMissingGrantCannotCreateADraft()throws Exception{
        long drafts=jdbc.queryForObject("SELECT COUNT(*) FROM change_request",Long.class);
        var response=create(revokes(id(),1));assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(body(response).path("code").stringValue()).isEqualTo("NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM change_request",Long.class)).isEqualTo(drafts);
    }
    @Test void mutableDraftCannotBeLoadedAsApprovedFixedWork() throws Exception {
        String draft=configurationDraft();long version=jdbc.queryForObject("SELECT status_version FROM change_request WHERE id=?",Long.class,draft);
        assertThatThrownBy(()->app.getBean(com.adgn.backend.core.transaction.CommandBoundary.class).execute(
                com.adgn.backend.change.FixedRequestStore::worker,c->app.getBean(com.adgn.backend.change.FixedRequestStore.class).load(draft)))
                .isInstanceOfSatisfying(com.adgn.backend.core.transaction.PersistenceFailure.class,e->assertThat(e.kind()).isEqualTo(com.adgn.backend.core.transaction.PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
        assertThat(requestState(draft)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT status_version FROM change_request WHERE id=?",Long.class,draft)).isEqualTo(version);
    }

    @Test void executedRoleConfigurationInvalidatesOldPendingGrantAndReleasesItsClaim() throws Exception {
        String role=effectiveRole();String pending=submitContent(grants(List.of(grantLine(role,employee,department))));
        String function=id(),operation=id();
        jdbc.update("INSERT INTO directory_node(id,system_id,code,name,node_type,created_at) VALUES(?,?,?,'Function','FUNCTION',UTC_TIMESTAMP(6))",function,system,"F_"+function.replace("-",""));
        jdbc.update("INSERT INTO permission(id,system_id,function_id,code,name,permission_type,menu_eligible,created_at) VALUES(?,?,?,'VIEW','View','FUNCTION_OPERATION',1,UTC_TIMESTAMP(6))",operation,system,function);
        jdbc.update("UPDATE directory_node SET enabled=1,visibility_permission_id=? WHERE id=?",operation,function);
        String access=jdbc.queryForObject("SELECT id FROM permission WHERE system_id=? AND permission_type='SYSTEM_ACCESS'",String.class,system);
        String configuration=submitContent(configuration(role,1,List.of(access,operation)));approveExisting(configuration);execute(configuration);
        assertThat(requestState(configuration)).isEqualTo("EXECUTED");assertThat(requestState(pending)).isEqualTo("INVALIDATED");
        assertThat(jdbc.queryForObject("SELECT failure_code FROM change_request WHERE id=?",String.class,pending)).isEqualTo("ROLE_CONFIGURATION_CHANGED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE request_id=?",Long.class,pending)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=?",Long.class,pending)).isZero();
        assertThat(jdbc.queryForObject("SELECT configuration_version FROM business_role WHERE id=?",Long.class,role)).isEqualTo(2);
    }
    IsolatedMySqlDatabase db;
    ConfigurableApplicationContext app;
    JdbcTemplate jdbc;
    Path keys;
    String[] webArgs;
    com.nimbusds.jose.jwk.RSAKey oaKey,businessKey;
    String base,token,otherToken,restrictedToken,superAccount,company,department,otherDepartment,system,account,employee,passwordHash;
    final JsonMapper json=JsonMapper.builder().build();
    final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    final String password="Fixture-Scope-123456";
    static String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"workflow-keys-");
        for(String family:List.of("admin","business","oa")){
            var key=new RSAKeyGenerator(2048).keyID(family).generate();if(family.equals("oa"))oaKey=key;if(family.equals("business"))businessKey=key;
            Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));
        }
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        db=new IsolatedMySqlDatabase();
        webArgs=new String[]{"--rbac.workflow.scheduling-enabled=false","--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
                "--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),
                "--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),
                "--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),
                "--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000",
                "--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture",
                "--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(secret)))};
        app=db.startWeb(webArgs);
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));
        base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/admin/";
        passwordHash=new PasswordPolicy().encode(password);
        superAccount=account(null,true,false);token=login(superAccount);
        otherToken=login(account(null,true,false));restrictedToken=login(account(null,true,true));
    }
    @BeforeEach void fixture(){
        ((org.springframework.beans.factory.support.DefaultListableBeanFactory)app.getBeanFactory()).destroySingleton("fixtureOaAdapter");
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


    void fixed(String request,String state){
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=draft_version,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type='GLOBAL',original_authority='{}',fixed_payload_bytes=?,content_digest=UNHEX(SHA2('fixture',256)) WHERE id=?",superAccount,new byte[]{1},request);
        if(state.equals("REJECTED"))rejectFixture(request);
    }







    void rejectFixture(String draft){
        String oa=id(),event=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oa,oa);
        jdbc.update("INSERT INTO oa_link(request_id,oa_instance_id,oa_approval_id,accepted_at,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",draft,oa,id());
        jdbc.update("INSERT INTO oa_callback_event(id,oa_instance_id,event_id,request_id,payload_digest,safe_payload,received_at) VALUES(?,?,?,?,UNHEX(REPEAT('00',32)),'{}',UTC_TIMESTAMP(6))",event,oa,id(),draft);
        jdbc.update("INSERT INTO oa_final_result(request_id,first_event_id,decision,decided_at,comment,received_at) VALUES(?,?,'REJECTED',UTC_TIMESTAMP(6),'Fixture',UTC_TIMESTAMP(6))",draft,event);
        jdbc.update("UPDATE change_request SET state='REJECTED',delivery_status='LINKED',approval_result='REJECTED' WHERE id=?",draft);
    }






    <T> T guarded(com.adgn.backend.core.transaction.CommandBoundary.CheckedFunction<com.adgn.backend.core.transaction.WriteContext<com.adgn.backend.query.management.ManagementAccess.Authority>,T> work)throws Exception{
        var jwt=app.getBean(com.adgn.backend.identity.security.IdentityTokens.class).verify(token,com.adgn.backend.identity.security.IdentityTokens.Family.ADMIN);
        return app.getBean(com.adgn.backend.core.transaction.CommandBoundary.class).execute(()->app.getBean(com.adgn.backend.query.management.ManagementAccess.class).global(jwt).identity(),work);
    }





    String oa;
    void configureOa(){if(oa==null){oa=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oa,oa);}}
    String configurationDraft()throws Exception{
        configureOa();var made=createRole();String role=made.path("role").path("id").stringValue(),draft=made.path("draftId").stringValue();
        var permissions=jdbc.queryForList("SELECT id FROM permission WHERE system_id=? AND permission_type='SYSTEM_ACCESS'",String.class,system);String permission=permissions.isEmpty()?id():permissions.getFirst();if(permissions.isEmpty())jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,created_at) VALUES(?,?,?,'进入','SYSTEM_ACCESS',UTC_TIMESTAMP(6))",permission,system,permission);
        var r=request("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",1,"content",configuration(role,0,List.of(permission)),"reason","提交配置"),token,id());assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return draft;
    }
    JsonNode preview(String draft,long version)throws Exception{
        var r=request("POST","change-requests/"+draft+"/previews",Map.of("expectedDraftVersion",version),token,null);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return body(r);
    }
    HttpResponse<String> submit(String draft,long version,String preview,String key)throws Exception{return request("POST","change-requests/"+draft+"/submissions",Map.of("expectedDraftVersion",version,"previewId",preview),token,key);}
    @Test void fixedSubmissionAtomicallyCreatesOutboxButNeverGrants()throws Exception{
        String draft=configurationDraft();var p=preview(draft,2);String key=id();var r=submit(draft,2,p.path("preview").path("id").stringValue(),key);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(202);assertThat(body(r).path("outcome").stringValue()).isEqualTo("SUBMITTED");
        assertThat(body(r).path("request").path("submittedVersion").longValue()).isEqualTo(2);assertThat(body(r).path("request").path("contentDigest").stringValue()).matches("[a-f0-9]{64}");
        assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT task_state FROM oa_outbox WHERE request_id=?",String.class,draft)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role_id=(SELECT role_id FROM change_target WHERE request_id=?)",Long.class,draft)).isZero();
        var replay=submit(draft,2,p.path("preview").path("id").stringValue(),key);assertThat(replay.statusCode()).isEqualTo(202);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        var bytes=jdbc.queryForObject("SELECT fixed_payload_bytes FROM change_request WHERE id=?",byte[].class,draft);
        var node=json.readTree(bytes);assertThat(node.propertyNames()).containsExactlyInAnyOrder("requestId","submittedVersion","content","reason","originalAuthority","submittedByAccountId","submittedAt");
        assertThat(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).isEqualTo(jdbc.queryForObject("SELECT content_digest FROM change_request WHERE id=?",byte[].class,draft));
    }
    @Test void editedDraftRequiresNewPreviewAndDoesNotQueueAnything()throws Exception{
        String draft=configurationDraft();var p=preview(draft,2);var content=json.readTree(jdbc.queryForObject("SELECT content FROM change_request WHERE id=?",String.class,draft));
        assertThat(request("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",2,"content",content,"reason","修改原因"),token,id()).statusCode()).isEqualTo(200);
        failure(submit(draft,3,p.path("preview").path("id").stringValue(),id()),409,"PREVIEW_STALE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_outbox WHERE request_id=?",Long.class,draft)).isZero();
    }
    @Test void submissionAuditFailureRollsBackFreezeAndOutbox()throws Exception{
        String draft=configurationDraft();var p=preview(draft,2);
        db.execute("CREATE TRIGGER workflow_fail_submit BEFORE INSERT ON audit_event FOR EACH ROW BEGIN IF NEW.event_type='CHANGE_SUBMITTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected'; END IF; END");
        try{assertThat(submit(draft,2,p.path("preview").path("id").stringValue(),id()).statusCode()).isEqualTo(500);}finally{db.execute("DROP TRIGGER workflow_fail_submit");}
        assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft)).isEqualTo("DRAFT");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_outbox WHERE request_id=?",Long.class,draft)).isZero();
    }

    @Test void fixedTimestampEqualsPublicRequestAndStoredUtcTimestamp()throws Exception{
        String draft=configurationDraft();var p=preview(draft,2);var r=submit(draft,2,p.path("preview").path("id").stringValue(),id());assertThat(r.statusCode()).isEqualTo(202);
        var fixed=json.readTree(jdbc.queryForObject("SELECT fixed_payload_bytes FROM change_request WHERE id=?",byte[].class,draft));assertThat(body(r).path("request").path("submittedAt").stringValue()).isEqualTo(fixed.path("submittedAt").stringValue());
    }
    @Test void expiredPreviewAndChangedEmploymentRequireRepreview()throws Exception{
        String role=effectiveRole();var r=create(grants(List.of(grantLine(role,employee,department))));String draft=body(r).path("id").stringValue();var p=preview(draft,1);String preview=p.path("preview").path("id").stringValue();
        employ(employee,department);failure(submit(draft,1,preview,id()),409,"PREVIEW_STALE");
        String config=configurationDraft();var p2=preview(config,2);jdbc.update("UPDATE impact_preview SET created_at=UTC_TIMESTAMP(6)-INTERVAL 10 MINUTE,expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",p2.path("preview").path("id").stringValue());failure(submit(config,2,p2.path("preview").path("id").stringValue(),id()),409,"PREVIEW_STALE");
    }
    @Test void alreadyGrantedKeepsDraftAndCreatesNoOutbox()throws Exception{
        String role=effectiveRole(),grant=fixtureGrant(role,"ACTIVE",1);var r=create(grants(List.of(grantLine(role,employee,department))));String draft=body(r).path("id").stringValue();var p=preview(draft,1);assertThat(p.path("effectiveContent").isNull()).isTrue();assertThat(p.path("noChangeItems").get(0).path("reason").stringValue()).isEqualTo("ALREADY_GRANTED");
        var response=submit(draft,1,p.path("preview").path("id").stringValue(),id());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);assertThat(body(response).path("outcome").stringValue()).isEqualTo("NO_CHANGE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_outbox WHERE request_id=?",Long.class,draft)).isZero();
    }
    String submitted()throws Exception{String draft=configurationDraft();var p=preview(draft,2);assertThat(submit(draft,2,p.path("preview").path("id").stringValue(),id()).statusCode()).isEqualTo(202);return draft;}
    Object invokeService(String name,String method,String id)throws Exception{
        try{Class<?> type=Class.forName(name);return type.getMethod(method,String.class).invoke(app.getBean(type),id);}catch(ClassNotFoundException missing){throw new AssertionError("Missing workflow service: "+name,missing);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}
    }
    @Test void missingOaAdapterRecordsSafeRetryWithoutLosingFixedRequest()throws Exception{
        String draft=submitted();invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",draft);
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM change_request WHERE id=?",String.class,draft)).isEqualTo("SEND_FAILED");
        String key=id();var r=request("POST","change-requests/"+draft+"/delivery-retries",Map.of("expectedStatusVersion",3,"reason","恢复投递"),token,key);assertThat(r.statusCode()).as(r.body()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM change_request WHERE id=?",String.class,draft)).isEqualTo("PENDING_SEND");
    }

    void adapter(com.adgn.backend.oa.OaSubmissionPort port){((org.springframework.beans.factory.support.DefaultListableBeanFactory)app.getBeanFactory()).destroySingleton("fixtureOaAdapter");app.getBeanFactory().registerSingleton("fixtureOaAdapter",port);}
    void goodAdapter(){adapter(x->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new com.adgn.backend.oa.OaSubmissionPort.Receipt(x.requestId(),x.submittedVersion(),x.contentDigest(),"OA_"+x.requestId(),java.time.Instant.now());});}
    String delivered()throws Exception{String draft=submitted();goodAdapter();invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",draft);assertThat(jdbc.queryForObject("SELECT delivery_status FROM change_request WHERE id=?",String.class,draft)).isEqualTo("LINKED");return draft;}
    String oaToken(String instance)throws Exception{
        var now=java.time.Instant.now();var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("urn:rbac:oa").audience("rbac-oa-callback").subject(instance).claim("oaInstanceId",instance).jwtID(id()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(120))).build();
        var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID("oa").build(),claims);jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(oaKey));return jwt.serialize();
    }
    Map<String,Object> callbackInput(String draft,String decision){var fixed=json.readTree(jdbc.queryForObject("SELECT fixed_payload_bytes FROM change_request WHERE id=?",byte[].class,draft));return new TreeMap<>(Map.of("eventId",id(),"requestId",draft,"oaApprovalId","OA_"+draft,"submittedVersion",fixed.path("submittedVersion").longValue(),"contentDigest",HexFormat.of().formatHex(jdbc.queryForObject("SELECT content_digest FROM change_request WHERE id=?",byte[].class,draft)),"decision",decision,"isFinal",true,"decidedAt","2026-10-20T08:00:00Z","comment","审批测试"));}
    HttpResponse<String> callback(Map<String,Object> input)throws Exception{
        return http.send(HttpRequest.newBuilder(URI.create(base.replace("/admin/","/oa/")+"final-results")).header("Authorization","Bearer "+oaToken(oa)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void reliableCallbackApprovesWithoutGrantAndDeduplicatesAcrossEventIds()throws Exception{
        String draft=delivered();var input=callbackInput(draft,"APPROVED");var first=callback(input);assertThat(first.statusCode()).as(first.body()).isEqualTo(202);assertThat(body(first).path("executionStatus").stringValue()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role_id=(SELECT role_id FROM change_target WHERE request_id=?)",Long.class,draft)).isZero();
        var replay=callback(input);assertThat(replay.statusCode()).isEqualTo(200);assertThat(body(replay).path("duplicate").booleanValue()).isTrue();
        var next=new TreeMap<>(input);next.put("eventId",id());assertThat(callback(next).statusCode()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_final_result WHERE request_id=?",Long.class,draft)).isEqualTo(1);
        var conflict=new TreeMap<>(input);conflict.put("comment","不同内容");failure(callback(conflict),409,"CALLBACK_CONFLICT");next.put("eventId",id());next.put("decision","REJECTED");failure(callback(next),409,"CALLBACK_CONFLICT");
    }
    @Test void callbackWithoutReliableLinkDoesNotConsumeEventId()throws Exception{
        String draft=submitted();var input=callbackInput(draft,"REJECTED");var r=callback(input);failure(r,409,"OA_LINK_PENDING");assertThat(r.headers().firstValue("Retry-After")).contains("5");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_callback_event WHERE event_id=?",Long.class,input.get("eventId"))).isZero();goodAdapter();invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",draft);assertThat(callback(input).statusCode()).isEqualTo(202);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_change_claim WHERE request_id=?",Long.class,draft)).isZero();
    }
    @Test void invalidationDuringNetworkOnlyLeavesHistoricalLinkAndFinalResult()throws Exception{
        String draft=submitted();adapter(x->{guarded(c->{var writer=app.getBean(com.adgn.backend.change.WorkflowStateWriter.class);writer.invalidate(c,draft,2,"SOURCE_EPOCH_CHANGED");return null;});return new com.adgn.backend.oa.OaSubmissionPort.Receipt(x.requestId(),x.submittedVersion(),x.contentDigest(),"OA_"+x.requestId(),java.time.Instant.now());});
        invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",draft);assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft)).isEqualTo("INVALIDATED");assertThat(jdbc.queryForObject("SELECT task_state FROM oa_outbox WHERE request_id=?",String.class,draft)).isEqualTo("STOPPED");assertThat(callback(callbackInput(draft,"APPROVED")).statusCode()).isEqualTo(202);assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft)).isEqualTo("INVALIDATED");
    }
    @Test void callbackAuditFailureRollsBackReliableReceipt()throws Exception{
        String draft=delivered();var input=callbackInput(draft,"APPROVED");db.execute("CREATE TRIGGER workflow_fail_callback BEFORE INSERT ON audit_event FOR EACH ROW BEGIN IF NEW.event_type='OA_APPROVED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected'; END IF; END");
        try{assertThat(callback(input).statusCode()).isEqualTo(500);}finally{db.execute("DROP TRIGGER workflow_fail_callback");}assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_final_result WHERE request_id=?",Long.class,draft)).isZero();assertThat(callback(input).statusCode()).isEqualTo(202);
    }

    String approved()throws Exception{String draft=delivered();var r=callback(callbackInput(draft,"APPROVED"));assertThat(r.statusCode()).as(r.body()).isEqualTo(202);return draft;}
    void execute(String draft)throws Exception{invokeService("com.adgn.backend.execution.ExecutionService","execute",draft);}
    String requestState(String draft){return jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,draft);}
    @Test void approvedConfigurationExecutesExactlyOnceWithSuccessAuditHistoryAndClaimRelease()throws Exception{
        String draft=approved();execute(draft);assertThat(requestState(draft)).isEqualTo("EXECUTED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_configuration_history WHERE request_id=?",Long.class,draft)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_change_claim WHERE request_id=?",Long.class,draft)).isZero();execute(draft);assertThat(jdbc.queryForObject("SELECT configuration_version FROM business_role WHERE id=(SELECT role_id FROM change_target WHERE request_id=?)",Long.class,draft)).isEqualTo(1);
    }
    @Test void executionAuditFailureRollsBackBusinessThenRecordsRetryAndRecovers()throws Exception{
        String draft=approved();db.execute("CREATE TRIGGER workflow_fail_execution BEFORE INSERT ON audit_event FOR EACH ROW BEGIN IF NEW.event_type='EXECUTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected'; END IF; END");
        try{execute(draft);}finally{db.execute("DROP TRIGGER workflow_fail_execution");}
        assertThat(requestState(draft)).isEqualTo("EXECUTION_FAILED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_configuration_history WHERE request_id=?",Long.class,draft)).isZero();assertThat(jdbc.queryForObject("SELECT configuration_version FROM business_role WHERE id=(SELECT role_id FROM change_target WHERE request_id=?)",Long.class,draft)).isZero();
        jdbc.update("UPDATE change_request SET next_retry_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",draft);invokeService("com.adgn.backend.execution.ExecutionService","execute",draft);assertThat(requestState(draft)).isEqualTo("EXECUTED");
    }
    @Test void unavailableAddedPermissionInvalidatesWholeRequestWithoutBusinessChanges()throws Exception{
        String draft=approved();jdbc.update("UPDATE permission SET enabled=0,version=version+1 WHERE id=(SELECT JSON_UNQUOTE(JSON_EXTRACT(content,'$.permissionIds[0]')) FROM change_request WHERE id=?)",draft);execute(draft);assertThat(requestState(draft)).isEqualTo("INVALIDATED");assertThat(jdbc.queryForObject("SELECT failure_code FROM change_request WHERE id=?",String.class,draft)).isEqualTo("PERMISSION_UNAVAILABLE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role_id=(SELECT role_id FROM change_target WHERE request_id=?)",Long.class,draft)).isZero();
    }
    String submitContent(Object content)throws Exception{configureOa();var r=create(content);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);String draft=body(r).path("id").stringValue();var p=preview(draft,1);assertThat(submit(draft,1,p.path("preview").path("id").stringValue(),id()).statusCode()).isEqualTo(202);return draft;}
    void approveExisting(String draft)throws Exception{goodAdapter();invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",draft);assertThat(callback(callbackInput(draft,"APPROVED")).statusCode()).isEqualTo(202);}
    @Test void grantBatchExecutesAllEvidenceOnceAndSourceAdvanceInvalidatesAnotherBatch()throws Exception{
        String role=effectiveRole(),e2=employee();employ(e2,department);String draft=submitContent(grants(List.of(grantLine(role,employee,department),grantLine(role,e2,department))));approveExisting(draft);execute(draft);assertThat(requestState(draft)).isEqualTo("EXECUTED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=?",Long.class,draft)).isEqualTo(2);execute(draft);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=?",Long.class,draft)).isEqualTo(2);
        String e3=employee();employ(e3,department);String next=submitContent(grants(List.of(grantLine(role,e3,department))));approveExisting(next);jdbc.update("UPDATE authorization_source SET source_epoch=source_epoch+1,source_version=source_version+1 WHERE id=(SELECT source_id FROM change_target WHERE request_id=?)",next);execute(next);assertThat(requestState(next)).isEqualTo("INVALIDATED");assertThat(jdbc.queryForObject("SELECT failure_code FROM change_request WHERE id=?",String.class,next)).isEqualTo("SOURCE_EPOCH_CHANGED");
    }
    @Test void secondGrantFailureRollsBackFirstGrantAndEveryRelationVersion()throws Exception{
        String role=effectiveRole(),e2=employee();employ(e2,department);String draft=submitContent(grants(List.of(grantLine(role,employee,department),grantLine(role,e2,department))));approveExisting(draft);String baseline=jdbc.queryForObject("SELECT baseline_id FROM change_target WHERE request_id=? AND line_no=1",String.class,draft);
        db.execute("CREATE TRIGGER workflow_fail_second BEFORE INSERT ON role_grant FOR EACH ROW BEGIN IF NEW.baseline_id='"+baseline+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected second line'; END IF; END");try{execute(draft);}finally{db.execute("DROP TRIGGER workflow_fail_second");}
        assertThat(requestState(draft)).isEqualTo("EXECUTION_FAILED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=?",Long.class,draft)).isZero();assertThat(jdbc.queryForList("SELECT b.relation_version FROM grant_baseline b JOIN change_target t ON t.baseline_id=b.id WHERE t.request_id=?",Long.class,draft)).containsOnly(0L);
    }
    @Test void exactRevocationWorksWhileSystemIsDisabled()throws Exception{
        String role=effectiveRole(),grant=fixtureGrant(role,"ACTIVE",1);String draft=submitContent(revokes(grant,1));approveExisting(draft);jdbc.update("UPDATE business_system SET enabled=0,version=version+1 WHERE id=?",system);execute(draft);assertThat(requestState(draft)).isEqualTo("EXECUTED");assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,grant)).isEqualTo("REVOKED");
    }

    @Test void oneDeliveryLeasePreventsConcurrentNetworkAndMismatchedReceiptIsRejected()throws Exception{
        String draft=submitted();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        adapter(x->{entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return new com.adgn.backend.oa.OaSubmissionPort.Receipt(x.requestId(),x.submittedVersion(),x.contentDigest(),"OA_"+x.requestId(),java.time.Instant.now());});
        try{var first=pool.submit(()->invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",draft));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();assertThat(invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",draft)).isEqualTo(false);release.countDown();first.get(10,TimeUnit.SECONDS);}finally{release.countDown();pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM oa_outbox WHERE request_id=?",Long.class,draft)).isEqualTo(1);
        String other=submitted();adapter(x->new com.adgn.backend.oa.OaSubmissionPort.Receipt(x.requestId(),x.submittedVersion()+1,x.contentDigest(),"wrong",java.time.Instant.now()));invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",other);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_link WHERE request_id=?",Long.class,other)).isZero();assertThat(jdbc.queryForObject("SELECT delivery_status FROM change_request WHERE id=?",String.class,other)).isEqualTo("SEND_FAILED");
    }
    @Test void callbackRejectsAdminWrongInstanceTamperedDigestAndNonFinalResult()throws Exception{
        String draft=delivered();var input=callbackInput(draft,"APPROVED");var r=http.send(HttpRequest.newBuilder(URI.create(base.replace("/admin/","/oa/")+"final-results")).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(401);
        var altered=new TreeMap<>(input);altered.put("contentDigest","a".repeat(64));failure(callback(altered),409,"CALLBACK_CONFLICT");altered=new TreeMap<>(input);altered.put("isFinal",false);failure(callback(altered),422,"VALIDATION_ERROR");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_final_result WHERE request_id=?",Long.class,draft)).isZero();
    }
    String scopedApprovedGrant()throws Exception{
        configureOa();String role=effectiveRole();scoped();var r=scopedRequest("POST","change-requests",Map.of("content",grants(List.of(grantLine(role,employee,department))),"reason","范围申请"),id());assertThat(r.statusCode()).as(r.body()).isEqualTo(201);String draft=body(r).path("id").stringValue();var p=scopedRequest("POST","change-requests/"+draft+"/previews",Map.of("expectedDraftVersion",1),id());assertThat(p.statusCode()).as(p.body()).isEqualTo(200);assertThat(scopedRequest("POST","change-requests/"+draft+"/submissions",Map.of("expectedDraftVersion",1,"previewId",body(p).path("preview").path("id").stringValue()),id()).statusCode()).isEqualTo(202);approveExisting(draft);return draft;
    }
    @Test void originalScopeSurvivesSubmitterAndAppointmentRevocation()throws Exception{
        String draft=scopedApprovedGrant();jdbc.update("UPDATE admin_account SET enabled=0,version=version+1,session_epoch=session_epoch+1 WHERE id=?",account);jdbc.update("UPDATE admin_appointment SET status='REVOKED',ended_at=UTC_TIMESTAMP(6),ended_reason='Fixture',appointment_version=appointment_version+1 WHERE id=?",appointment);execute(draft);assertThat(requestState(draft)).isEqualTo("EXECUTED");
    }
    @Test void originalScopeLossInvalidatesPermanentlyEvenAfterCoverageReturns()throws Exception{
        String draft=scopedApprovedGrant();jdbc.update("DELETE FROM scope_system WHERE scope_id=? AND system_id=?",scope,system);execute(draft);assertThat(requestState(draft)).isEqualTo("INVALIDATED");jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,system);execute(draft);assertThat(requestState(draft)).isEqualTo("INVALIDATED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=?",Long.class,draft)).isZero();
    }
    @Test void submissionFreezesPermissionAndEmploymentNamesInsteadOfOnlyIds()throws Exception{
        String role=effectiveRole();configureOa();String draft=submitContent(grants(List.of(grantLine(role,employee,department))));String snapshot=jdbc.queryForObject("SELECT target_snapshot FROM change_target WHERE request_id=?",String.class,draft);var fixed=json.readTree(snapshot);assertThat(fixed.path("permissions").isArray()).isTrue();assertThat(fixed.path("permissions").get(0).path("name").stringValue()).isEqualTo("Access");assertThat(fixed.path("employments").size()).isEqualTo(1);assertThat(fixed.path("employee").path("name").stringValue()).isEqualTo("Employee");
    }

    void restart(boolean scheduling,boolean demo){app.close();var args=new ArrayList<>(List.of(webArgs));args.removeIf(x->x.startsWith("--rbac.workflow.scheduling-enabled="));args.add("--rbac.workflow.scheduling-enabled="+scheduling);if(demo)args.add("--spring.profiles.active=dev,demo");app=db.startWeb(args.toArray(String[]::new));jdbc=new JdbcTemplate(app.getBean(DataSource.class));base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/admin/";}
    @Test void processRestartRecoversPersistedApprovalWithoutCallbackNotification()throws Exception{
        String draft=approved();try{restart(true,false);assertThat(requestState(draft)).isEqualTo("EXECUTED");}finally{restart(false,false);}
    }
    @Test void demoAdapterOnlyExistsInDemoProfileAndReceiptSurvivesRestart()throws Exception{
        String draft=submitted();try{restart(false,true);invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",draft);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_link WHERE request_id=?",Long.class,draft)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT oa_approval_id FROM oa_link WHERE request_id=?",String.class,draft)).isEqualTo("DEMO-"+draft);assertThat(requestState(draft)).isEqualTo("PENDING");restart(false,true);var cls=Class.forName("com.adgn.backend.demo.DemoOaSubmissionAdapter");var port=(com.adgn.backend.oa.OaSubmissionPort)app.getBean(cls);var bytes=jdbc.queryForObject("SELECT fixed_payload_bytes FROM change_request WHERE id=?",byte[].class,draft);var payload=(tools.jackson.databind.node.ObjectNode)json.readTree(bytes);String digest=HexFormat.of().formatHex(jdbc.queryForObject("SELECT content_digest FROM change_request WHERE id=?",byte[].class,draft));payload.put("contentDigest",digest);assertThat(port.send(new com.adgn.backend.oa.OaSubmissionPort.Submission(oa,draft,2,digest,payload)).oaApprovalId()).isEqualTo("DEMO-"+draft);}finally{restart(false,false);}
        assertThat(app.getBeansOfType(com.adgn.backend.oa.OaSubmissionPort.class)).isEmpty();
    }
    ProcessBuilder demoCommand(String command,String identity,Path output,Path signer){var pb=com.adgn.backend.testsupport.JavaSubprocess.command("-Dloader.main=com.adgn.backend.demo.DemoServiceApplication","-cp","target/backend-0.0.1-SNAPSHOT.jar","org.springframework.boot.loader.launch.PropertiesLauncher",command);pb.environment().keySet().removeIf(k->k.startsWith("RBAC_")||k.startsWith("SPRING_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("JDK_JAVA_OPTIONS"));db.configureBootstrap(pb);var env=pb.environment();for(String name:List.of("URL","USER","PASSWORD","CONFIRM_DATABASE")){env.put("RBAC_DEMO_"+name,env.remove("RBAC_BOOTSTRAP_"+name));}env.put("RBAC_DEMO_MODE","demo");env.put("RBAC_DEMO_SERVICE_ID",identity);env.put("RBAC_DEMO_SUBJECT",identity);env.put("RBAC_DEMO_KIND","OA");env.put("RBAC_DEMO_SIGNING_JWK",signer.toAbsolutePath().toString());env.put("RBAC_DEMO_VERIFY_JWK_SET",keys.resolve("oa.json").toAbsolutePath().toString());env.put("RBAC_DEMO_OUTPUT",output.toAbsolutePath().toString());return pb.redirectErrorStream(true);}
    record CliResult(int code,String text){}
    CliResult runCli(ProcessBuilder pb)throws Exception{Path log=Files.createTempFile(Path.of(".tmp"),"demo-cli-",".log");Process p=pb.redirectOutput(log.toFile()).start();try{assertThat(p.waitFor(30,TimeUnit.SECONDS)).isTrue();return new CliResult(p.exitValue(),Files.readString(log));}finally{if(p.isAlive())p.destroyForcibly().waitFor();Files.deleteIfExists(log);}}
    @Test void demoCliConfiguresServiceAndWritesShortTokenWithoutExposingOrOverwritingSecrets()throws Exception{
        String identity=id();Path output=keys.resolve("demo.jwt"),signer=keys.resolve("demo-private.json");Files.writeString(signer,new JWKSet(oaKey).toString(false));
        try{var configure=runCli(demoCommand("configure-demo-service",identity,output,signer));assertThat(configure.code()).as(configure.text()).isZero();assertThat(runCli(demoCommand("configure-demo-service",identity,output,signer)).code()).isNotZero();var wrong=demoCommand("issue-demo-service-token",identity,output,signer);wrong.environment().put("RBAC_DEMO_VERIFY_JWK_SET",keys.resolve("business.json").toAbsolutePath().toString());assertThat(runCli(wrong).code()).isNotZero();assertThat(Files.exists(output)).isFalse();var issue=runCli(demoCommand("issue-demo-service-token",identity,output,signer));assertThat(issue.code()).as(issue.text()).isZero();String raw=Files.readString(output).strip();var verified=app.getBean(com.adgn.backend.identity.security.IdentityTokens.class).verify(raw,com.adgn.backend.identity.security.IdentityTokens.Family.OA);assertThat(verified.getSubject()).isEqualTo(identity);assertThat(java.time.Duration.between(verified.getIssuedAt(),verified.getExpiresAt()).toSeconds()).isBetween(1L,300L);assertThat(issue.text()).doesNotContain(raw,json.readTree(oaKey.toJSONString()).path("d").stringValue());assertThat(runCli(demoCommand("issue-demo-service-token",identity,output,signer)).code()).isNotZero();assertThat(Files.readString(output).strip()).isEqualTo(raw);
        }finally{jdbc.update("UPDATE service_identity SET enabled=0,version=version+1 WHERE id=?",identity);Files.deleteIfExists(output);Files.deleteIfExists(signer);}
    }

    @Test void demoCliInvalidConfigurationCannotCreateServiceOrAppendAudit()throws Exception{
        String identity=id();long audits=jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class);
        var mutations=List.<java.util.function.Consumer<ProcessBuilder>>of(
                p->p.environment().remove("RBAC_DEMO_MODE"),
                p->p.environment().put("RBAC_DEMO_MODE","production"),
                p->p.environment().put("RBAC_DEMO_SUBJECT"," "),
                p->p.environment().put("RBAC_DEMO_SUBJECT","a".repeat(101)),
                p->p.environment().put("RBAC_DEMO_SERVICE_ID","1-1-1-1-1"),
                p->p.environment().put("RBAC_DEMO_KIND","ADMIN"),
                p->p.environment().put("RBAC_DEMO_KIND","BUSINESS"),
                p->p.environment().put("RBAC_DEMO_CONFIRM_DATABASE","mysql"),
                p->p.environment().put("RBAC_DEMO_URL",p.environment().get("RBAC_DEMO_URL")+"?user=root"),
                p->p.command().set(p.command().size()-1,"unknown-command"));
        for(var mutation:mutations){
            var process=demoCommand("configure-demo-service",identity,keys.resolve("unused.jwt"),keys.resolve("unused.json"));
            mutation.accept(process);var result=runCli(process);
            assertThat(result.code()).isEqualTo(2);
            assertThat(result.text()).contains("DEMO_COMMAND_REFUSED").doesNotContain("jdbc:","Exception");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_identity WHERE id=?",Long.class,identity)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class)).isEqualTo(audits);
    }

    @Test void businessDemoTokenUsesBoundSystemAndDisabledServiceCannotIssueAgain()throws Exception{
        String identity=id();Path signer=keys.resolve("business-private.json"),output=keys.resolve("business-demo.jwt");
        Files.writeString(signer,businessKey.toJSONString());
        java.util.function.BiFunction<String,Path,ProcessBuilder> command=(operation,destination)->{
            var process=demoCommand(operation,identity,destination,signer);var env=process.environment();
            env.put("RBAC_DEMO_KIND","BUSINESS");env.put("RBAC_DEMO_SYSTEM_ID",system);
            env.put("RBAC_DEMO_SIGNING_KEY_ID","business");env.put("RBAC_DEMO_VERIFY_JWK_SET",keys.resolve("business.json").toAbsolutePath().toString());
            return process;
        };
        Path denied=keys.resolve("disabled-business.jwt");
        try{
            var missing=command.apply("configure-demo-service",output);missing.environment().put("RBAC_DEMO_SYSTEM_ID",id());
            assertThat(runCli(missing).code()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_identity WHERE id=?",Long.class,identity)).isZero();
            assertThat(runCli(command.apply("configure-demo-service",output)).code()).isZero();
            var issued=runCli(command.apply("issue-demo-service-token",output));assertThat(issued.code()).as(issued.text()).isZero();
            String token=Files.readString(output).strip();var claims=app.getBean(com.adgn.backend.identity.security.IdentityTokens.class)
                    .verify(token,com.adgn.backend.identity.security.IdentityTokens.Family.BUSINESS);
            assertThat(claims.getSubject()).isEqualTo(identity);assertThat(claims.getClaimAsString("systemId")).isEqualTo(system);
            assertThat(claims.getAudience()).containsExactly("rbac-business");
            assertThat(issued.text()).doesNotContain(token,json.readTree(businessKey.toJSONString()).path("d").stringValue());
            jdbc.update("UPDATE service_identity SET enabled=0,version=version+1 WHERE id=?",identity);
            assertThat(runCli(command.apply("issue-demo-service-token",denied)).code()).isEqualTo(2);
            assertThat(Files.exists(denied)).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE event_type='DEMO_SERVICE_TOKEN_ISSUED' AND target_id=?",Long.class,identity)).isEqualTo(1);
        }finally{
            jdbc.update("UPDATE service_identity SET enabled=0 WHERE id=?",identity);
            Files.deleteIfExists(signer);Files.deleteIfExists(output);Files.deleteIfExists(denied);
        }
    }

    @Test void fullWorkflowTimelineIncludesSubmissionAndReliableDelivery()throws Exception{
        String draft=approved();execute(draft);var r=request("GET","change-requests/"+draft+"/events",null,token,null);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);var types=new ArrayList<String>();for(var item:body(r).path("items"))types.add(item.path("eventType").stringValue());assertThat(types).contains("SUBMITTED","DELIVERY_LINKED","OA_APPROVED","EXECUTED");
    }
    @Test void replayCannotDiscloseGloballySubmittedCrossDepartmentRoleAfterDemotion()throws Exception{
        configureOa();String role=effectiveRoleAt(otherDepartment);employ(employee,otherDepartment);String actor=account;jdbc.update("UPDATE admin_account SET is_super_admin=1,version=version+1 WHERE id=?",actor);String bearer=login(actor);var made=request("POST","change-requests",Map.of("content",grants(List.of(grantLine(role,employee,department))),"reason","跨部门角色"),bearer,id());assertThat(made.statusCode()).isEqualTo(201);String draft=body(made).path("id").stringValue();var p=request("POST","change-requests/"+draft+"/previews",Map.of("expectedDraftVersion",1),bearer,null);assertThat(p.statusCode()).isEqualTo(200);var input=Map.of("expectedDraftVersion",1,"previewId",body(p).path("preview").path("id").stringValue());String key=id(),path="change-requests/"+draft+"/submissions";assertThat(request("POST",path,input,bearer,key).statusCode()).isEqualTo(202);
        String oldAccount=account;try{account=actor;scoped();jdbc.update("UPDATE admin_account SET is_super_admin=0,version=version+1 WHERE id=?",actor);scopedToken=login(actor);failure(scopedRequest("POST",path,input,key),403,"FORBIDDEN");}finally{account=oldAccount;}
    }

    void closeSource()throws Exception{guarded(c->{var closer=app.getBean(com.adgn.backend.grant.SourceCloser.class);closer.close(c,List.of(new com.adgn.backend.grant.SourceCloser.Selection(new com.adgn.backend.grant.GrantReader.SourceKey(employee,company,department,system),0,0)),"并发来源关闭");return null;});}
    @Test void sourceCloseCommitBeforeExecutionPreventsOldEpochGrant()throws Exception{
        String role=effectiveRole(),draft=submitContent(grants(List.of(grantLine(role,employee,department))));approveExisting(draft);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var first=pool.submit(()->guarded(c->{app.getBean(com.adgn.backend.grant.SourceCloser.class).close(c,List.of(new com.adgn.backend.grant.SourceCloser.Selection(new com.adgn.backend.grant.GrantReader.SourceKey(employee,company,department,system),0,0)),"并发先关闭");entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return null;}));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();var second=pool.submit(()->{started.countDown();execute(draft);return null;});assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->second.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);assertThat(jdbc.queryForObject("SELECT source_epoch FROM authorization_source WHERE id=(SELECT source_id FROM change_target WHERE request_id=?)",Long.class,draft)).isZero();release.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);assertThat(requestState(draft)).isEqualTo("INVALIDATED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=?",Long.class,draft)).isZero();}finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void executionCommitBeforeSourceCloseLeavesOneSuccessAndNoActiveGrant()throws Exception{
        String role=effectiveRole(),draft=submitContent(grants(List.of(grantLine(role,employee,department))));approveExisting(draft);String lock="wf_"+id().replace("-","");var pool=Executors.newFixedThreadPool(2);var entered=new CountDownLatch(1);var started=new CountDownLatch(1);
        try(var connection=db.runtimeConnection();var statement=connection.createStatement()){
            statement.executeQuery("SELECT GET_LOCK('"+lock+"',5)").close();db.execute("CREATE TRIGGER workflow_execution_gate BEFORE INSERT ON execution_success FOR EACH ROW BEGIN SET @wf_gate=GET_LOCK('"+lock+"',10); SET @wf_released=RELEASE_LOCK('"+lock+"'); END");
            try{var first=pool.submit(()->{execute(draft);return null;});long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean waiting=false;while(System.nanoTime()<until){if(Long.parseLong(db.scalar("SELECT COUNT(*) FROM performance_schema.metadata_locks WHERE OBJECT_TYPE='USER LEVEL LOCK' AND OBJECT_NAME='"+lock+"' AND LOCK_STATUS='PENDING'"))>0){waiting=true;break;}Thread.sleep(20);}assertThat(waiting).as("执行已持guard并到达成功事务提交前").isTrue();
                var second=pool.submit(()->guarded(c->{started.countDown();entered.countDown();app.getBean(com.adgn.backend.grant.SourceCloser.class).close(c,List.of(new com.adgn.backend.grant.SourceCloser.Selection(new com.adgn.backend.grant.GrantReader.SourceKey(employee,company,department,system),0,0)),"执行后关闭");return null;}));assertThat(entered.await(250,TimeUnit.MILLISECONDS)).isFalse();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isZero();statement.executeQuery("SELECT RELEASE_LOCK('"+lock+"')").close();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);assertThat(requestState(draft)).isEqualTo("EXECUTED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE granted_by_request_id=? AND status='ACTIVE'",Long.class,draft)).isZero();
            }finally{statement.executeQuery("SELECT RELEASE_LOCK('"+lock+"')").close();db.execute("DROP TRIGGER workflow_execution_gate");}
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void concurrentExecutionCreatesOneSuccessAndImmutablePayloadCannotBeRewritten()throws Exception{
        String draft=approved();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{start.await();execute(draft);return null;});var b=pool.submit(()->{start.await();execute(draft);return null;});start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE change_request_id=? AND event_type='EXECUTED'",Long.class,draft)).isEqualTo(1);assertThatThrownBy(()->jdbc.update("UPDATE change_request SET fixed_payload_bytes=? WHERE id=?",new byte[]{1},draft)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->jdbc.update("DELETE FROM change_target WHERE request_id=?",draft)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void demoResultScriptUsesLiveRequestAndTrustedCallbackWithoutPrintingToken()throws Exception{
        String draft=delivered();var response=request("GET","change-requests/"+draft,null,token,null);assertThat(response.statusCode()).isEqualTo(200);Path requestFile=keys.resolve("live-request.json"),tokenFile=keys.resolve("callback.jwt");Files.writeString(requestFile,response.body());String raw=oaToken(oa);Files.writeString(tokenFile,raw);
        try{var pb=new ProcessBuilder("python3","../tools/demo_oa_result.py","--request",requestFile.toAbsolutePath().toString(),"--decision","APPROVED");pb.environment().put("RBAC_DEMO_CALLBACK_TOKEN_FILE",tokenFile.toAbsolutePath().toString());pb.environment().put("RBAC_DEMO_BASE_URL",base.substring(0,base.indexOf("/api/v1/")));var result=runCli(pb.redirectErrorStream(true));assertThat(result.code()).as(result.text()).isZero();assertThat(result.text()).contains("202","PENDING").doesNotContain(raw);assertThat(requestState(draft)).isEqualTo("APPROVED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isZero();}finally{Files.deleteIfExists(requestFile);Files.deleteIfExists(tokenFile);}
    }

    @Test void callbackAndRetryRejectWrongFieldTypesAsValidationWithoutWrites()throws Exception{
        String draft=delivered();var valid=callbackInput(draft,"APPROVED");
        for(String field:List.of("contentDigest","decision","decidedAt","comment")){var invalid=new TreeMap<>(valid);invalid.put(field,7);failure(callback(invalid),422,"VALIDATION_ERROR");}
        String unsent=submitted();invokeService("com.adgn.backend.oa.OaDeliveryService","deliver",unsent);
        failure(request("POST","change-requests/"+unsent+"/delivery-retries",Map.of("expectedStatusVersion",3,"reason",7),token,id()),422,"VALIDATION_ERROR");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oa_final_result WHERE request_id=?",Long.class,draft)).isZero();
    }
}
