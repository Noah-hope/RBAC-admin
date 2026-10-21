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
    private Path keys;private String base,admin;private RSAKey business;private final JsonMapper json=JsonMapper.builder().build();private final HttpClient http=HttpClient.newHttpClient();
    private static String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"delivered-modules-keys-");
        for(String family:List.of("admin","business","oa")){var key=new RSAKeyGenerator(2048).keyID(family).generate();if(family.equals("business"))business=key;Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));}
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);db=new IsolatedMySqlDatabase();
        app=db.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin","--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),"--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),"--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),"--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture","--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(secret))));
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/";
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
        assertThat(read("change-requests/"+draft).path("state").stringValue()).isEqualTo("DRAFT");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM authorization_source",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant",Long.class)).isZero();
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
}
