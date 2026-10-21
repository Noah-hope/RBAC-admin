package com.adgn.integrationfixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.PasswordPolicy;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Public delivered flows only: no fabricated approval, execution, grant or import commit. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeliveredModulesMySqlCheck {
    private IsolatedMySqlDatabase db;private ConfigurableApplicationContext app;private JdbcTemplate jdbc;
    private Path keys;private String base,admin,oaInstance;private RSAKey business,oa;private final JsonMapper json=JsonMapper.builder().build();private final HttpClient http=HttpClient.newHttpClient();
    private static String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"delivered-modules-keys-");
        for(String family:List.of("admin","business","oa")){var key=new RSAKeyGenerator(2048).keyID(family).generate();if(family.equals("business"))business=key;if(family.equals("oa"))oa=key;Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));}
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);db=new IsolatedMySqlDatabase();
        app=db.startWeb("--spring.profiles.active=dev,demo","--rbac.workflow.scheduling-enabled=false","--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin","--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),"--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),"--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),"--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture","--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(secret))));
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/";
        oaInstance=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oaInstance,oaInstance);
        String account=id();jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",account,account,new PasswordPolicy().encode("Delivered-Modules-1234"));
        admin=value(request("POST","admin/sessions",Map.of("loginName",account,"password","Delivered-Modules-1234"),null,null),201).path("accessToken").stringValue();
    }
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(db!=null)db.close();}finally{if(keys!=null){for(String f:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(f+".json"));Files.deleteIfExists(keys);}}}}
    private HttpResponse<String> request(String method,String path,Object body,String bearer,String key)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30));if(bearer!=null)b.header("Authorization","Bearer "+bearer);if(key!=null)b.header("Idempotency-Key",key);if(body!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode value(HttpResponse<String> r,int status){assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return r.body().isEmpty()?json.nullNode():json.readTree(r.body());}
    private JsonNode read(String path)throws Exception{return value(request("GET","admin/"+path,null,admin,null),200);}
    private JsonNode write(String method,String path,Object input,int status)throws Exception{return value(request(method,"admin/"+path,input,admin,id()),status);}
    private record Fixture(String system,String access,String company,String department,String employee,String businessToken){ }
    private Fixture fixture()throws Exception{
        var system=write("POST","systems",Map.of("code","s_"+id(),"name","集成系统","reason","集成登记"),201);String sid=system.path("id").stringValue();
        var input=Map.of("expectedVersion",1,"name","集成系统","description","","enabled",true,"reason","启用测试系统");var preview=write("POST","systems/"+sid+"/state-previews",input,200);
        write("PUT","systems/"+sid,Map.of("input",input,"previewId",preview.path("id").stringValue()),200);
        String c=id(),d=id(),p=id(),e=id(),subject=id();jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'测试公司',UTC_TIMESTAMP(6))",c,c);jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'测试部门',UTC_TIMESTAMP(6))",d,c,d);
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'测试岗位',UTC_TIMESTAMP(6))",p,d,p);jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'测试员工',UTC_TIMESTAMP(6))",e,e);jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",id(),e,c,d,p);
        jdbc.update("INSERT INTO service_identity(id,subject,kind,system_id) VALUES(?,?,'BUSINESS',?)",id(),subject,sid);
        var now=Instant.now();var claims=new JWTClaimsSet.Builder().issuer("urn:rbac:business").audience("rbac-business").subject(subject).jwtID(id()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).claim("systemId",sid).build();var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("business").build(),claims);jwt.sign(new RSASSASigner(business));
        return new Fixture(sid,system.path("systemAccessPermissionId").stringValue(),c,d,e,jwt.serialize());
    }
    private JsonNode role(Fixture f)throws Exception{return write("POST","roles",Map.of("name","集成角色","companyId",f.company(),"departmentId",f.department(),"systemId",f.system(),"reason","创建真实草稿"),201);}
    private Map<String,Object> metadata(){return Map.of("expectedVersion",1,"name","集成角色改名","description","集成","reason","元数据与草稿独立");}
    private Map<String,Object> catalogChange(Fixture f){return Map.of("expectedCatalogVersion",1,"reason","进入权限改名","commands",List.of(Map.of("kind","PERMISSION_UPDATE","id",f.access(),"expectedVersion",1,"name","改名进入","menuEligible",false,"enabled",true)));}
    @Test void savedRoleDraftDoesNotBecomeEffectiveInRoleGrantOrBusinessQueries()throws Exception{
        var f=fixture();var created=role(f);String role=created.path("role").path("id").stringValue(),draft=created.path("draftId").stringValue();
        write("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",1,"reason","选择进入权限","content",Map.of("kind","ROLE_CONFIGURATION","companyId",f.company(),"roleId",role,"expectedConfigurationVersion",0,"permissionIds",List.of(f.access()))),200);
        var r=read("roles/"+role);assertThat(r.path("state").stringValue()).isEqualTo("UNCONFIGURED");assertThat(r.path("configurationVersion").longValue()).isZero();assertThat(r.path("permissionIds").isEmpty()).isTrue();assertThat(r.path("activeChangeRequestId").stringValue()).isEqualTo(draft);
        var baseline=write("POST","grant-baselines",Map.of("items",List.of(Map.of("employeeId",f.employee(),"roleId",role,"sourceCompanyId",f.company(),"sourceDepartmentId",f.department()))),200).path("items").get(0);
        assertThat(baseline.path("sourceEpoch").longValue()).isZero();assertThat(baseline.path("relationVersion").longValue()).isZero();assertThat(baseline.path("activeGrantId").isNull()).isTrue();assertThat(baseline.path("employmentIds").size()).isEqualTo(1);
        var q=value(request("POST","integration/authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company()),f.businessToken(),null),200);assertThat(q.path("canAccessSystem").booleanValue()).isFalse();assertThat(q.path("operations").isEmpty()).isTrue();
        assertThat(read("change-requests/"+draft).path("state").stringValue()).isEqualTo("DRAFT");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authorization_source WHERE employee_id=?",Long.class,f.employee())).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id JOIN authorization_source s ON s.id=b.source_id WHERE s.employee_id=?",Long.class,f.employee())).isZero();
    }
    @Test void metadataUpdateKeepsDraftAndDeletingDraftKeepsHistoricalAudit()throws Exception{
        var f=fixture();var created=role(f);String role=created.path("role").path("id").stringValue(),draft=created.path("draftId").stringValue();
        write("PUT","roles/"+role,metadata(),200);assertThat(read("roles/"+role).path("activeChangeRequestId").stringValue()).isEqualTo(draft);assertThat(read("change-requests/"+draft).path("draftVersion").longValue()).isEqualTo(1);
        long before=read("audit-events?changeRequestId="+draft).path("total").longValue();assertThat(before).isPositive();
        write("DELETE","change-requests/"+draft+"?expectedDraftVersion=1",null,204);assertThat(request("GET","admin/change-requests/"+draft,null,admin,null).statusCode()).isEqualTo(404);
        assertThat(read("audit-events?changeRequestId="+draft).path("total").longValue()).isGreaterThan(before);assertThat(request("GET","admin/roles/"+role,null,admin,null).statusCode()).isEqualTo(404);
    }
    @Test void unrelatedRoleMetadataDoesNotInvalidateCatalogPreview()throws Exception{
        var f=fixture();String role=role(f).path("role").path("id").stringValue();var input=catalogChange(f);var preview=write("POST","systems/"+f.system()+"/catalog-previews",input,200);
        write("PUT","roles/"+role,metadata(),200);var save=new LinkedHashMap<String,Object>(input);save.put("previewId",preview.path("id").stringValue());String key=id(),path="admin/systems/"+f.system()+"/catalog-changes";
        var result=value(request("POST",path,save,admin,key),200);assertThat(result.path("catalogVersion").longValue()).isEqualTo(2);
        var replay=request("POST",path,save,admin,key);assertThat(replay.statusCode()).isEqualTo(200);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(read("systems/"+f.system()).path("catalogVersion").longValue()).isEqualTo(2);
    }
    @Test void authenticationFamiliesStaySeparatedAcrossAdminAndBusinessFlows()throws Exception{
        var f=fixture();String draft=role(f).path("draftId").stringValue();assertThat(request("GET","admin/change-requests/"+draft,null,f.businessToken(),null).statusCode()).isEqualTo(401);
        assertThat(request("POST","integration/permission-checks",Map.of("employeeKey",f.employee(),"companyId",f.company(),"permissionCode","entry"),admin,null).statusCode()).isEqualTo(401);
        var denied=value(request("POST","integration/permission-checks",Map.of("employeeKey",f.employee(),"companyId",f.company(),"permissionCode","unknown"),f.businessToken(),null),200);assertThat(denied.path("hasPermission").booleanValue()).isFalse();assertThat(denied.path("permissionType").isNull()).isTrue();
    }
    @Test void approvalPreviewIsAvailableAtTheFrozenOpenApiPath()throws Exception{
        var f=fixture();var created=role(f);String role=created.path("role").path("id").stringValue(),draft=created.path("draftId").stringValue();
        write("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",1,"reason","配置进入权限","content",Map.of("kind","ROLE_CONFIGURATION","companyId",f.company(),"roleId",role,"expectedConfigurationVersion",0,"permissionIds",List.of(f.access()))),200);
        var preview=write("POST","change-requests/"+draft+"/previews",Map.of("expectedDraftVersion",2),200);assertThat(preview.path("preview").path("id").stringValue()).isNotBlank();
    }
    private List<String> functionalCatalog(Fixture f)throws Exception{
        var fn=new LinkedHashMap<String,Object>();fn.put("kind","NODE_CREATE");fn.put("localKey","function");fn.put("code","function");fn.put("name","功能");fn.put("nodeType","FUNCTION");fn.put("parentId",null);fn.put("parentLocalKey",null);fn.put("sortOrder",0);fn.put("enabled",true);fn.put("visibilityPermissionId",null);fn.put("visibilityPermissionLocalKey","view");
        var commands=new ArrayList<Object>();commands.add(fn);
        for(String code:List.of("view","edit")){var p=new LinkedHashMap<String,Object>();p.put("kind","PERMISSION_CREATE");p.put("localKey",code);p.put("functionId",null);p.put("functionLocalKey","function");p.put("code",code);p.put("name",code);p.put("menuEligible",code.equals("view"));p.put("enabled",true);commands.add(p);}
        var input=Map.of("expectedCatalogVersion",1,"commands",commands,"reason","正式目录集成");var preview=write("POST","systems/"+f.system()+"/catalog-previews",input,200);var save=new LinkedHashMap<String,Object>(input);save.put("previewId",preview.path("id").stringValue());
        var saved=write("POST","systems/"+f.system()+"/catalog-changes",save,200);var ids=new HashMap<String,String>();for(var row:saved.path("createdIds"))ids.put(row.path("localKey").stringValue(),row.path("id").stringValue());return List.of(f.access(),ids.get("view"),ids.get("edit"));
    }
    private String oaToken()throws Exception{
        var now=Instant.now();var claims=new JWTClaimsSet.Builder().issuer("urn:rbac:oa").audience("rbac-oa-callback").subject(oaInstance).jwtID(id()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).claim("oaInstanceId",oaInstance).build();
        var token=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("oa").build(),claims);token.sign(new RSASSASigner(oa));return token.serialize();
    }
    private Map<String,Object> approve(String draft,long version)throws Exception{
        var preview=write("POST","change-requests/"+draft+"/previews",Map.of("expectedDraftVersion",version),200);
        write("POST","change-requests/"+draft+"/submissions",Map.of("expectedDraftVersion",version,"previewId",preview.path("preview").path("id").stringValue()),202);
        assertThat(app.getBean(com.adgn.backend.oa.OaDeliveryService.class).deliver(draft)).isTrue();var fixed=read("change-requests/"+draft);
        var input=Map.<String,Object>of("eventId",id(),"requestId",draft,"oaApprovalId",fixed.path("oaApprovalId").stringValue(),"submittedVersion",fixed.path("submittedVersion").longValue(),"contentDigest",fixed.path("contentDigest").stringValue(),"decision","APPROVED","isFinal",true,"decidedAt",Instant.now().toString(),"comment","正式模拟OA最终结果");
        value(request("POST","oa/final-results",input,oaToken(),null),202);return input;
    }
    private JsonNode business(Fixture f)throws Exception{return value(request("POST","integration/authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company()),f.businessToken(),null),200);}
    @Test void realConfigurationGrantAndRevocationWorkflowControlsBusinessPermissions()throws Exception{
        var f=fixture();var permissions=functionalCatalog(f);var created=role(f);String role=created.path("role").path("id").stringValue(),config=created.path("draftId").stringValue();
        write("PUT","change-requests/"+config,Map.of("expectedDraftVersion",1,"reason","配置正式权限","content",Map.of("kind","ROLE_CONFIGURATION","companyId",f.company(),"roleId",role,"expectedConfigurationVersion",0,"permissionIds",permissions)),200);
        approve(config,2);assertThat(read("roles/"+role).path("configurationVersion").longValue()).isZero();
        app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(config);assertThat(read("roles/"+role).path("configurationVersion").longValue()).isEqualTo(1);assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
        var baseline=write("POST","grant-baselines",Map.of("items",List.of(Map.of("employeeId",f.employee(),"roleId",role,"sourceCompanyId",f.company(),"sourceDepartmentId",f.department()))),200).path("items").get(0);
        var line=Map.of("employeeId",f.employee(),"roleId",role,"sourceCompanyId",f.company(),"sourceDepartmentId",f.department(),"expectedRoleConfigurationVersion",baseline.path("roleConfigurationVersion").longValue(),"expectedSourceEpoch",baseline.path("sourceEpoch").longValue(),"expectedRelationVersion",baseline.path("relationVersion").longValue(),"employmentIds",json.convertValue(baseline.path("employmentIds"),List.class));
        String grantDraft=write("POST","change-requests",Map.of("content",Map.of("kind","ROLE_GRANT","companyId",f.company(),"items",List.of(line)),"reason","正式授予"),201).path("id").stringValue();
        var callback=approve(grantDraft,1);assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(grantDraft);
        var allowed=business(f);assertThat(allowed.path("canAccessSystem").booleanValue()).isTrue();assertThat(allowed.path("operations").size()).isEqualTo(2);assertThat(allowed.path("menus").size()).isEqualTo(1);
        value(request("POST","oa/final-results",callback,oaToken(),null),200);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(grantDraft);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,grantDraft)).isEqualTo(1);
        var grants=read("grants?employeeId="+f.employee());assertThat(grants.path("items").size()).isEqualTo(1);var grant=grants.path("items").get(0);
        String revoke=write("POST","change-requests",Map.of("content",Map.of("kind","ROLE_REVOCATION","companyId",f.company(),"items",List.of(Map.of("grantId",grant.path("id").stringValue(),"expectedRelationVersion",grant.path("relationVersion").longValue()))),"reason","正式撤销"),201).path("id").stringValue();
        approve(revoke,1);assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(revoke);
        var denied=business(f);assertThat(denied.path("canAccessSystem").booleanValue()).isFalse();assertThat(denied.path("operations").isEmpty()).isTrue();assertThat(read("grants?employeeId="+f.employee()).path("items").get(0).path("status").stringValue()).isEqualTo("REVOKED");assertThat(read("audit-events?changeRequestId="+revoke).path("total").longValue()).isPositive();
    }
}
