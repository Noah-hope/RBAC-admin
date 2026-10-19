package com.adgn.bfixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.PasswordPolicy;
import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.role.service.RoleConfigurationApplier;
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
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BModuleMySqlCheck {
    private IsolatedMySqlDatabase database;
    private ConfigurableApplicationContext application;
    private JdbcTemplate jdbc;
    private Path keys;
    private String base, account, token;
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newHttpClient();
    @BeforeAll void start() throws Exception {
        keys=Files.createTempDirectory(Path.of(".tmp"),"b-keys-");
        for(String family:List.of("admin","business","oa")){
            var key=new RSAKeyGenerator(2048).keyID(family).generate();
            Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));
        }
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        database=new IsolatedMySqlDatabase();
        application=database.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
                "--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),
                "--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),
                "--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),
                "--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000",
                "--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture",
                "--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(secret))));
        jdbc=new JdbcTemplate(application.getBean(DataSource.class));
        base="http://127.0.0.1:"+((WebServerApplicationContext)application).getWebServer().getPort();
        account=id();String password="B-Fixture-123456";
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",account,account,new PasswordPolicy().encode(password));
        var login=request("POST","/api/v1/admin/sessions",Map.of("loginName",account,"password",password),null,null,Map.of());
        assertThat(login.statusCode()).isEqualTo(201);token=body(login).path("accessToken").stringValue();
    }
    @AfterAll void close() throws Exception {
        try{if(application!=null)application.close();}finally{
            try{if(database!=null)database.close();}finally{
                if(keys!=null){for(String family:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(family+".json"));Files.deleteIfExists(keys);}
            }
        }
    }
    private static String id(){return UUID.randomUUID().toString();}
    private JsonNode body(HttpResponse<String> response){return json.readTree(response.body());}
    private HttpResponse<String> request(String method,String path,Object input,String bearer,String key,Map<String,String> headers)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25));
        if(bearer!=null)b.header("Authorization","Bearer "+bearer);
        if(key!=null)b.header("Idempotency-Key",key);
        headers.forEach(b::header);
        if(input!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,input==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> call(String method,String path,Object input,String key)throws Exception{return request(method,path,input,token,key,Map.of());}
    private JsonNode createSystem(String code)throws Exception{
        var response=call("POST","/api/v1/admin/systems",Map.of("code",code,"name","测试系统","reason","测试登记"),id());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return body(response);
    }
    @Test void registrationCommitsOneAccessPermissionDisabledSystemAuditAndSafeReplay()throws Exception{
        String code="sys_"+id(),key=id();var input=Map.of("code",code,"name","系统","reason","登记");
        var first=call("POST","/api/v1/admin/systems",input,key);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        var value=body(first);String system=value.path("id").stringValue();
        assertThat(value.propertyNames()).containsExactlyInAnyOrder("id","code","name","description","enabled","version","catalogVersion","systemAccessPermissionId","createdAt");
        assertThat(value.path("enabled").booleanValue()).isFalse();
        assertThat(value.path("description").stringValue()).isEmpty();
        assertThat(first.headers().firstValue("Location")).contains("/api/v1/admin/systems/"+system);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permission WHERE system_id=? AND permission_type='SYSTEM_ACCESS' AND code=? AND function_id IS NULL AND menu_eligible=0",Integer.class,system,code+":access")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id=?",Integer.class,system)).isEqualTo(1);
        var replay=call("POST","/api/v1/admin/systems",input,key);
        assertThat(replay.statusCode()).isEqualTo(201);assertThat(body(replay)).isEqualTo(value);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        var duplicate=call("POST","/api/v1/admin/systems",input,id());assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_system WHERE code=?",Integer.class,code)).isEqualTo(1);
    }
    @Test void systemQueriesUseStablePagesLiteralSearchAndDetectMissingAccess()throws Exception{
        String prefix="q"+id();JsonNode a=createSystem(prefix+"_literal");createSystem(prefix+"Xother");
        var page=call("GET","/api/v1/admin/systems?q="+prefix+"_&pageSize=10",null,null);
        assertThat(page.statusCode()).isEqualTo(200);assertThat(body(page).path("total").longValue()).isEqualTo(1);
        assertThat(body(page).path("items").get(0).path("id").stringValue()).isEqualTo(a.path("id").stringValue());
        assertThat(call("GET","/api/v1/admin/systems/"+id(),null,null).statusCode()).isEqualTo(404);
        assertThat(call("GET","/api/v1/admin/systems?pageSize=11",null,null).statusCode()).isEqualTo(422);
        String broken=id();jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",broken,broken,"broken");
        try{var response=call("GET","/api/v1/admin/systems/"+broken,null,null);assertThat(response.statusCode()).isEqualTo(500);assertThat(body(response).path("code").stringValue()).isEqualTo("DATA_INTEGRITY_ERROR");}
        finally{jdbc.update("DELETE FROM business_system WHERE id=?",broken);}
    }
    private record Scoped(String token,String appointment,String scope,String company,String department,Map<String,String> headers){}
    private Scoped scoped(String system)throws Exception{
        String employee=id(),other=id(),company=id(),department=id(),position=id(),scope=id(),appointment=id();
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",employee,employee,"测试员工");
        jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",company,company,"测试公司");
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",department,company,department,"测试部门");
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",position,department,position,"测试岗位");
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",id(),employee,company,department,position);
        jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,must_change_password,created_at) VALUES(?,?,?,?,0,UTC_TIMESTAMP(6))",other,employee,other,new PasswordPolicy().encode("Scoped-Fixture-123456"));
        jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,company,department);
        jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,system);
        jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",appointment,scope,other);
        var login=request("POST","/api/v1/admin/sessions",Map.of("loginName",other,"password","Scoped-Fixture-123456"),null,null,Map.of());
        assertThat(login.statusCode()).isEqualTo(201);
        return new Scoped(body(login).path("accessToken").stringValue(),appointment,scope,company,department,Map.of("X-Admin-Appointment-Id",appointment,"X-Admin-Appointment-Version","1","X-Admin-Scope-Version","1"));
    }
    @Test void systemReadsRequireOneCurrentAppointmentAndNeverExposeOtherSystems()throws Exception{
        String visible=createSystem("visible"+id()).path("id").stringValue(),hidden=createSystem("hidden"+id()).path("id").stringValue();
        var s=scoped(visible);
        assertThat(request("GET","/api/v1/admin/systems",null,s.token(),null,Map.of()).statusCode()).isEqualTo(422);
        var list=request("GET","/api/v1/admin/systems",null,s.token(),null,s.headers());
        assertThat(list.statusCode()).isEqualTo(200);assertThat(body(list).path("total").longValue()).isEqualTo(1);
        assertThat(body(list).path("items").get(0).path("id").stringValue()).isEqualTo(visible);
        for(String target:List.of(hidden,id()))assertThat(request("GET","/api/v1/admin/systems/"+target,null,s.token(),null,s.headers()).statusCode()).isEqualTo(404);
        assertThat(request("POST","/api/v1/admin/systems",Map.of("code",id(),"name","非法","reason","拒绝"),s.token(),id(),Map.of()).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE management_scope SET scope_version=2 WHERE id=?",s.scope());
        var stale=request("GET","/api/v1/admin/systems",null,s.token(),null,s.headers());
        assertThat(stale.statusCode()).isEqualTo(409);assertThat(body(stale).path("code").stringValue()).isEqualTo("CONTEXT_CHANGED");
    }
    @Test void registrationRejectsInvalidInputAndAuditFailureRollsBackBothRows()throws Exception{
        for(var input:List.of(Map.of("code","bad space","name","名称","reason","原因"),Map.of("code","x".repeat(94),"name","名称","reason","原因"),Map.of("code",id(),"name"," ","reason","原因"),Map.of("code",id(),"name","名称","reason","原因","enabled",true))){
            assertThat(call("POST","/api/v1/admin/systems",input,id()).statusCode()).isEqualTo(422);
        }
        String code="rollback"+id();
        database.execute("CREATE TRIGGER fail_b_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected audit failure'");
        try{assertThat(call("POST","/api/v1/admin/systems",Map.of("code",code,"name","名称","reason","原因"),id()).statusCode()).isGreaterThanOrEqualTo(500);}
        finally{database.execute("DROP TRIGGER fail_b_audit");}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_system WHERE code=?",Integer.class,code)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permission WHERE code=?",Integer.class,code+":access")).isZero();
    }
    private String role(String system,String company,String department,boolean effective){
        String role=id();
        jdbc.update("INSERT INTO business_role(id,code,name,company_id,department_id,system_id,state,configuration_version,created_at) VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",role,"ROLE_"+role.replace("-",""),"原角色",company,department,system,effective?"EFFECTIVE":"UNCONFIGURED",effective?1:0);
        if(effective)jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) SELECT ?,id,system_id FROM permission WHERE system_id=? AND permission_type='SYSTEM_ACCESS'",role,system);
        return role;
    }
    private String claim(String role,String company){
        String request=id();
        jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,'ROLE_CONFIGURATION',?,'{}','测试草稿',UTC_TIMESTAMP(6))",request,company);
        jdbc.update("INSERT INTO role_change_claim(role_id,request_id) VALUES(?,?)",role,request);return request;
    }
    private Map<String,Object> metadata(long expected,String name){return Map.of("expectedVersion",expected,"name",name,"description","角色说明","reason","改名");}
    @Test void roleMetadataChangesOnlyMetadataAndDisplaysCurrentConfigurationSeparatelyFromClaim()throws Exception{
        var system=createSystem("role"+id());String sid=system.path("id").stringValue();var s=scoped(sid);
        String role=role(sid,s.company(),s.department(),true),pending=claim(role,s.company());
        var detail=call("GET","/api/v1/admin/roles/"+role,null,null);
        assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
        assertThat(body(detail).path("permissionIds").get(0).stringValue()).isEqualTo(system.path("systemAccessPermissionId").stringValue());
        assertThat(body(detail).path("activeChangeRequestId").stringValue()).isEqualTo(pending);
        String key=id();var changed=call("PUT","/api/v1/admin/roles/"+role,metadata(1," 新角色 "),key);
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);
        var value=body(changed);assertThat(value.path("name").stringValue()).isEqualTo("新角色");
        assertThat(value.path("metadataVersion").longValue()).isEqualTo(2);assertThat(value.path("configurationVersion").longValue()).isEqualTo(1);
        assertThat(value.path("permissionIds")).isEqualTo(body(detail).path("permissionIds"));
        for(String field:List.of("id","code","companyId","departmentId","systemId","activeChangeRequestId","state"))assertThat(value.path(field)).isEqualTo(body(detail).path(field));
        assertThat(body(call("PUT","/api/v1/admin/roles/"+role,metadata(1," 新角色 "),key))).isEqualTo(value);
        assertThat(call("PUT","/api/v1/admin/roles/"+role,metadata(1,"过期"),id()).statusCode()).isEqualTo(409);
        assertThat(body(call("PUT","/api/v1/admin/roles/"+role,metadata(2,"新角色"),id())).path("metadataVersion").longValue()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id=?",Integer.class,role)).isEqualTo(1);
        var forbiddenField=new HashMap<>(metadata(2,"新角色"));forbiddenField.put("companyId",id());
        assertThat(call("PUT","/api/v1/admin/roles/"+role,forbiddenField,id()).statusCode()).isEqualTo(422);
    }
    @Test void scopedRoleQueriesFilterBeforeCountingAndSharedRolesAreReadOnly()throws Exception{
        String system=createSystem("scope-role"+id()).path("id").stringValue();var s=scoped(system);
        String own=role(system,s.company(),s.department(),false),shared=role(system,s.company(),null,false);
        String otherDepartment=id();jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",otherDepartment,s.company(),otherDepartment,"其他部门");
        String hidden=role(system,s.company(),otherDepartment,false);
        var list=request("GET","/api/v1/admin/roles?systemId="+system,null,s.token(),null,s.headers());
        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);assertThat(body(list).path("total").longValue()).isEqualTo(2);
        var ids=new ArrayList<String>();for(var item:body(list).path("items"))ids.add(item.path("id").stringValue());assertThat(ids).containsExactlyInAnyOrder(own,shared);
        for(String r:List.of(hidden,id()))assertThat(request("GET","/api/v1/admin/roles/"+r,null,s.token(),null,s.headers()).statusCode()).isEqualTo(404);
        assertThat(request("GET","/api/v1/admin/roles?departmentId="+otherDepartment,null,s.token(),null,s.headers()).statusCode()).isEqualTo(403);
        assertThat(request("GET","/api/v1/admin/roles?systemId="+id(),null,s.token(),null,s.headers()).statusCode()).isEqualTo(403);
        assertThat(request("PUT","/api/v1/admin/roles/"+shared,metadata(1,"共享"),s.token(),id(),s.headers()).statusCode()).isEqualTo(403);
        assertThat(request("PUT","/api/v1/admin/roles/"+own,metadata(1,"本部门"),s.token(),id(),s.headers()).statusCode()).isEqualTo(200);
        assertThat(call("GET","/api/v1/admin/roles?state=DISABLED",null,null).statusCode()).isEqualTo(422);
    }
    @Test void metadataReplayRechecksCurrentScopeAndFailureRollsBackMetadata()throws Exception{
        String system=createSystem("replay-role"+id()).path("id").stringValue();var s=scoped(system);
        String role=role(system,s.company(),s.department(),false),key=id();
        assertThat(request("PUT","/api/v1/admin/roles/"+role,metadata(1,"变更"),s.token(),key,s.headers()).statusCode()).isEqualTo(200);
        jdbc.update("DELETE FROM scope_system WHERE scope_id=? AND system_id=?",s.scope(),system);
        assertThat(request("PUT","/api/v1/admin/roles/"+role,metadata(1,"变更"),s.token(),key,s.headers()).statusCode()).isEqualTo(404);
        database.execute("CREATE TRIGGER fail_b_role_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected audit failure'");
        try{assertThat(call("PUT","/api/v1/admin/roles/"+role,metadata(2,"不应提交"),id()).statusCode()).isGreaterThanOrEqualTo(500);}
        finally{database.execute("DROP TRIGGER fail_b_role_audit");}
        var detail=call("GET","/api/v1/admin/roles/"+role,null,null);
        assertThat(body(detail).path("name").stringValue()).isEqualTo("变更");assertThat(body(detail).path("metadataVersion").longValue()).isEqualTo(2);
    }
    private String function(String system,String parent,boolean enabled){
        String node=id();jdbc.update("INSERT INTO directory_node(id,system_id,code,name,node_type,parent_id,enabled,created_at) VALUES(?,?,?,?,'FUNCTION',?,0,UTC_TIMESTAMP(6))",node,system,node,"测试功能",parent);
        if(enabled){String visible=permission(system,node,true);jdbc.update("UPDATE directory_node SET enabled=1,visibility_permission_id=? WHERE id=?",visible,node);}
        return node;
    }
    private String permission(String system,String function,boolean enabled){
        String p=id();jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,function_id,menu_eligible,enabled,created_at) VALUES(?,?,?,?,'FUNCTION_OPERATION',?,1,?,UTC_TIMESTAMP(6))",p,system,p,p,function,enabled);return p;
    }
    private String approved(String role,String company,String system,String department,long expected){
        String request=claim(role,company),oa=id(),event=id();
        jdbc.update("INSERT INTO change_target(request_id,line_no,line_kind,company_id,system_id,department_id,role_id,expected_configuration_version,target_snapshot) VALUES(?,0,'ROLE_CONFIGURATION',?,?,?,?,?,'{}')",request,company,system,department,role,expected);
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=1,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type='GLOBAL',original_authority='{}',fixed_payload_bytes=X'7B7D',content_digest=UNHEX(REPEAT('00',32)) WHERE id=?",account,request);
        jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oa,oa);
        jdbc.update("INSERT INTO oa_link(request_id,oa_instance_id,oa_approval_id,accepted_at,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",request,oa,id());
        jdbc.update("INSERT INTO oa_callback_event(id,oa_instance_id,event_id,request_id,payload_digest,safe_payload,received_at) VALUES(?,?,?,?,UNHEX(REPEAT('00',32)),'{}',UTC_TIMESTAMP(6))",event,oa,id(),request);
        jdbc.update("INSERT INTO oa_final_result(request_id,first_event_id,decision,decided_at,comment,received_at) VALUES(?,?,'APPROVED',UTC_TIMESTAMP(6),'测试批准',UTC_TIMESTAMP(6))",request,event);
        jdbc.update("UPDATE change_request SET state='APPROVED',delivery_status='LINKED',approval_result='APPROVED',execution_status='PENDING' WHERE id=?",request);
        return request;
    }
    private Object apply(WriteContext<?> context,String request,String role,long version,List<String> permissions)throws Exception{
        return application.getBean(RoleConfigurationApplier.class).apply(context,request,role,version,permissions);
    }
    private Object executeApply(String request,String role,long version,List<String> permissions)throws Exception{
        return application.getBean(CommandBoundary.class).execute(()->new CommandBoundary.Identity<>("EXECUTION_FIXTURE",new AuditRecord.Actor(AuditRecord.ActorKind.SERVICE,"fixture-executor")),context->apply(context,request,role,version,permissions));
    }
    private List<String> permissions(String role){return jdbc.queryForList("SELECT permission_id FROM role_permission WHERE role_id=? ORDER BY permission_id",String.class,role);}
    @Test void configurationAppliesCompleteDifferenceRetainsDisabledItemsAndRecordsHistory()throws Exception{
        var system=createSystem("config"+id());String sid=system.path("id").stringValue(),access=system.path("systemAccessPermissionId").stringValue();var s=scoped(sid);
        jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        String role=role(sid,s.company(),s.department(),true),fn=function(sid,null,true),disabled=permission(sid,fn,false),added=permission(sid,fn,true);
        jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",role,disabled,sid);
        String request=approved(role,s.company(),sid,s.department(),1);
        executeApply(request,role,1,List.of(access,disabled,added));
        assertThat(permissions(role)).containsExactlyInAnyOrder(access,disabled,added);
        assertThat(jdbc.queryForObject("SELECT configuration_version FROM business_role WHERE id=?",Long.class,role)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT metadata_version FROM business_role WHERE id=?",Long.class,role)).isEqualTo(1);
        String snapshot=jdbc.queryForObject("SELECT configuration_snapshot FROM role_configuration_history WHERE request_id=?",String.class,request);
        assertThat(json.readTree(snapshot).path("schemaVersion").intValue()).isEqualTo(1);
        assertThat(snapshot).contains(access,disabled,added,"FUNCTION_OPERATION","SYSTEM_ACCESS");
        assertThat(jdbc.queryForObject("SELECT state FROM change_request WHERE id=?",String.class,request)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Integer.class,request)).isZero();
    }
    @Test void firstConfigurationCanContainOnlyAnOperationAndNeverAddsSystemAccess()throws Exception{
        String sid=createSystem("first"+id()).path("id").stringValue();var s=scoped(sid);jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        String role=role(sid,s.company(),null,false),fn=function(sid,null,true),operation=permission(sid,fn,true),request=approved(role,s.company(),sid,null,0);
        executeApply(request,role,0,List.of(operation));
        assertThat(permissions(role)).containsExactly(operation);
        assertThat(jdbc.queryForObject("SELECT state FROM business_role WHERE id=?",String.class,role)).isEqualTo("EFFECTIVE");
        assertThat(jdbc.queryForObject("SELECT configuration_version FROM business_role WHERE id=?",Long.class,role)).isEqualTo(1);
    }
    @Test void invalidConfigurationNeverPartiallyReplacesPermissions()throws Exception{
        String sid=createSystem("invalid-config"+id()).path("id").stringValue();var s=scoped(sid);jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        String role=role(sid,s.company(),s.department(),true),fn=function(sid,null,true),disabled=permission(sid,fn,false),operation=permission(sid,fn,true),request=approved(role,s.company(),sid,s.department(),1);
        List<String> before=permissions(role);String foreign=createSystem("foreign"+id()).path("systemAccessPermissionId").stringValue();
        for(List<String> invalid:List.of(List.<String>of(),List.of(operation,operation),List.of(id()),List.of(operation,disabled),List.of(foreign))){
            assertThatThrownBy(()->executeApply(request,role,1,invalid)).isInstanceOf(RoleConfigurationApplier.Rejection.class);
            assertThat(permissions(role)).isEqualTo(before);
        }
        jdbc.update("UPDATE directory_node SET enabled=0 WHERE id=?",fn);
        assertThatThrownBy(()->executeApply(request,role,1,List.of(operation))).isInstanceOf(RoleConfigurationApplier.Rejection.class);
        assertThatThrownBy(()->executeApply(request,role,0,before)).isInstanceOf(RoleConfigurationApplier.Rejection.class);
        assertThat(permissions(role)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_configuration_history WHERE role_id=?",Integer.class,role)).isZero();
    }
    @Test void historyFailureRollsBackRelationsVersionsAndOuterWritesEvenIfCallerCatches()throws Exception{
        String sid=createSystem("atomic-config"+id()).path("id").stringValue();var s=scoped(sid);jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        String role=role(sid,s.company(),s.department(),true),fn=function(sid,null,true),operation=permission(sid,fn,true),request=approved(role,s.company(),sid,s.department(),1);List<String> before=permissions(role);
        database.execute("CREATE TRIGGER fail_b_history BEFORE INSERT ON role_configuration_history FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected history failure'");
        try{
            assertThatThrownBy(()->application.getBean(CommandBoundary.class).execute(()->new CommandBoundary.Identity<>("EXECUTION_FIXTURE",new AuditRecord.Actor(AuditRecord.ActorKind.SERVICE,"fixture-executor")),context->{
                jdbc.update("UPDATE business_role SET description='outer write' WHERE id=?",role);
                try{apply(context,request,role,1,List.of(operation));}catch(RuntimeException ignored){}
                return null;
            })).isInstanceOf(RuntimeException.class);
        }finally{database.execute("DROP TRIGGER fail_b_history");}
        assertThat(permissions(role)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT configuration_version FROM business_role WHERE id=?",Long.class,role)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT description FROM business_role WHERE id=?",String.class,role)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_configuration_history WHERE role_id=?",Integer.class,role)).isZero();
    }
    @Test void invisibleRoleWithDamagedConfigurationStillReturnsNotFound()throws Exception{
        String sid=createSystem("private-role"+id()).path("id").stringValue();var s=scoped(sid);
        String other=id();jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",other,s.company(),other,"其他部门");
        String hidden=role(sid,s.company(),other,false);
        jdbc.update("UPDATE business_role SET state='EFFECTIVE',configuration_version=1 WHERE id=?",hidden);
        var response=request("GET","/api/v1/admin/roles/"+hidden,null,s.token(),null,s.headers());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
    }
    @Test void foreignAppointmentIsRejectedConsistentlyByPeopleSystemAndRoleEndpoints()throws Exception{
        String sid=createSystem("context-integration"+id()).path("id").stringValue();
        var owner=scoped(sid);var other=scoped(sid);
        String role=role(sid,owner.company(),owner.department(),false);
        for(String path:List.of("/api/v1/admin/companies","/api/v1/admin/systems","/api/v1/admin/roles/"+role)){
            var response=request("GET",path,null,owner.token(),null,other.headers());
            assertThat(response.statusCode()).as(path+": "+response.body()).isEqualTo(403);
            assertThat(body(response).path("code").stringValue()).isEqualTo("FORBIDDEN");
        }
    }
}
