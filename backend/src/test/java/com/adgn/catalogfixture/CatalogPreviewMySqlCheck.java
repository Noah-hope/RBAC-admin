package com.adgn.catalogfixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.PasswordPolicy;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.query.management.ManagementAccess;
import com.adgn.backend.query.preview.*;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.core.audit.AuditRecord;
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
class CatalogPreviewMySqlCheck {
    private IsolatedMySqlDatabase database;
    private ConfigurableApplicationContext application;
    private JdbcTemplate jdbc;
    private Path keys;
    private String base,account,token;
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"catalog-keys-");
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
        account=id();token=admin(account);
    }
    @AfterAll void close()throws Exception{
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
    private String admin(String login)throws Exception{
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",login,login,new PasswordPolicy().encode("Catalog-Fixture-123456"));
        var response=request("POST","/api/v1/admin/sessions",Map.of("loginName",login,"password","Catalog-Fixture-123456"),null,null,Map.of());
        assertThat(response.statusCode()).isEqualTo(201);return body(response).path("accessToken").stringValue();
    }
    private String seededPreview(){
        String preview=id();
        var authority=json.createObjectNode().put("accountId",account).putNull("scopeId").putNull("companyId").putNull("departmentId")
                .putNull("appointmentId").putNull("appointmentVersion").putNull("scopeVersion");
        authority.putArray("systems");
        jdbc.update("INSERT INTO impact_preview(id,creator_account_id,authority_snapshot,operation,input_digest,content_digest,dependency_snapshot,result_snapshot,expires_at,created_at) VALUES(?,?,?,'test',?,?, '{}','{}',UTC_TIMESTAMP(6)+INTERVAL 5 MINUTE,UTC_TIMESTAMP(6))",preview,account,json.writeValueAsString(authority),new byte[32],new byte[32]);
        return preview;
    }
    @Test void previewPagesUseOneStoredSnapshotAndStableKeysNotLiveEmployeeRows()throws Exception{
        String preview=seededPreview();
        for(int i=0;i<21;i++){
            var item=Map.of("employeeId","employee-"+i,"companyId","company","systemId","system","canAccessBefore",true,"canAccessAfter",false,"resultCompleteness","COMPLETE","visibleGrantIds",List.of("grant-"+i));
            jdbc.update("INSERT INTO preview_item(id,preview_id,item_kind,stable_key,safe_item,visibility_basis) VALUES(?,?,'EMPLOYEE',?,?,'{}')",id(),preview,String.format("%03d",i),json.writeValueAsString(item));
        }
        var first=call("GET","/api/v1/admin/previews/"+preview+"/employees?pageSize=10",null,null);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(body(first).path("total").longValue()).isEqualTo(21);
        assertThat(body(first).path("items").get(0).path("employeeId").stringValue()).isEqualTo("employee-0");
        var last=call("GET","/api/v1/admin/previews/"+preview+"/employees?pageSize=10&page=3",null,null);
        assertThat(body(last).path("items").size()).isEqualTo(1);
        assertThat(body(last).path("items").get(0).path("employeeId").stringValue()).isEqualTo("employee-20");
        var targets=call("GET","/api/v1/admin/previews/"+preview+"/targets",null,null);
        assertThat(targets.statusCode()).as(targets.body()).isEqualTo(200);
        assertThat(body(targets).path("total").longValue()).isZero();
    }
    @Test void previewCannotBeReadByAnotherGlobalAdminAndExpiresAsAWhole()throws Exception{
        String preview=seededPreview(),other=admin(id());
        var foreign=request("GET","/api/v1/admin/previews/"+preview+"/employees",null,other,null,Map.of());
        assertThat(foreign.statusCode()).as(foreign.body()).isEqualTo(404);
        jdbc.update("UPDATE impact_preview SET created_at=UTC_TIMESTAMP(6)-INTERVAL 10 MINUTE,expires_at=UTC_TIMESTAMP(6)-INTERVAL 5 MINUTE WHERE id=?",preview);
        var expired=call("GET","/api/v1/admin/previews/"+preview+"/targets",null,null);
        assertThat(expired.statusCode()).as(expired.body()).isEqualTo(409);
        assertThat(body(expired).path("code").stringValue()).isEqualTo("PREVIEW_STALE");
    }
    private JsonNode system()throws Exception{
        var response=call("POST","/api/v1/admin/systems",Map.of("code","catalog_"+id(),"name","系统","reason","登记"),id());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return body(response);
    }
    private Map<String,Object> edit(long version,boolean enabled){return Map.of("expectedVersion",version,"name","系统","description","","enabled",enabled,"reason","修改状态");}
    @Test void systemPreviewIsPersistedWithoutChangingFormalSystemState()throws Exception{
        String system=system().path("id").stringValue();
        var response=call("POST","/api/v1/admin/systems/"+system+"/state-previews",edit(1,true),null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var value=body(response);String preview=value.path("id").stringValue();
        assertThat(value.path("contentDigest").stringValue()).matches("[a-f0-9]{64}");
        assertThat(value.path("impact").path("visibleEmployeeCount").longValue()).isZero();
        assertThat(value.path("impact").path("visibility").stringValue()).isEqualTo("COMPLETE");
        assertThat(jdbc.queryForObject("SELECT enabled FROM business_system WHERE id=?",Boolean.class,system)).isFalse();
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,created_at,expires_at) FROM impact_preview WHERE id=?",Long.class,preview)).isEqualTo(300);
        assertThat(call("GET","/api/v1/admin/previews/"+preview+"/employees",null,null).statusCode()).isEqualTo(200);
        assertThat(call("POST","/api/v1/admin/systems/"+system+"/state-previews",edit(0,true),null).statusCode()).isEqualTo(409);
    }
    private record Member(String employee,String company,String department,String employment) { }
    private Member member(){
        String e=id(),c=id(),d=id(),p=id(),em=id();
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",e,e,"员工");
        jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",c,c,"公司");
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",d,c,d,"部门");
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",p,d,p,"岗位");
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",em,e,c,d,p);
        return new Member(e,c,d,em);
    }
    private String role(String system,Member m,String permission){
        String r=id();
        jdbc.update("INSERT INTO business_role(id,code,name,company_id,department_id,system_id,state,configuration_version,created_at) VALUES(?,?,?,?,?,?,'EFFECTIVE',1,UTC_TIMESTAMP(6))",r,"ROLE_"+r.replace("-",""),"角色",m.company(),m.department(),system);
        jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",r,permission,system);return r;
    }
    private String grant(String system,Member m,String role){
        String source=id(),baseline=id(),grant=id(),request=id();
        var existing=jdbc.queryForList("SELECT id FROM authorization_source WHERE employee_id=? AND company_id=? AND department_id=? AND system_id=?",String.class,m.employee(),m.company(),m.department(),system);
        if(existing.isEmpty())jdbc.update("INSERT INTO authorization_source(id,employee_id,company_id,department_id,system_id) VALUES(?,?,?,?,?)",source,m.employee(),m.company(),m.department(),system);
        else source=existing.getFirst();
        jdbc.update("INSERT INTO grant_baseline(id,source_id,role_id,company_id,system_id,relation_version) VALUES(?,?,?,?,?,1)",baseline,source,role,m.company(),system);
        jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,'ROLE_GRANT',?,'{}','夹具授予',UTC_TIMESTAMP(6))",request,m.company());
        jdbc.update("INSERT INTO change_target(request_id,line_no,line_kind,company_id,system_id,department_id,employee_id,role_id,source_id,baseline_id,expected_configuration_version,expected_source_epoch,expected_relation_version,target_snapshot) VALUES(?,0,'ROLE_GRANT',?,?,?,?,?,?,?,1,0,0,'{}')",request,m.company(),system,m.department(),m.employee(),role,source,baseline);
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=1,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type='GLOBAL',original_authority='{}',fixed_payload_bytes=X'7B7D',content_digest=UNHEX(REPEAT('00',32)) WHERE id=?",account,request);
        String oa=id(),event=id(),audit=id();
        jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oa,oa);
        jdbc.update("INSERT INTO oa_link(request_id,oa_instance_id,oa_approval_id,accepted_at,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",request,oa,id());
        jdbc.update("INSERT INTO oa_callback_event(id,oa_instance_id,event_id,request_id,payload_digest,safe_payload,received_at) VALUES(?,?,?,?,UNHEX(REPEAT('00',32)),'{}',UTC_TIMESTAMP(6))",event,oa,id(),request);
        jdbc.update("INSERT INTO oa_final_result(request_id,first_event_id,decision,decided_at,comment,received_at) VALUES(?,?,'APPROVED',UTC_TIMESTAMP(6),'夹具批准',UTC_TIMESTAMP(6))",request,event);
        jdbc.update("UPDATE change_request SET state='APPROVED',delivery_status='LINKED',approval_result='APPROVED',execution_status='PENDING' WHERE id=?",request);
        jdbc.update("INSERT INTO audit_event(id,occurred_at,event_type,actor_kind,actor_id,target_id,visibility,result,correlation_id,reason,event_snapshot) VALUES(?,UTC_TIMESTAMP(6),'FIXTURE_EXECUTED','ACCOUNT',?,?,'GLOBAL','SUCCEEDED',?,'夹具成功','{\"schemaVersion\":1}')",audit,account,request,id());
        jdbc.update("INSERT INTO execution_success(request_id,audit_event_id,executed_at,result_snapshot) VALUES(?,?,UTC_TIMESTAMP(6),'{}')",request,audit);
        jdbc.update("UPDATE change_request SET state='EXECUTED',execution_status='SUCCEEDED' WHERE id=?",request);
        jdbc.update("INSERT INTO role_grant(id,baseline_id,relation_version,source_epoch,granted_by_request_id,created_at) VALUES(?,?,1,0,?,UTC_TIMESTAMP(6))",grant,baseline,request);
        jdbc.update("INSERT INTO grant_employment_evidence(grant_id,employment_id) VALUES(?,?)",grant,m.employment());
        return grant;
    }
    @Test void systemDisablePreviewDeduplicatesEmployeesButPreservesBothGrantImpacts()throws Exception{
        var system=system();String sid=system.path("id").stringValue(),access=system.path("systemAccessPermissionId").stringValue();
        jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        var m=member();String roleA=role(sid,m,access),roleB=role(sid,m,access);
        String first=grant(sid,m,roleA),second=grant(sid,m,roleB);
        var response=call("POST","/api/v1/admin/systems/"+sid+"/state-previews",edit(1,false),null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var value=body(response);var impact=value.path("impact");
        assertThat(impact.path("visibleEmployeeCount").longValue()).isEqualTo(1);
        assertThat(impact.path("visibleLosingAccessCount").longValue()).isEqualTo(1);
        assertThat(impact.path("visibleRoleCount").longValue()).isEqualTo(2);
        assertThat(impact.path("visibleGrantCount").longValue()).isEqualTo(2);
        assertThat(impact.path("visibleSourceCount").longValue()).isEqualTo(1);
        var employees=body(call("GET","/api/v1/admin/previews/"+value.path("id").stringValue()+"/employees",null,null));
        var item=employees.path("items").get(0);
        assertThat(item.path("employeeId").stringValue()).isEqualTo(m.employee());
        assertThat(item.path("canAccessBefore").booleanValue()).isTrue();assertThat(item.path("canAccessAfter").booleanValue()).isFalse();
        assertThat(item.path("visibleGrantIds").size()).isEqualTo(2);
        assertThat(item.path("visibleGrantIds").toString()).contains(first,second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE id IN (?,?) AND status='ACTIVE'",Integer.class,first,second)).isEqualTo(2);
    }
    private Map<String,Object> node(String local,String type,String parent,String binding,boolean enabled){
        var value=new LinkedHashMap<String,Object>();value.put("kind","NODE_CREATE");value.put("localKey",local);value.put("code",local);value.put("name",local);
        value.put("nodeType",type);value.put("parentId",null);value.put("parentLocalKey",parent);value.put("sortOrder",0);value.put("enabled",enabled);
        value.put("visibilityPermissionId",null);value.put("visibilityPermissionLocalKey",binding);return value;
    }
    private Map<String,Object> operation(String local,String function,boolean menu){
        var value=new LinkedHashMap<String,Object>();value.put("kind","PERMISSION_CREATE");value.put("localKey",local);value.put("functionId",null);value.put("functionLocalKey",function);
        value.put("code",local);value.put("name",local);value.put("menuEligible",menu);value.put("enabled",true);return value;
    }
    private Map<String,Object> catalog(long version,List<Map<String,Object>> commands){return Map.of("expectedCatalogVersion",version,"commands",commands,"reason","目录变更");}
    private JsonNode previewCatalog(String system,Map<String,Object> input)throws Exception{
        var response=call("POST","/api/v1/admin/systems/"+system+"/catalog-previews",input,null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return body(response);
    }
    private HttpResponse<String> saveCatalog(String system,Map<String,Object> input,String preview,String key)throws Exception{
        var save=new LinkedHashMap<>(input);save.put("previewId",preview);return call("POST","/api/v1/admin/systems/"+system+"/catalog-changes",save,key);
    }
    @Test void catalogCreatesForwardReferencesAsOneAtomicTreeAndReturnsVersionedPages()throws Exception{
        String system=system().path("id").stringValue();
        var input=catalog(1,List.of(operation("view","function",true),operation("edit","function",false),node("function","FUNCTION","root","view",true),node("root","NAVIGATION",null,null,true)));
        var preview=previewCatalog(system,input);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM directory_node WHERE system_id=?",Long.class,system)).isZero();
        String key=id();var response=saveCatalog(system,input,preview.path("id").stringValue(),key);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(body(response).path("outcome").stringValue()).isEqualTo("APPLIED");
        assertThat(body(response).path("createdIds").size()).isEqualTo(4);
        assertThat(body(response).path("catalogVersion").longValue()).isEqualTo(2);
        assertThat(body(response).path("auditEventIds").size()).isEqualTo(1);
        var replay=saveCatalog(system,input,preview.path("id").stringValue(),key);
        assertThat(body(replay)).isEqualTo(body(response));assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        var nodes=call("GET","/api/v1/admin/systems/"+system+"/directory-nodes?rootOnly=true",null,null);
        assertThat(nodes.statusCode()).as(nodes.body()).isEqualTo(200);assertThat(body(nodes).path("total").longValue()).isEqualTo(1);
        assertThat(nodes.headers().firstValue("X-Catalog-Version")).contains("2");
        var permissions=call("GET","/api/v1/admin/systems/"+system+"/permissions?permissionType=FUNCTION_OPERATION",null,null);
        assertThat(permissions.statusCode()).as(permissions.body()).isEqualTo(200);assertThat(body(permissions).path("total").longValue()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM directory_node n JOIN permission p ON p.id=n.visibility_permission_id AND p.function_id=n.id WHERE n.system_id=? AND n.enabled=1 AND p.menu_eligible=1",Long.class,system)).isEqualTo(1);
    }
    @Test void systemSaveChecksInputAndNewlyAddedDependenciesButAllowsUnrelatedRoleRename()throws Exception{
        var system=system();String sid=system.path("id").stringValue();var input=edit(1,true);
        var preview=body(call("POST","/api/v1/admin/systems/"+sid+"/state-previews",input,null));
        var mismatch=new HashMap<>(input);mismatch.put("reason","不同输入");
        var bad=call("PUT","/api/v1/admin/systems/"+sid,Map.of("input",mismatch,"previewId",preview.path("id").stringValue()),id());
        assertThat(bad.statusCode()).as(bad.body()).isEqualTo(409);
        String key=id();var save=Map.of("input",input,"previewId",preview.path("id").stringValue());
        var success=call("PUT","/api/v1/admin/systems/"+sid,save,key);
        assertThat(success.statusCode()).as(success.body()).isEqualTo(200);assertThat(body(success).path("version").longValue()).isEqualTo(2);
        assertThat(body(success).path("catalogVersion").longValue()).isEqualTo(1);
        jdbc.update("UPDATE impact_preview SET created_at=UTC_TIMESTAMP(6)-INTERVAL 10 MINUTE,expires_at=UTC_TIMESTAMP(6)-INTERVAL 5 MINUTE WHERE id=?",preview.path("id").stringValue());
        assertThat(body(call("PUT","/api/v1/admin/systems/"+sid,save,key))).isEqualTo(body(success));
        var disable=edit(2,false);var stale=body(call("POST","/api/v1/admin/systems/"+sid+"/state-previews",disable,null));
        var m=member();String role=role(sid,m,system.path("systemAccessPermissionId").stringValue());grant(sid,m,role);
        var rejected=call("PUT","/api/v1/admin/systems/"+sid,Map.of("input",disable,"previewId",stale.path("id").stringValue()),id());
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(409);assertThat(body(rejected).path("code").stringValue()).isEqualTo("PREVIEW_STALE");
        var fresh=body(call("POST","/api/v1/admin/systems/"+sid+"/state-previews",disable,null));
        jdbc.update("UPDATE business_role SET name='仅改名',metadata_version=metadata_version+1,row_version=row_version+1 WHERE id=?",role);
        var applied=call("PUT","/api/v1/admin/systems/"+sid,Map.of("input",disable,"previewId",fresh.path("id").stringValue()),id());
        assertThat(applied.statusCode()).as(applied.body()).isEqualTo(200);assertThat(body(applied).path("enabled").booleanValue()).isFalse();
    }
    private Map<String,String> install(String system)throws Exception{
        var input=catalog(1,List.of(node("root","NAVIGATION",null,null,true),node("function","FUNCTION","root","view",true),operation("view","function",true),operation("edit","function",false)));
        var preview=previewCatalog(system,input);var saved=saveCatalog(system,input,preview.path("id").stringValue(),id());
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);var ids=new HashMap<String,String>();
        for(var item:body(saved).path("createdIds"))ids.put(item.path("localKey").stringValue(),item.path("id").stringValue());return ids;
    }
    private Map<String,Object> nodeUpdate(String id,long version,String name,String parent,boolean enabled,String visibility){
        var c=new LinkedHashMap<String,Object>();c.put("kind","NODE_UPDATE");c.put("id",id);c.put("expectedVersion",version);c.put("name",name);c.put("parentId",parent);c.put("sortOrder",0);c.put("enabled",enabled);c.put("visibilityPermissionId",visibility);return c;
    }
    private Map<String,Object> permissionUpdate(String id,long version,String name,boolean menu,boolean enabled){return Map.of("kind","PERMISSION_UPDATE","id",id,"expectedVersion",version,"name",name,"menuEligible",menu,"enabled",enabled);}
    @Test void catalogRejectsCyclesCrossSystemBindingsAndDuplicateCommandsBeforeWriting()throws Exception{
        var system=system();String sid=system.path("id").stringValue();var ids=install(sid);
        String foreign=install(system().path("id").stringValue()).get("view");
        var cases=List.of(
                catalog(2,List.of(nodeUpdate(ids.get("root"),1,"root",ids.get("function"),true,null))),
                catalog(2,List.of(nodeUpdate(ids.get("function"),1,"function",ids.get("root"),true,foreign))),
                catalog(2,List.of(nodeUpdate(ids.get("root"),1,"root",id(),true,null))),
                catalog(2,List.of(permissionUpdate(system.path("systemAccessPermissionId").stringValue(),1,"进入",true,true))),
                catalog(2,List.of(node("x","FUNCTION",null,null,true))),
                catalog(2,List.of(operation("x","unknown-local",true))),
                catalog(2,List.of(nodeUpdate(ids.get("root"),1,"root",null,true,null),nodeUpdate(ids.get("root"),1,"root",null,true,null))));
        for(var input:cases){var response=call("POST","/api/v1/admin/systems/"+sid+"/catalog-previews",input,null);assertThat(response.statusCode()).as(response.body()).isEqualTo(422);}
        assertThat(jdbc.queryForObject("SELECT catalog_version FROM business_system WHERE id=?",Long.class,sid)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM directory_node WHERE system_id=?",Long.class,sid)).isEqualTo(2);
    }
    @Test void noChangeKeepsVersionsAndAuditsButStillChecksEveryExpectedVersion()throws Exception{
        String sid=system().path("id").stringValue();var ids=install(sid);
        var input=catalog(2,List.of(nodeUpdate(ids.get("root"),1,"root",null,true,null)));
        var preview=previewCatalog(sid,input);long audits=jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id=?",Long.class,sid);
        var response=saveCatalog(sid,input,preview.path("id").stringValue(),id());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);assertThat(body(response).path("outcome").stringValue()).isEqualTo("NO_CHANGE");
        assertThat(body(response).path("catalogVersion").longValue()).isEqualTo(2);assertThat(body(response).path("auditEventIds").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id=?",Long.class,sid)).isEqualTo(audits);
        assertThat(call("POST","/api/v1/admin/systems/"+sid+"/catalog-previews",catalog(2,List.of(nodeUpdate(ids.get("root"),0,"root",null,true,null))),null).statusCode()).isEqualTo(409);
    }
    @Test void disablingBoundViewPreservesBindingEditAndGrantAndNameSwapsAreAtomic()throws Exception{
        var system=system();String sid=system.path("id").stringValue();var ids=install(sid);jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        var m=member();String r=role(sid,m,system.path("systemAccessPermissionId").stringValue());String grant=grant(sid,m,r);
        jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?),(?,?,?)",r,ids.get("view"),sid,r,ids.get("edit"),sid);
        var input=catalog(2,List.of(permissionUpdate(ids.get("view"),1,"view",true,false)));
        var preview=previewCatalog(sid,input);assertThat(preview.path("impact").path("visibleRetainingAccessCount").longValue()).isEqualTo(1);
        assertThat(saveCatalog(sid,input,preview.path("id").stringValue(),id()).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT visibility_permission_id FROM directory_node WHERE id=?",String.class,ids.get("function"))).isEqualTo(ids.get("view"));
        assertThat(jdbc.queryForObject("SELECT enabled FROM permission WHERE id=?",Boolean.class,ids.get("edit"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM role_grant WHERE id=?",String.class,grant)).isEqualTo("ACTIVE");
        var swap=catalog(3,List.of(permissionUpdate(ids.get("view"),2,"edit",true,false),permissionUpdate(ids.get("edit"),1,"view",false,true)));
        var swappedPreview=previewCatalog(sid,swap);var swapped=saveCatalog(sid,swap,swappedPreview.path("id").stringValue(),id());
        assertThat(swapped.statusCode()).as(swapped.body()).isEqualTo(200);assertThat(body(swapped).path("catalogVersion").longValue()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT name FROM permission WHERE id=?",String.class,ids.get("view"))).isEqualTo("edit");
        assertThat(jdbc.queryForObject("SELECT name FROM permission WHERE id=?",String.class,ids.get("edit"))).isEqualTo("view");
    }
    @Test void auditFailureRollsBackTheWholeCatalogBatchAndSystemState()throws Exception{
        String sid=system().path("id").stringValue();var input=catalog(1,List.of(node("new-root","NAVIGATION",null,null,true)));
        var preview=previewCatalog(sid,input);var state=body(call("POST","/api/v1/admin/systems/"+sid+"/state-previews",edit(1,true),null));
        database.execute("CREATE TRIGGER catalog_fail_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected catalog audit failure'");
        try{
            assertThat(saveCatalog(sid,input,preview.path("id").stringValue(),id()).statusCode()).isGreaterThanOrEqualTo(500);
            var response=call("PUT","/api/v1/admin/systems/"+sid,Map.of("input",edit(1,true),"previewId",state.path("id").stringValue()),id());
            assertThat(response.statusCode()).isGreaterThanOrEqualTo(500);
        }finally{database.execute("DROP TRIGGER catalog_fail_audit");}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM directory_node WHERE system_id=?",Long.class,sid)).isZero();
        assertThat(jdbc.queryForObject("SELECT catalog_version FROM business_system WHERE id=?",Long.class,sid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM business_system WHERE id=?",Long.class,sid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT enabled FROM business_system WHERE id=?",Boolean.class,sid)).isFalse();
    }
    @Test void concurrentCatalogSavesSerializeAndOnlyOneExpectedVersionWins()throws Exception{
        String sid=system().path("id").stringValue();var ids=install(sid);
        var left=catalog(2,List.of(nodeUpdate(ids.get("root"),1,"left",null,true,null)));
        var right=catalog(2,List.of(nodeUpdate(ids.get("root"),1,"right",null,true,null)));
        String lp=previewCatalog(sid,left).path("id").stringValue(),rp=previewCatalog(sid,right).path("id").stringValue();
        var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{ready.countDown();start.await();return saveCatalog(sid,left,lp,id());});
            var two=pool.submit(()->{ready.countDown();start.await();return saveCatalog(sid,right,rp,id());});
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(one.get(25,java.util.concurrent.TimeUnit.SECONDS).statusCode(),two.get(25,java.util.concurrent.TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT catalog_version FROM business_system WHERE id=?",Long.class,sid)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT version FROM directory_node WHERE id=?",Long.class,ids.get("root"))).isEqualTo(2);
    }
    @Test void logicalNewSourcePreviewLoadsCurrentEmploymentEvenBeforeSourceIsMaterialized()throws Exception{
        var system=system();String sid=system.path("id").stringValue();jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        var m=member();String r=role(sid,m,system.path("systemAccessPermissionId").stringValue());
        var source=new ImpactPolicy.Source(m.employee(),m.company(),m.department(),sid);
        var change=new ImpactPolicy.Change(Set.of(sid),Map.of(),Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),List.of(new ImpactPolicy.Addition(id(),r,source)),Set.of(),List.of());
        var authority=new ManagementAccess.Authority(application.getBean(IdentityRepository.class).byId(account),null,null,null,Set.of());
        var impacts=application.getBean(ImpactService.class);
        assertThatThrownBy(()->impacts.analyze(authority,change)).isInstanceOf(IllegalStateException.class);
        var analysis=application.getBean(CommandBoundary.class).execute(()->new CommandBoundary.Identity<>(authority,new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account)),context->impacts.analyze(authority,change));
        assertThat(analysis.projection().impact().get("visibleEmployeeCount")).isEqualTo(1);
        assertThat(analysis.projection().employees().getFirst().path("canAccessBefore").booleanValue()).isFalse();
        assertThat(analysis.projection().employees().getFirst().path("canAccessAfter").booleanValue()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authorization_source WHERE employee_id=?",Long.class,m.employee())).isZero();
    }
    private record Scoped(String account,String token,ManagementAccess.Authority authority,ManagementAccess.Context selection,Map<String,String> headers,String scope) { }
    private Scoped scoped(Member m,String system)throws Exception{
        String admin=id(),scope=id(),appointment=id();
        jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,must_change_password,created_at) VALUES(?,?,?,?,0,UTC_TIMESTAMP(6))",admin,m.employee(),admin,new PasswordPolicy().encode("Scoped-Catalog-123456"));
        jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,m.company(),m.department());
        jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,system);
        jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",appointment,scope,admin);
        var login=request("POST","/api/v1/admin/sessions",Map.of("loginName",admin,"password","Scoped-Catalog-123456"),null,null,Map.of());
        assertThat(login.statusCode()).isEqualTo(201);
        var authority=new ManagementAccess.Authority(application.getBean(IdentityRepository.class).byId(admin),scope,m.company(),m.department(),Set.of(system));
        return new Scoped(admin,body(login).path("accessToken").stringValue(),authority,new ManagementAccess.Context(appointment,"1","1"),Map.of("X-Admin-Appointment-Id",appointment,"X-Admin-Appointment-Version","1","X-Admin-Scope-Version","1"),scope);
    }
    @Test void scopedPreviewKeepsOtherDepartmentAccessSecretAndInvalidatesAllPagesOnContextChange()throws Exception{
        var system=system();String sid=system.path("id").stringValue();jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        var m=member();String aRole=role(sid,m,system.path("systemAccessPermissionId").stringValue()),aGrant=grant(sid,m,aRole);
        String dep=id(),pos=id(),employment=id();
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",dep,m.company(),dep,"隐藏部门");
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",pos,dep,pos,"隐藏岗位");
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",employment,m.employee(),m.company(),dep,pos);
        var other=new Member(m.employee(),m.company(),dep,employment);String bRole=role(sid,other,system.path("systemAccessPermissionId").stringValue()),bGrant=grant(sid,other,bRole);
        var selected=scoped(m,sid);var source=new ImpactPolicy.Source(m.employee(),m.company(),m.department(),sid);
        var change=new ImpactPolicy.Change(Set.of(sid),Map.of(),Map.of(),Map.of(),Map.of(),Set.of(source),Set.of(),Set.of(),Set.of(),List.of(),Set.of(),List.of());
        var previews=application.getBean(PreviewService.class);var impacts=application.getBean(ImpactService.class);
        var preview=application.getBean(CommandBoundary.class).execute(()->new CommandBoundary.Identity<>(selected.authority(),new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,selected.account())),context->{
            var analysis=impacts.analyze(context.authority(),change);
            return previews.create(context,context.authority(),selected.selection(),"closeSource:fixture",Map.of("source",source),analysis.dependencies(),analysis.projection(),Map.of());
        });
        assertThat(preview.impact().get("visibleRetainingAccessCount")).isEqualTo(1L);
        var response=request("GET","/api/v1/admin/previews/"+preview.id()+"/employees",null,selected.token(),null,selected.headers());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);assertThat(body(response).path("total").longValue()).isEqualTo(1);
        assertThat(body(response).path("items").get(0).path("canAccessAfter").booleanValue()).isTrue();
        assertThat(response.body()).contains(aGrant).doesNotContain(bGrant,bRole,dep);
        var targets=request("GET","/api/v1/admin/previews/"+preview.id()+"/targets",null,selected.token(),null,selected.headers());
        assertThat(targets.statusCode()).as(targets.body()).isEqualTo(200);assertThat(targets.body()).doesNotContain(bGrant,bRole,dep);
        assertThat(call("GET","/api/v1/admin/previews/"+preview.id()+"/employees",null,null).statusCode()).isEqualTo(404);
        assertThat(request("GET","/api/v1/admin/systems/"+sid+"/directory-nodes",null,selected.token(),null,selected.headers()).statusCode()).isEqualTo(200);
        assertThat(request("POST","/api/v1/admin/systems/"+sid+"/state-previews",edit(1,false),selected.token(),null,selected.headers()).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE management_scope SET scope_version=scope_version+1 WHERE id=?",selected.scope());
        for(String path:List.of("employees","targets")){
            var stale=request("GET","/api/v1/admin/previews/"+preview.id()+"/"+path,null,selected.token(),null,selected.headers());
            assertThat(stale.statusCode()).as(stale.body()).isEqualTo(409);assertThat(body(stale).path("code").stringValue()).isEqualTo("PREVIEW_STALE");
        }
    }
    @Test void catalogPreviewRejectsWrongOperationOrDifferentLocalKeysAsStale()throws Exception{
        String sid=system().path("id").stringValue();var input=catalog(1,List.of(node("root","NAVIGATION",null,null,true)));
        var wrong=body(call("POST","/api/v1/admin/systems/"+sid+"/state-previews",edit(1,true),null));
        var rejected=saveCatalog(sid,input,wrong.path("id").stringValue(),id());
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(409);assertThat(body(rejected).path("code").stringValue()).isEqualTo("PREVIEW_STALE");
        var original=previewCatalog(sid,input);var different=catalog(1,List.of(node("different","NAVIGATION",null,null,true)));
        var changed=saveCatalog(sid,different,original.path("id").stringValue(),id());
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(409);assertThat(body(changed).path("code").stringValue()).isEqualTo("PREVIEW_STALE");
    }
    @Test void metadataOnlyCatalogPreviewDoesNotDependOnUnrelatedNewGrant()throws Exception{
        var system=system();String sid=system.path("id").stringValue();var ids=install(sid);
        var input=catalog(2,List.of(nodeUpdate(ids.get("root"),1,"仅更名",null,true,null)));var preview=previewCatalog(sid,input);
        var m=member();String r=role(sid,m,system.path("systemAccessPermissionId").stringValue());grant(sid,m,r);
        var response=saveCatalog(sid,input,preview.path("id").stringValue(),id());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(body(response).path("outcome").stringValue()).isEqualTo("APPLIED");
    }
    private String pendingConfiguration(String system,Member m,String role,List<String> permissions){
        String request=id();String content=json.writeValueAsString(Map.of("kind","ROLE_CONFIGURATION","companyId",m.company(),"roleId",role,"expectedConfigurationVersion",1,"permissionIds",permissions));
        jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,'ROLE_CONFIGURATION',?,?,'夹具固定配置',UTC_TIMESTAMP(6))",request,m.company(),content);
        jdbc.update("INSERT INTO role_change_claim(role_id,request_id) VALUES(?,?)",role,request);
        jdbc.update("INSERT INTO change_target(request_id,line_no,line_kind,company_id,system_id,department_id,role_id,expected_configuration_version,target_snapshot) VALUES(?,0,'ROLE_CONFIGURATION',?,?,?,?,1,'{}')",request,m.company(),system,m.department(),role);
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=1,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type='GLOBAL',original_authority='{}',fixed_payload_bytes=X'7B7D',content_digest=UNHEX(REPEAT('00',32)) WHERE id=?",account,request);
        return request;
    }
    @Test void newlySubmittedConfigurationIsAnEmptySetPhantomEvenWithoutCurrentPermissionReference()throws Exception{
        var system=system();String sid=system.path("id").stringValue();var ids=install(sid);var m=member();String r=role(sid,m,system.path("systemAccessPermissionId").stringValue());
        var input=catalog(2,List.of(permissionUpdate(ids.get("view"),1,"view",true,false)));var preview=previewCatalog(sid,input);
        assertThat(preview.path("impact").path("visiblePendingRequestCount").longValue()).isZero();
        String request=pendingConfiguration(sid,m,r,List.of(ids.get("view")));
        var rejected=saveCatalog(sid,input,preview.path("id").stringValue(),id());
        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(409);assertThat(body(rejected).path("code").stringValue()).isEqualTo("PREVIEW_STALE");
        var fresh=previewCatalog(sid,input);assertThat(fresh.path("impact").path("visiblePendingRequestCount").longValue()).isEqualTo(1);
        var targets=call("GET","/api/v1/admin/previews/"+fresh.path("id").stringValue()+"/targets",null,null);
        assertThat(targets.statusCode()).as(targets.body()).isEqualTo(200);assertThat(targets.body()).contains(request);
        assertThat(jdbc.queryForObject("SELECT enabled FROM permission WHERE id=?",Boolean.class,ids.get("view"))).isTrue();
    }
    @Test void newlyApplicableEmploymentMakesAnOldFunctionalPreviewStale()throws Exception{
        var system=system();String sid=system.path("id").stringValue();jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",sid);
        var m=member();String r=role(sid,m,system.path("systemAccessPermissionId").stringValue());grant(sid,m,r);
        var input=edit(1,false);var preview=body(call("POST","/api/v1/admin/systems/"+sid+"/state-previews",input,null));
        String position=id();jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",position,m.department(),position,"新增兼岗");
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",id(),m.employee(),m.company(),m.department(),position);
        var response=call("PUT","/api/v1/admin/systems/"+sid,Map.of("input",input,"previewId",preview.path("id").stringValue()),id());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);assertThat(body(response).path("code").stringValue()).isEqualTo("PREVIEW_STALE");
        assertThat(jdbc.queryForObject("SELECT enabled FROM business_system WHERE id=?",Boolean.class,sid)).isTrue();
    }
    @Test void contractBoundsStrictFieldsSupplementalTargetsAndStableCatalogPages()throws Exception{
        String sid=system().path("id").stringValue();var commands=new ArrayList<Map<String,Object>>();
        for(int i=0;i<101;i++)commands.add(node(String.format("n%03d",i),"NAVIGATION",null,null,true));
        assertThat(call("POST","/api/v1/admin/systems/"+sid+"/catalog-previews",catalog(1,commands),null).statusCode()).isEqualTo(422);
        assertThat(call("POST","/api/v1/admin/systems/"+sid+"/catalog-previews",catalog(1,List.of()),null).statusCode()).isEqualTo(422);
        var unknown=new HashMap<>(commands.getFirst());unknown.put("delete",true);
        assertThat(call("POST","/api/v1/admin/systems/"+sid+"/catalog-previews",catalog(1,List.of(unknown)),null).statusCode()).isEqualTo(422);
        var input=catalog(1,commands.subList(0,21));var preview=previewCatalog(sid,input);
        var targetPage=call("GET","/api/v1/admin/previews/"+preview.path("id").stringValue()+"/targets?page=3&pageSize=10",null,null);
        assertThat(targetPage.statusCode()).as(targetPage.body()).isEqualTo(200);assertThat(body(targetPage).path("total").longValue()).isEqualTo(21);assertThat(body(targetPage).path("items").size()).isEqualTo(1);
        assertThat(saveCatalog(sid,input,preview.path("id").stringValue(),id()).statusCode()).isEqualTo(200);
        var page=call("GET","/api/v1/admin/systems/"+sid+"/directory-nodes?page=3&pageSize=10",null,null);
        assertThat(body(page).path("items").get(0).path("code").stringValue()).isEqualTo("n020");assertThat(body(page).path("total").longValue()).isEqualTo(21);
        assertThat(page.headers().firstValue("X-Catalog-Version")).contains("2");
        assertThat(call("GET","/api/v1/admin/systems/"+sid+"/directory-nodes?rootOnly=true&parentId="+id(),null,null).statusCode()).isEqualTo(422);
        assertThat(call("GET","/api/v1/admin/systems/"+sid+"/permissions?pageSize=11",null,null).statusCode()).isEqualTo(422);
    }
    @Test void noChangeRemainsLegalAtMaximumVersionsButAnActualChangeIsRejected()throws Exception{
        String sid=system().path("id").stringValue();var ids=install(sid);long max=9007199254740991L;
        jdbc.update("UPDATE business_system SET catalog_version=?,version=?,row_version=? WHERE id=?",max,max,max,sid);
        jdbc.update("UPDATE directory_node SET version=? WHERE id=?",max,ids.get("root"));
        var same=catalog(max,List.of(nodeUpdate(ids.get("root"),max,"root",null,true,null)));var preview=previewCatalog(sid,same);
        var response=saveCatalog(sid,same,preview.path("id").stringValue(),id());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);assertThat(body(response).path("outcome").stringValue()).isEqualTo("NO_CHANGE");
        var change=catalog(max,List.of(nodeUpdate(ids.get("root"),max,"不同名称",null,true,null)));
        assertThat(call("POST","/api/v1/admin/systems/"+sid+"/catalog-previews",change,null).statusCode()).isEqualTo(409);
        var state=body(call("POST","/api/v1/admin/systems/"+sid+"/state-previews",edit(max,false),null));
        var saved=call("PUT","/api/v1/admin/systems/"+sid,Map.of("input",edit(max,false),"previewId",state.path("id").stringValue()),id());
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);assertThat(body(saved).path("version").longValue()).isEqualTo(max);
    }
}
