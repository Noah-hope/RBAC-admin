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
class AuditRequestQueriesMySqlCheck {
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
        keys=Files.createTempDirectory(Path.of(".tmp"),"be19-22-keys-");
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

    String scopedToken,appointment,scope;
    void scoped()throws Exception{
        scope=id();appointment=id();jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,company,department);
        jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,system);
        jdbc.update("INSERT INTO admin_appointment(id,account_id,scope_id,status,created_at) VALUES(?,?,?,'ACTIVE',UTC_TIMESTAMP(6))",appointment,account,scope);scopedToken=login(account);
    }
    HttpResponse<String> scopedGet(String path)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+scopedToken)
          .header("X-Admin-Appointment-Id",appointment).header("X-Admin-Appointment-Version","1").header("X-Admin-Scope-Version","1");
        return http.send(b.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    String audit(String visibility,String dept,String request,String type,Object snapshot){
        String id=id();jdbc.update("INSERT INTO audit_event(id,occurred_at,event_type,actor_kind,actor_id,target_id,change_request_id,company_id,source_department_id,system_id,visibility,result,correlation_id,reason,event_snapshot) VALUES(?,UTC_TIMESTAMP(6),?,'ACCOUNT',?,?,?,?,?, ?,?,'SUCCEEDED',?,'记录原因',?)",id,type,superAccount,request==null?employee:request,request,company,dept,system,visibility,id(),json.writeValueAsString(snapshot));
        jdbc.update("INSERT INTO audit_subject(audit_event_id,subject_type,subject_id) VALUES(?,'EMPLOYEE',?)",id,employee);return id;
    }
    String event(String dept,String request){return audit("SCOPED",dept,request,"SUBMITTED",Map.of("schemaVersion",1));}
    @Test void auditListFiltersBeforeCountingAndDetailMatchesCurrentScope()throws Exception{
        String own=event(department,null),foreign=event(otherDepartment,null),global=audit("GLOBAL",null,null,"IMPORT_PRECHECKED",Map.of("schemaVersion",1));scoped();
        var list=scopedGet("audit-events?companyId="+company);assertThat(list.statusCode()).as(list.body()).isEqualTo(200);assertThat(body(list).path("total").longValue()).isEqualTo(1);assertThat(body(list).path("items").get(0).path("id").stringValue()).isEqualTo(own);
        assertThat(list.headers().firstValue("Cache-Control")).contains("no-store");assertThat(scopedGet("audit-events/"+own).statusCode()).isEqualTo(200);
        failure(scopedGet("audit-events/"+foreign),404,"NOT_FOUND");failure(scopedGet("audit-events/"+global),404,"NOT_FOUND");failure(scopedGet("audit-events/"+id()),404,"NOT_FOUND");
        failure(scopedGet("audit-events?sourceDepartmentId="+otherDepartment),403,"FORBIDDEN");
        var all=request("GET","audit-events?companyId="+company,null,token,null);assertThat(all.statusCode()).as(all.body()).isEqualTo(200);assertThat(body(all).path("total").longValue()).isEqualTo(3);
    }
    @Test void draftGetListAndTimelineUseFullCurrentRequestVisibility()throws Exception{
        var made=createRole();String draft=made.path("draftId").stringValue();scoped();
        var detail=scopedGet("change-requests/"+draft);assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);assertThat(body(detail).path("state").stringValue()).isEqualTo("DRAFT");
        var list=scopedGet("change-requests?companyId="+company);assertThat(list.statusCode()).as(list.body()).isEqualTo(200);assertThat(body(list).path("total").longValue()).isEqualTo(1);
        var timeline=scopedGet("change-requests/"+draft+"/events");assertThat(timeline.statusCode()).as(timeline.body()).isEqualTo(200);assertThat(body(timeline).path("items").get(0).path("eventType").stringValue()).isEqualTo("DRAFT_SAVED");
        jdbc.update("DELETE FROM scope_system WHERE scope_id=?",scope);
        failure(scopedGet("change-requests/"+draft),404,"NOT_FOUND");failure(scopedGet("change-requests/"+draft+"/events"),404,"NOT_FOUND");
        var hidden=scopedGet("change-requests?companyId="+company);assertThat(hidden.statusCode()).isEqualTo(200);assertThat(body(hidden).path("total").longValue()).isZero();
    }
    @Test void auditDetailProjectsHistoricalDifferencesWithoutExposingRawSecrets()throws Exception{
        String a=audit("SCOPED",department,null,"ROLE_METADATA_UPDATED",Map.of("schemaVersion",1,"before",Map.of("name","旧名称","passwordHash","never-show"),"after",Map.of("name","新名称","accessToken","never-show-token"),"submittedVersion",3,"oaApprovalId","OA-123"));
        var r=request("GET","audit-events/"+a,null,token,null);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(body(r).propertyNames()).containsExactlyInAnyOrder("event","differences","originalSubmitterId","oaApprovalId","submittedVersion","failureCode");
        assertThat(r.body()).contains("旧名称","新名称").doesNotContain("never-show","passwordHash","accessToken","event_snapshot");
        assertThat(body(r).path("submittedVersion").longValue()).isEqualTo(3);assertThat(body(r).path("oaApprovalId").stringValue()).isEqualTo("OA-123");
    }
    @Test void readsDoNotAppendAuditOrChangeDraftAndUnauthenticatedIsRejected()throws Exception{
        var made=createRole();String draft=made.path("draftId").stringValue();long audit=count("audit_event"),claims=count("role_change_claim");
        for(String p:List.of("audit-events?companyId="+company,"change-requests?companyId="+company,"change-requests/"+draft,"change-requests/"+draft+"/events")){
            failure(request("GET",p,null,null,null),401,"AUTHENTICATION_FAILED");assertThat(request("GET",p,null,token,null).statusCode()).isEqualTo(200);
        }
        assertThat(count("audit_event")).isEqualTo(audit);assertThat(count("role_change_claim")).isEqualTo(claims);assertThat(jdbc.queryForObject("SELECT draft_version FROM change_request WHERE id=?",Long.class,draft)).isEqualTo(1);
    }

    String effectiveRole(String dept,String sid){
        String role=id(),permission=id();jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,menu_eligible,created_at) VALUES(?,?,?,'Access','SYSTEM_ACCESS',0,UTC_TIMESTAMP(6))",permission,sid,"SYSTEM_ACCESS");
        jdbc.update("INSERT INTO business_role(id,code,name,company_id,department_id,system_id,state,configuration_version,created_at) VALUES(?,?,'Effective',?,?,?,'EFFECTIVE',1,UTC_TIMESTAMP(6))",role,"ROLE_"+role.replace("-",""),company,dept,sid);
        jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",role,permission,sid);return role;
    }
    Map<String,Object> grantLine(String role,String dept){return Map.of("employeeId",employee,"roleId",role,"sourceCompanyId",company,"sourceDepartmentId",dept,"expectedRoleConfigurationVersion",1,"expectedSourceEpoch",0,"expectedRelationVersion",0,"employmentIds",jdbc.queryForList("SELECT id FROM employment WHERE employee_id=? AND department_id=? AND status='ACTIVE' ORDER BY id",String.class,employee,dept));}
    String mixedDraft()throws Exception{
        String role=effectiveRole(department,system);employ(employee,otherDepartment);
        var r=create(Map.of("kind","ROLE_GRANT","companyId",company,"items",List.of(grantLine(role,department),grantLine(role,otherDepartment))));assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return body(r).path("id").stringValue();
    }
    @Test void partialMixedRequestIsAbsentFromListDetailsAuditAndTimeline()throws Exception{
        String mixed=mixedDraft();String a=event(department,mixed);scoped();
        var list=scopedGet("change-requests?q="+mixed);assertThat(list.statusCode()).as(list.body()).isEqualTo(200);assertThat(body(list).path("total").longValue()).isZero();
        failure(scopedGet("change-requests/"+mixed),404,"NOT_FOUND");failure(scopedGet("change-requests/"+mixed+"/events"),404,"NOT_FOUND");failure(scopedGet("audit-events/"+a),404,"NOT_FOUND");
        failure(scopedGet("audit-events?changeRequestId="+mixed),403,"FORBIDDEN");
        var visible=request("GET","change-requests/"+mixed,null,token,null);assertThat(visible.statusCode()).as(visible.body()).isEqualTo(200);
    }
    @Test void deletedDraftRetainsReadableHistoricalAuditUsingFixedSource()throws Exception{
        var made=createRole();String draft=made.path("draftId").stringValue();String event=jdbc.queryForObject("SELECT id FROM audit_event WHERE target_id=?",String.class,draft);scoped();
        assertThat(request("DELETE","change-requests/"+draft+"?expectedDraftVersion=1",null,token,id()).statusCode()).isEqualTo(204);
        assertThat(scopedGet("audit-events/"+event).statusCode()).isEqualTo(200);failure(scopedGet("change-requests/"+draft),404,"NOT_FOUND");
        var list=scopedGet("audit-events?changeRequestId="+draft);assertThat(list.statusCode()).as(list.body()).isEqualTo(200);assertThat(body(list).path("total").longValue()).isEqualTo(2);
    }
    Map<String,Object> globalAuthority(){var m=new TreeMap<String,Object>();m.put("kind","GLOBAL");m.put("scopeId",null);m.put("scopeVersion",null);m.put("appointmentId",null);m.put("appointmentVersion",null);m.put("companyId",company);m.put("departmentId",null);m.put("systemIds",List.of());return m;}
    void pending(String draft){
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=draft_version,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type='GLOBAL',original_authority=?,fixed_payload_bytes=?,content_digest=UNHEX(SHA2('fixture',256)) WHERE id=?",superAccount,json.writeValueAsString(globalAuthority()),new byte[]{1},draft);
    }
    @Test void fixedReadPreservesAuthorityDigestAndSurvivesOriginalSubmitterLeaving()throws Exception{
        var made=createRole();String draft=made.path("draftId").stringValue();pending(draft);scoped();jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",superAccount);
        try{
            var r=scopedGet("change-requests/"+draft);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
            assertThat(body(r).path("state").stringValue()).isEqualTo("PENDING");assertThat(body(r).path("deliveryStatus").stringValue()).isEqualTo("PENDING_SEND");assertThat(body(r).path("approvalResult").stringValue()).isEqualTo("NONE");assertThat(body(r).path("executionStatus").stringValue()).isEqualTo("NOT_STARTED");
            assertThat(body(r).path("contentDigest").stringValue()).matches("[a-f0-9]{64}");assertThat(body(r).path("originalAuthority").path("kind").stringValue()).isEqualTo("GLOBAL");assertThat(body(r).path("submittedByAccountId").stringValue()).isEqualTo(superAccount);assertThat(body(r).path("retryable").booleanValue()).isFalse();
        }finally{jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",superAccount);}
    }
    @Test void eventVisibilityAndOrderingNeverDiscloseGlobalEventToScopedAdmin()throws Exception{
        var made=createRole();String draft=made.path("draftId").stringValue();String a=event(department,draft),b=audit("GLOBAL",null,draft,"OA_APPROVED",Map.of("schemaVersion",1));scoped();
        var r=scopedGet("change-requests/"+draft+"/events");assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(body(r).path("total").longValue()).isEqualTo(2);
        assertThat(r.body()).doesNotContain(b);var all=request("GET","change-requests/"+draft+"/events",null,token,null);assertThat(body(all).path("total").longValue()).isEqualTo(3);
    }
    @Test void invalidPaginationTimeIntervalAndFiltersAreRejected()throws Exception{
        scoped();for(String query:List.of("page=0","pageSize=7","from=invalid","from=2026-10-20T00:00:00","from=2026-10-21T00:00:00Z&to=2026-10-20T00:00:00Z"))failure(scopedGet("audit-events?"+query),422,"VALIDATION_ERROR");
        failure(scopedGet("change-requests?state=NOT_A_STATE"),422,"VALIDATION_ERROR");failure(scopedGet("change-requests?systemId="+id()),403,"FORBIDDEN");
    }

    @Test void subjectLinkedMixedRequestCannotBypassWholeRequestAuditVisibility()throws Exception{
        String mixed=mixedDraft(),a=event(department,null);jdbc.update("INSERT INTO audit_subject(audit_event_id,subject_type,subject_id) VALUES(?,'REQUEST',?)",a,mixed);scoped();
        failure(scopedGet("audit-events/"+a),404,"NOT_FOUND");
        var list=scopedGet("audit-events?eventType=SUBMITTED&companyId="+company);assertThat(list.statusCode()).isEqualTo(200);assertThat(body(list).path("total").longValue()).isZero();
    }
    @Test void utcTimeWindowIsInclusiveAtStartExclusiveAtEndAndAcceptsOffsets()throws Exception{
        String a=id();jdbc.update("INSERT INTO audit_event(id,occurred_at,event_type,actor_kind,actor_id,target_id,company_id,source_department_id,system_id,visibility,result,correlation_id,reason,event_snapshot) VALUES(?,'2026-10-20 00:00:00','TIME_FIXTURE','ACCOUNT',?,?,?,?,?,'SCOPED','SUCCEEDED',?,'time','{\"schemaVersion\":1}')",a,superAccount,employee,company,department,system,id());
        String filter="audit-events?companyId="+company+"&eventType=TIME_FIXTURE";
        var include=request("GET",filter+"&from=2026-10-20T00:00:00Z&to=2026-10-20T00:00:01Z",null,token,null);assertThat(include.statusCode()).as(include.body()).isEqualTo(200);assertThat(body(include).path("total").longValue()).isEqualTo(1);
        var exclude=request("GET",filter+"&from=2026-10-19T23:59:59Z&to=2026-10-20T00:00:00Z",null,token,null);assertThat(body(exclude).path("total").longValue()).isZero();
        var offset=request("GET",filter+"&from=2026-10-20T08:00:00%2B08:00&to=2026-10-20T08:00:01%2B08:00",null,token,null);assertThat(body(offset).path("total").longValue()).isEqualTo(1);
    }

    @Test void missingTargetProjectionCannotMakeMixedContentVisible()throws Exception{
        String draft=mixedDraft();jdbc.update("DELETE FROM change_target WHERE request_id=? AND department_id=?",draft,otherDepartment);scoped();
        failure(scopedGet("change-requests/"+draft),404,"NOT_FOUND");
        failure(request("GET","change-requests/"+draft,null,token,null),500,"DATA_INTEGRITY_ERROR");
    }
    void linked(String draft,String decision){
        String oa=id(),event=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oa,oa);
        jdbc.update("INSERT INTO oa_link(request_id,oa_instance_id,oa_approval_id,accepted_at,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",draft,oa,"OA-"+draft);
        jdbc.update("INSERT INTO oa_callback_event(id,oa_instance_id,event_id,request_id,payload_digest,safe_payload,received_at) VALUES(?,?,?,?,UNHEX(REPEAT('00',32)),'{}',UTC_TIMESTAMP(6))",event,oa,id(),draft);
        jdbc.update("INSERT INTO oa_final_result(request_id,first_event_id,decision,decided_at,comment,received_at) VALUES(?,?,?,UTC_TIMESTAMP(6),'Fixture',UTC_TIMESTAMP(6))",draft,event,decision);
        jdbc.update("UPDATE change_request SET state=?,delivery_status='LINKED',approval_result=?,execution_status=? WHERE id=?",decision.equals("APPROVED")?"APPROVED":"REJECTED",decision,decision.equals("APPROVED")?"PENDING":"NOT_STARTED",draft);
    }
    @Test void allSevenStateProjectionsKeepApprovalDeliveryAndExecutionDistinct()throws Exception{
        for(String state:List.of("DRAFT","PENDING","REJECTED","APPROVED","EXECUTION_FAILED","EXECUTED","INVALIDATED")){
            var made=createRole();String draft=made.path("draftId").stringValue();if(!state.equals("DRAFT"))pending(draft);
            if(Set.of("REJECTED","APPROVED","EXECUTION_FAILED","EXECUTED").contains(state))linked(draft,state.equals("REJECTED")?"REJECTED":"APPROVED");
            if(state.equals("EXECUTION_FAILED"))jdbc.update("UPDATE change_request SET state='EXECUTION_FAILED',execution_status='FAILED',failure_code='EXECUTION_STORAGE_FAILURE',next_retry_at=UTC_TIMESTAMP(6)+INTERVAL 1 MINUTE,status_version=5 WHERE id=?",draft);
            if(state.equals("EXECUTED")){String a=audit("GLOBAL",null,draft,"EXECUTED",Map.of("schemaVersion",1));jdbc.update("INSERT INTO execution_success(request_id,audit_event_id,executed_at,result_snapshot) VALUES(?,?,UTC_TIMESTAMP(6),'{}')",draft,a);jdbc.update("UPDATE change_request SET state='EXECUTED',execution_status='SUCCEEDED',status_version=6 WHERE id=?",draft);}
            if(state.equals("INVALIDATED"))jdbc.update("UPDATE change_request SET state='INVALIDATED',execution_status='INVALIDATED',failure_code='SOURCE_EPOCH_CHANGED',status_version=4 WHERE id=?",draft);
            var r=request("GET","change-requests/"+draft,null,token,null);assertThat(r.statusCode()).as(state+": "+r.body()).isEqualTo(200);var b=body(r);assertThat(b.path("state").stringValue()).isEqualTo(state);assertThat(b.path("retryable").booleanValue()).isEqualTo(Set.of("APPROVED","EXECUTION_FAILED").contains(state));assertThat(b.path("draftVersion").longValue()).isEqualTo(1);
            assertThat(b.propertyNames()).containsExactlyInAnyOrder("id","content","reason","draftVersion","statusVersion","submittedVersion","contentDigest","state","deliveryStatus","approvalResult","executionStatus","originalAuthority","submittedByAccountId","oaApprovalId","retryable","failureCode","nextRetryAt","createdAt","submittedAt");
            if(state.equals("EXECUTION_FAILED")){assertThat(b.path("approvalResult").stringValue()).isEqualTo("APPROVED");assertThat(b.path("statusVersion").longValue()).isEqualTo(5);assertThat(b.path("failureCode").stringValue()).isEqualTo("EXECUTION_STORAGE_FAILURE");}
        }
    }
    @Test void listPagingAndExactFiltersHaveStableTotalsAndNoDuplicates()throws Exception{
        for(int i=0;i<12;i++)event(department,null);scoped();String query="audit-events?companyId="+company+"&pageSize=10";
        var one=body(scopedGet(query+"&page=1"));var two=body(scopedGet(query+"&page=2"));assertThat(one.path("total").longValue()).isEqualTo(12);assertThat(two.path("items").size()).isEqualTo(2);
        Set<String> ids=new HashSet<>();for(var item:one.path("items"))assertThat(ids.add(item.path("id").stringValue())).isTrue();for(var item:two.path("items"))assertThat(ids.add(item.path("id").stringValue())).isTrue();
        assertThat(body(scopedGet(query+"&employeeId="+employee)).path("total").longValue()).isEqualTo(12);
        failure(scopedGet("audit-events?page=1&page=2"),422,"VALIDATION_ERROR");
    }

    @Test void sharedRoleConfigurationIsGlobalButDepartmentGrantRemainsVisible()throws Exception{
        var input=roleInput();input.put("departmentId",null);var shared=request("POST","roles",input,token,id());assertThat(shared.statusCode()).as(shared.body()).isEqualTo(201);String config=body(shared).path("draftId").stringValue();
        String sid=id();jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,'Shared system',UTC_TIMESTAMP(6))",sid,sid);String role=effectiveRole(null,sid);
        var r=create(Map.of("kind","ROLE_GRANT","companyId",company,"items",List.of(grantLine(role,department))));assertThat(r.statusCode()).as(r.body()).isEqualTo(201);String grant=body(r).path("id").stringValue();scoped();jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,sid);
        failure(scopedGet("change-requests/"+config),404,"NOT_FOUND");assertThat(scopedGet("change-requests/"+grant).statusCode()).isEqualTo(200);
    }
    @Test void brokenStorageReturnsAnErrorInsteadOfAnEmptyAuditList()throws Exception{
        db.execute("RENAME TABLE audit_event TO be19_unavailable_audit");
        try{var r=request("GET","audit-events",null,token,null);failure(r,500,"DATA_INTEGRITY_ERROR");assertThat(r.body()).doesNotContain("SELECT","PreparedStatement","SQLException","be19_unavailable_audit");}
        finally{db.execute("RENAME TABLE be19_unavailable_audit TO audit_event");}
    }
    @Test void corruptedFixedAuthorityAndUnknownFailureTextAreNeverDisclosed()throws Exception{
        var made=createRole();String draft=made.path("draftId").stringValue();pending(draft);
        // 固定触发器禁止篡改原资格；在首次提交前准备损坏数据应由读取端失败关闭。
        var other=createRole();String bad=other.path("draftId").stringValue();
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=1,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type='GLOBAL',original_authority='{\"accessToken\":\"never-disclose\"}',fixed_payload_bytes=?,content_digest=UNHEX(SHA2('fixture',256)) WHERE id=?",superAccount,new byte[]{1},bad);
        var r=request("GET","change-requests/"+bad,null,token,null);failure(r,500,"DATA_INTEGRITY_ERROR");assertThat(r.body()).doesNotContain("never-disclose","accessToken");
        jdbc.update("UPDATE change_request SET state='INVALIDATED',execution_status='INVALIDATED',failure_code='SELECT_PASSWORD_FROM_INTERNAL_SQL' WHERE id=?",draft);
        r=request("GET","change-requests/"+draft,null,token,null);failure(r,500,"DATA_INTEGRITY_ERROR");assertThat(r.body()).doesNotContain("SELECT_PASSWORD");
    }
    @Test void eventSummaryBoundsLongPublicValuesAndNeverExpandsUnknownFields()throws Exception{
        String a=audit("SCOPED",department,null,"ROLE_METADATA_UPDATED",Map.of("schemaVersion",1,"before",Map.of("name","old"),"after",Map.of("name","测".repeat(2100),"phoneNumber","never-phone","privateKey","never-key")));
        var r=request("GET","audit-events/"+a,null,token,null);assertThat(r.statusCode()).isEqualTo(200);var differences=body(r).path("differences");assertThat(differences.size()).isEqualTo(1);
        String value=differences.get(0).path("after").stringValue();assertThat(value.codePointCount(0,value.length())).isLessThanOrEqualTo(2000);assertThat(value).contains("摘要已截断");assertThat(r.body()).doesNotContain("never-phone","never-key");
    }

    @Test void draftAuditDisplaysRoleNameFromNestedSnapshotAfterCurrentRename()throws Exception{
        var made=createRole();String role=made.path("role").path("id").stringValue(),draft=made.path("draftId").stringValue();String a=jdbc.queryForObject("SELECT id FROM audit_event WHERE target_id=?",String.class,draft);
        jdbc.update("UPDATE business_role SET name='现在的新名称' WHERE id=?",role);
        var r=request("GET","audit-events/"+a,null,token,null);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(r.body()).contains("草稿角色").doesNotContain("现在的新名称");
    }
}
