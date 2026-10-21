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
    private String[] applicationArgs;private Path keys,storage;private String base,admin,oaInstance;private RSAKey business,oa;private final JsonMapper json=JsonMapper.builder().build();private final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    private static String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"delivered-modules-keys-");
        storage=Files.createTempDirectory(Path.of(".tmp"),"be33-files-");
        for(String family:List.of("admin","business","oa")){var key=new RSAKeyGenerator(2048).keyID(family).generate();if(family.equals("business"))business=key;if(family.equals("oa"))oa=key;Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));}
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);db=new IsolatedMySqlDatabase();
        applicationArgs=new String[]{"--spring.profiles.active=dev,demo","--rbac.workflow.scheduling-enabled=false","--rbac.storage.import-directory="+storage.toAbsolutePath(),"--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000","--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin","--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),"--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),"--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),"--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture","--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(secret)))};app=db.startWeb(applicationArgs);
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/";
        oaInstance=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",oaInstance,oaInstance);
        String account=id();jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",account,account,new PasswordPolicy().encode("Delivered-Modules-1234"));
        admin=value(request("POST","admin/sessions",Map.of("loginName",account,"password","Delivered-Modules-1234"),null,null),201).path("accessToken").stringValue();
    }
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(db!=null)db.close();}finally{for(Path dir:List.of(keys,storage)){if(dir!=null)try(var files=Files.walk(dir)){for(var file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}}}}
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
        write("PUT","roles/"+role+"/metadata",metadata(),200);assertThat(read("roles/"+role).path("activeChangeRequestId").stringValue()).isEqualTo(draft);assertThat(read("change-requests/"+draft).path("draftVersion").longValue()).isEqualTo(1);
        long before=read("audit-events?changeRequestId="+draft).path("total").longValue();assertThat(before).isPositive();
        write("DELETE","change-requests/"+draft+"?expectedDraftVersion=1",null,204);assertThat(request("GET","admin/change-requests/"+draft,null,admin,null).statusCode()).isEqualTo(404);
        assertThat(read("audit-events?changeRequestId="+draft).path("total").longValue()).isGreaterThan(before);assertThat(request("GET","admin/roles/"+role,null,admin,null).statusCode()).isEqualTo(404);
    }
    @Test void unrelatedRoleMetadataDoesNotInvalidateCatalogPreview()throws Exception{
        var f=fixture();String role=role(f).path("role").path("id").stringValue();var input=catalogChange(f);var preview=write("POST","systems/"+f.system()+"/catalog-previews",input,200);
        write("PUT","roles/"+role+"/metadata",metadata(),200);var save=new LinkedHashMap<String,Object>(input);save.put("previewId",preview.path("id").stringValue());String key=id(),path="admin/systems/"+f.system()+"/catalog-changes";
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
    @Test void roleMetadataUpdateIsAvailableAtTheFrozenOpenApiPath()throws Exception {
        var f=fixture();String r=role(f).path("role").path("id").stringValue();
        var result=write("PUT","roles/"+r+"/metadata",metadata(),200);
        assertThat(result.path("metadataVersion").longValue()).isEqualTo(2);
        assertThat(read("roles/"+r).path("name").stringValue()).isEqualTo("集成角色改名");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM http_idempotency WHERE canonical_path=? AND state='COMPLETED'",Long.class,"/api/v1/admin/roles/"+r+"/metadata")).isEqualTo(1);
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
    private JsonNode submitAndDeliver(String draft,long version)throws Exception{
        var preview=write("POST","change-requests/"+draft+"/previews",Map.of("expectedDraftVersion",version),200);
        write("POST","change-requests/"+draft+"/submissions",Map.of("expectedDraftVersion",version,"previewId",preview.path("preview").path("id").stringValue()),202);
        assertThat(app.getBean(com.adgn.backend.oa.OaDeliveryService.class).deliver(draft)).isTrue();return read("change-requests/"+draft);
    }
    private Map<String,Object> callback(String draft,JsonNode fixed){
        return Map.<String,Object>of("eventId",id(),"requestId",draft,"oaApprovalId",fixed.path("oaApprovalId").stringValue(),"submittedVersion",fixed.path("submittedVersion").longValue(),"contentDigest",fixed.path("contentDigest").stringValue(),"decision","APPROVED","isFinal",true,"decidedAt",Instant.now().toString(),"comment","正式模拟OA最终结果");
    }
    private Map<String,Object> approve(String draft,long version)throws Exception{
        var fixed=submitAndDeliver(draft,version);
        var input=callback(draft,fixed);
        value(request("POST","oa/final-results",input,oaToken(),null),202);return input;
    }
    private String configuredRole(Fixture f)throws Exception{
        var created=role(f);String role=created.path("role").path("id").stringValue(),draft=created.path("draftId").stringValue();
        write("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",1,"reason","关闭来源集成配置","content",Map.of("kind","ROLE_CONFIGURATION","companyId",f.company(),"roleId",role,"expectedConfigurationVersion",0,"permissionIds",List.of(f.access()))),200);
        approve(draft,2);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(draft);return role;
    }
    private String grantDraft(Fixture f,String role)throws Exception{
        var b=write("POST","grant-baselines",Map.of("items",List.of(Map.of("employeeId",f.employee(),"roleId",role,"sourceCompanyId",f.company(),"sourceDepartmentId",f.department()))),200).path("items").get(0);
        var line=Map.of("employeeId",f.employee(),"roleId",role,"sourceCompanyId",f.company(),"sourceDepartmentId",f.department(),"expectedRoleConfigurationVersion",b.path("roleConfigurationVersion").longValue(),"expectedSourceEpoch",b.path("sourceEpoch").longValue(),"expectedRelationVersion",b.path("relationVersion").longValue(),"employmentIds",json.convertValue(b.path("employmentIds"),List.class));
        return write("POST","change-requests",Map.of("content",Map.of("kind","ROLE_GRANT","companyId",f.company(),"items",List.of(line)),"reason","关闭来源集成授予"),201).path("id").stringValue();
    }
    private JsonNode closeSource(Fixture f)throws Exception{
        var s=write("POST","source-baselines",Map.of("employeeId",f.employee(),"companyId",f.company(),"departmentId",f.department(),"systemIds",List.of(f.system())),200).path("items").get(0);
        var input=Map.of("employeeId",f.employee(),"companyId",f.company(),"departmentId",f.department(),"systems",List.of(Map.of("systemId",f.system(),"expectedSourceEpoch",s.path("sourceEpoch").longValue(),"expectedVersion",s.path("version").longValue())),"reason","关闭准确来源集成验证");
        var preview=write("POST","source-closure-previews",input,200);var body=Map.of("input",input,"previewId",preview.path("id").stringValue());String key=id();
        var result=value(request("POST","admin/source-closures",body,admin,key),200);
        var replay=request("POST","admin/source-closures",body,admin,key);value(replay,200);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(json.readTree(replay.body())).isEqualTo(result);return result;
    }
    // Caching old grants, or replaying an execution after close, must not restore access.
    @Test void sourceClosureRemovesExecutedAccessFromTheNextBusinessQuery()throws Exception{
        var f=fixture();String role=configuredRole(f),grant=grantDraft(f,role);var approved=approve(grant,1);
        app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(grant);assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();
        var closed=closeSource(f);assertThat(closed.path("invalidatedGrantCount").longValue()).isEqualTo(1);
        assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();value(request("POST","oa/final-results",approved,oaToken(),null),200);
        app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(grant);assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,grant)).isEqualTo(1);
    }
    // A trusted late approval is historical evidence, not permission to revive a closed epoch.
    @Test void lateApprovalAfterSourceClosureCannotGrantButANewEpochApplicationCan()throws Exception{
        var f=fixture();String role=configuredRole(f),grant=grantDraft(f,role);var fixed=submitAndDeliver(grant,1);
        var closed=closeSource(f);assertThat(closed.path("invalidatedRequestCount").longValue()).isEqualTo(1);
        var late=callback(grant,fixed);value(request("POST","oa/final-results",late,oaToken(),null),202);
        app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(grant);
        assertThat(read("change-requests/"+grant).path("state").stringValue()).isEqualTo("INVALIDATED");assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,grant)).isZero();
        String fresh=grantDraft(f,role);approve(fresh,1);assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
        app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(fresh);assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();
        value(request("POST","oa/final-results",late,oaToken(),null),200);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(grant);
        assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();assertThat(read("change-requests/"+grant).path("state").stringValue()).isEqualTo("INVALIDATED");
    }
    private JsonNode business(Fixture f)throws Exception{return value(request("POST","integration/authorization-queries",Map.of("employeeKey",employeeKey(f.employee()),"companyId",f.company()),f.businessToken(),null),200);}
    private String employeeKey(String employee){return jdbc.queryForObject("SELECT employee_key FROM employee WHERE id=?",String.class,employee);}

    // Public CSV upload/precheck/commit: no direct organization/people/grant inserts in these stories.
    private String uploaded(String csv)throws Exception {
        String boundary="fixture"+id();String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"story.csv\"\r\nContent-Type: text/csv\r\n\r\n"+csv+"\r\n--"+boundary+"--\r\n";
        return value(http.send(HttpRequest.newBuilder(URI.create(base+"admin/import-files")).header("Authorization","Bearer "+admin).header("Idempotency-Key",id()).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()),201).path("id").stringValue();
    }
    private String preparedImport(String type,String csv,String...fields)throws Exception {
        var in=Map.of("fileId",uploaded(csv),"sourceSystem","HR","sourceBatchId",id(),"importType",type,"worksheet","CSV","fieldMappings",Arrays.stream(fields).map(f->Map.of("sourceColumn",f,"targetField",f)).toList(),"organizationMappings",List.of(),"initialEmploymentState","EXPLICIT_ACTIVE","reason","BE33正式集成导入");
        var b=write("POST","import-batches",in,201);assertThat(b.path("state").stringValue()).as(b.toString()).isEqualTo("READY");return b.path("id").stringValue();
    }
    private JsonNode commitImport(String batch)throws Exception {
        var b=read("import-batches/"+batch);
        var result=write("POST","import-batches/"+batch+"/commits",Map.of("expectedVersion",b.path("version").longValue(),"contentDigest",b.path("contentDigest").stringValue()),200);
        assertThat(result.path("state").stringValue()).isEqualTo("SUCCEEDED");return result;
    }
    private String csvOrg(String department,String column){return jdbc.queryForObject("SELECT "+column+" FROM department WHERE id=?",String.class,department);}
    private String position(String department){return jdbc.queryForObject("SELECT id FROM `position` WHERE department_id=?",String.class,department);}
    private String positionCode(String department){return jdbc.queryForObject("SELECT code FROM `position` WHERE department_id=?",String.class,department);}
    private String companyCode(String company){return jdbc.queryForObject("SELECT code FROM company WHERE id=?",String.class,company);}
    private String businessToken(String sid)throws Exception {
        String subject=id();jdbc.update("INSERT INTO service_identity(id,subject,kind,system_id) VALUES(?,?,'BUSINESS',?)",id(),subject,sid);
        var now=Instant.now();var claims=new JWTClaimsSet.Builder().issuer("urn:rbac:business").audience("rbac-business").subject(subject).jwtID(id()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).claim("systemId",sid).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("business").build(),claims);jwt.sign(new RSASSASigner(business));return jwt.serialize();
    }
    private JsonNode enabledSystem()throws Exception {
        var s=write("POST","systems",Map.of("code","s_"+id(),"name","完整故事系统","reason","登记"),201);String sid=s.path("id").stringValue();
        var input=Map.of("expectedVersion",1,"name","完整故事系统","description","","enabled",true,"reason","启用");var p=write("POST","systems/"+sid+"/state-previews",input,200);write("PUT","systems/"+sid,Map.of("input",input,"previewId",p.path("id").stringValue()),200);return s;
    }
    private record Story(Fixture first,Fixture secondDepartment,Fixture secondSystem,Fixture otherCompany) {}
    private Story story()throws Exception {
        String n=id(),ca="A"+n,cb="B"+n;
        var result=commitImport(preparedImport("ORGANIZATION","companyName,departmentName,positionName\n"+ca+",研发,开发\n"+ca+",运营,专员\n"+cb+",研发,开发\n"+cb+",运营,专员\n","companyName","departmentName","positionName"));
        assertThat(result.path("actualCounts").path("created").longValue()).isEqualTo(10);
        String a=jdbc.queryForObject("SELECT id FROM company WHERE name=?",String.class,ca),b=jdbc.queryForObject("SELECT id FROM company WHERE name=?",String.class,cb);
        String a1=jdbc.queryForObject("SELECT id FROM department WHERE company_id=? AND name='研发'",String.class,a),a2=jdbc.queryForObject("SELECT id FROM department WHERE company_id=? AND name='运营'",String.class,a),b1=jdbc.queryForObject("SELECT id FROM department WHERE company_id=? AND name='研发'",String.class,b);
        String employee="employee_"+id();commitImport(preparedImport("EMPLOYEE_INITIALIZATION","employeeKey,name,companyCode,departmentCode,positionCode\n"+employee+",兼岗员工,"+companyCode(a)+","+csvOrg(a1,"code")+","+positionCode(a1)+"\n","employeeKey","name","companyCode","departmentCode","positionCode"));
        String e=jdbc.queryForObject("SELECT id FROM employee WHERE employee_key=?",String.class,employee);
        commitImport(preparedImport("PERSONNEL_CHANGES","action,employeeId,companyCode,departmentCode,positionCode\nADD_EMPLOYMENT,"+e+","+companyCode(a)+","+csvOrg(a2,"code")+","+positionCode(a2)+"\nADD_EMPLOYMENT,"+e+","+companyCode(b)+","+csvOrg(b1,"code")+","+positionCode(b1)+"\n","action","employeeId","companyCode","departmentCode","positionCode"));
        var sys=enabledSystem();var sys2=enabledSystem();String s1=sys.path("id").stringValue(),s2=sys2.path("id").stringValue(),t1=businessToken(s1),t2=businessToken(s2);
        return new Story(new Fixture(s1,sys.path("systemAccessPermissionId").stringValue(),a,a1,e,t1),new Fixture(s1,sys.path("systemAccessPermissionId").stringValue(),a,a2,e,t1),new Fixture(s2,sys2.path("systemAccessPermissionId").stringValue(),a,a1,e,t2),new Fixture(s1,sys.path("systemAccessPermissionId").stringValue(),b,b1,e,t1));
    }
    private String fullConfiguredRole(Fixture f,boolean shared)throws Exception {
        var permissionIds=jdbc.queryForObject("SELECT catalog_version FROM business_system WHERE id=?",Long.class,f.system())==1?functionalCatalog(f):jdbc.queryForList("SELECT id FROM permission WHERE system_id=? AND enabled=1 ORDER BY id",String.class,f.system());
        var input=new LinkedHashMap<String,Object>();input.put("companyId",f.company());input.put("departmentId",shared?null:f.department());input.put("systemId",f.system());input.put("name",shared?"公司级共享角色":"部门角色");input.put("reason","完整故事配置");
        var created=write("POST","roles",input,201);String r=created.path("role").path("id").stringValue(),draft=created.path("draftId").stringValue();
        write("PUT","change-requests/"+draft,Map.of("expectedDraftVersion",1,"reason","配置进入权限","content",Map.of("kind","ROLE_CONFIGURATION","companyId",f.company(),"roleId",r,"expectedConfigurationVersion",0,"permissionIds",permissionIds)),200);
        approve(draft,2);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(draft);assertThat(read("change-requests/"+draft).path("state").stringValue()).isEqualTo("EXECUTED");return r;
    }
    private String executedGrant(Fixture f,String role)throws Exception {String draft=grantDraft(f,role);approve(draft,1);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(draft);assertThat(read("change-requests/"+draft).path("state").stringValue()).isEqualTo("EXECUTED");return draft;}
    private JsonNode employeeState(Fixture f,String status)throws Exception {
        long version=jdbc.queryForObject("SELECT version FROM employee WHERE id=?",Long.class,f.employee());var input=Map.of("expectedVersion",version,"status",status,"reason","完整故事状态变更");
        var preview=write("POST","employees/"+f.employee()+"/state-previews",input,200);
        return write("PUT","employees/"+f.employee()+"/state",Map.of("input",input,"previewId",preview.path("id").stringValue()),200);
    }
    @Test void importedMultiCompanyMultiDepartmentMultiSystemStoryKeepsIndependentSources()throws Exception {
        var s=story();String shared=fullConfiguredRole(s.first(),true);String department=fullConfiguredRole(s.secondDepartment(),false);
        executedGrant(s.first(),shared);executedGrant(s.secondDepartment(),department);executedGrant(s.secondSystem(),fullConfiguredRole(s.secondSystem(),false));executedGrant(s.otherCompany(),fullConfiguredRole(s.otherCompany(),true));
        for(var f:List.of(s.first(),s.secondDepartment(),s.secondSystem(),s.otherCompany())){var allowed=business(f);assertThat(allowed.path("canAccessSystem").booleanValue()).isTrue();assertThat(allowed.path("operations").size()).isEqualTo(2);assertThat(allowed.path("menus").size()).isEqualTo(1);}
        closeSource(s.first());assertThat(business(s.first()).path("canAccessSystem").booleanValue()).isTrue();
        closeSource(s.secondDepartment());assertThat(business(s.first()).path("canAccessSystem").booleanValue()).isFalse();
        assertThat(business(s.secondSystem()).path("canAccessSystem").booleanValue()).isTrue();assertThat(business(s.otherCompany()).path("canAccessSystem").booleanValue()).isTrue();
        assertThat(read("employees/"+s.first().employee()+"/employments").path("total").longValue()).isEqualTo(3);
        String lateDraft=grantDraft(s.first(),shared);var fixed=submitAndDeliver(lateDraft,1);closeSource(s.first());var late=callback(lateDraft,fixed);
        value(request("POST","oa/final-results",late,oaToken(),null),202);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(lateDraft);assertThat(read("change-requests/"+lateDraft).path("state").stringValue()).isEqualTo("INVALIDATED");
        assertThat(business(s.first()).path("canAccessSystem").booleanValue()).isFalse();employeeState(s.first(),"INACTIVE");employeeState(s.first(),"ACTIVE");
        for(var f:List.of(s.first(),s.secondSystem(),s.otherCompany()))assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
        value(request("POST","oa/final-results",late,oaToken(),null),200);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(lateDraft);assertThat(business(s.first()).path("canAccessSystem").booleanValue()).isFalse();
        executedGrant(s.first(),shared);assertThat(business(s.first()).path("canAccessSystem").booleanValue()).isTrue();assertThat(business(s.secondSystem()).path("canAccessSystem").booleanValue()).isFalse();

    }
    @Test void importedEmployeeDisableRestoreRejectsTrustedLateApprovalAndDoesNotRestoreAccess()throws Exception {
        var s=story();var f=s.first();String role=fullConfiguredRole(f,true);executedGrant(f,role);String pending=grantDraft(s.secondDepartment(),role);var fixed=submitAndDeliver(pending,1);
        assertThat(employeeState(f,"INACTIVE").path("invalidatedGrantCount").longValue()).isEqualTo(1);assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
        employeeState(f,"ACTIVE");assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();value(request("POST","oa/final-results",callback(pending,fixed),oaToken(),null),202);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(pending);
        assertThat(read("change-requests/"+pending).path("state").stringValue()).isEqualTo("INVALIDATED");assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
        executedGrant(f,role);assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();
    }
    private String personnelBatch(Fixture f,String actions,String...fields)throws Exception{return preparedImport("PERSONNEL_CHANGES",actions,fields);}
    private String disableBatch(Fixture f)throws Exception{return personnelBatch(f,"action,employeeId,expectedVersion\nDISABLE_EMPLOYEE,"+f.employee()+","+jdbc.queryForObject("SELECT version FROM employee WHERE id=?",Long.class,f.employee())+"\n","action","employeeId","expectedVersion");}
    @Test void committedEndAndRehireCannotReviveApprovedGrantOrOldSourceButOtherDepartmentSurvives()throws Exception {
        var s=story();var f=s.first();String role=fullConfiguredRole(f,true);executedGrant(f,role);executedGrant(s.secondDepartment(),role);
        String pending=grantDraft(s.secondSystem(),fullConfiguredRole(s.secondSystem(),false));var fixed=submitAndDeliver(pending,1);
        String job=jdbc.queryForObject("SELECT id FROM employment WHERE employee_id=? AND department_id=? AND status='ACTIVE'",String.class,f.employee(),f.department());
        String csv="action,employeeId,employmentId,expectedVersion,companyCode,departmentCode,positionCode\nEND_EMPLOYMENT,"+f.employee()+","+job+",1,,,\nADD_EMPLOYMENT,"+f.employee()+",,,"+companyCode(f.company())+","+csvOrg(f.department(),"code")+","+positionCode(f.department())+"\n";
        var committed=commitImport(personnelBatch(f,csv,"action","employeeId","employmentId","expectedVersion","companyCode","departmentCode","positionCode"));assertThat(committed.path("actualCounts").path("invalidatedGrants").longValue()).isEqualTo(1);
        assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();assertThat(business(s.secondSystem()).path("canAccessSystem").booleanValue()).isFalse();
        value(request("POST","oa/final-results",callback(pending,fixed),oaToken(),null),202);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(pending);assertThat(read("change-requests/"+pending).path("state").stringValue()).isEqualTo("INVALIDATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employment WHERE employee_id=? AND department_id=? AND status='ACTIVE'",Long.class,f.employee(),f.department())).isEqualTo(1);
        executedGrant(s.secondSystem(),jdbc.queryForObject("SELECT role_id FROM change_target WHERE request_id=? LIMIT 1",String.class,pending));assertThat(business(s.secondSystem()).path("canAccessSystem").booleanValue()).isTrue();
    }
    @Test void failedFinalImportAuditRollsBackGrantAndRequestInvalidationThenFreshRetryClosesThem()throws Exception {
        var s=story();var f=s.first();String r=fullConfiguredRole(f,true);executedGrant(f,r);String pending=grantDraft(s.secondDepartment(),r);submitAndDeliver(pending,1);String batch=disableBatch(f);var before=read("import-batches/"+batch);String key=id();
        db.execute("CREATE TRIGGER be33_fail_import BEFORE INSERT ON audit_event FOR EACH ROW BEGIN IF NEW.event_type='IMPORT_COMMITTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'; END IF; END");
        try{value(request("POST","admin/import-batches/"+batch+"/commits",Map.of("expectedVersion",before.path("version").longValue(),"contentDigest",before.path("contentDigest").stringValue()),admin,key),503);}finally{db.execute("DROP TRIGGER be33_fail_import");}
        assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();assertThat(read("change-requests/"+pending).path("state").stringValue()).isEqualTo("PENDING");assertThat(read("import-batches/"+batch).path("state").stringValue()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM http_idempotency WHERE idempotency_key=? AND state='COMPLETED'",Long.class,key)).isZero();commitImport(batch);
        assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();assertThat(read("change-requests/"+pending).path("state").stringValue()).isEqualTo("INVALIDATED");
    }
    private HttpResponse<String> scoped(String method,String path,Object input,String token,JsonNode appointment)throws Exception {
        var b=HttpRequest.newBuilder(URI.create(base+"admin/"+path)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).header("Idempotency-Key",id()).header("X-Admin-Appointment-Id",appointment.path("id").stringValue()).header("X-Admin-Appointment-Version",appointment.path("version").asString()).header("X-Admin-Scope-Version",appointment.path("scopeVersion").asString());
        if(input!=null)b.header("Content-Type","application/json");return http.send(b.method(method,input==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private String manager(Fixture f)throws Exception {
        String password="Story-Account-123456",login="manager_"+id();write("POST","accounts",Map.of("employeeId",f.employee(),"loginName",login,"temporaryPassword",password,"confirmPassword",password,"reason","故事账号"),201);return login;
    }
    private JsonNode createScope(Fixture f,String login)throws Exception {
        String account=jdbc.queryForObject("SELECT id FROM admin_account WHERE login_name=?",String.class,login);var input=Map.of("companyId",f.company(),"departmentId",f.department(),"systemIds",List.of(f.system()),"accountIds",List.of(account),"reason","故事范围");var p=write("POST","management-scope-previews",input,200);return write("POST","management-scopes",Map.of("input",input,"previewId",p.path("id").stringValue()),201).path("scope");
    }
    private String managerLogin(String login,boolean initial)throws Exception {
        String password=initial?"Story-Account-123456":"Story-Account-Changed-123456";var s=value(request("POST","admin/sessions",Map.of("loginName",login,"password",password),null,null),201);String token=s.path("accessToken").stringValue();
        if(initial){value(request("PUT","admin/password",Map.of("currentPassword",password,"newPassword","Story-Account-Changed-123456","confirmPassword","Story-Account-Changed-123456"),token,null),204);return managerLogin(login,false);}return token;
    }
    private void replaceSystems(Fixture f,String scope,long version,List<String> systems,String login)throws Exception {
        String account=jdbc.queryForObject("SELECT id FROM admin_account WHERE login_name=?",String.class,login);var input=Map.of("expectedVersion",version,"companyId",f.company(),"departmentId",f.department(),"systemIds",systems,"accountIds",List.of(account),"reason","范围缩放");var p=write("POST","management-scopes/"+scope+"/previews",input,200);write("PUT","management-scopes/"+scope,Map.of("input",input,"previewId",p.path("id").stringValue()),200);
    }
    @Test void scopeShrinkAndExpandNeverRevivesManagerSubmittedFixedRequest()throws Exception {
        var s=story();var f=s.first();String role=fullConfiguredRole(f,false),login=manager(f);var scope=createScope(f,login);String scopeId=scope.path("id").stringValue(),token=managerLogin(login,true);
        var appointments=value(request("GET","admin/my-appointments",null,token,null),200);var ap=appointments.path("items").get(0);
        var baseline=value(scoped("POST","grant-baselines",Map.of("items",List.of(Map.of("employeeId",f.employee(),"roleId",role,"sourceCompanyId",f.company(),"sourceDepartmentId",f.department()))),token,ap),200).path("items").get(0);
        var line=Map.of("employeeId",f.employee(),"roleId",role,"sourceCompanyId",f.company(),"sourceDepartmentId",f.department(),"expectedRoleConfigurationVersion",baseline.path("roleConfigurationVersion").longValue(),"expectedSourceEpoch",baseline.path("sourceEpoch").longValue(),"expectedRelationVersion",baseline.path("relationVersion").longValue(),"employmentIds",json.convertValue(baseline.path("employmentIds"),List.class));
        String draft=value(scoped("POST","change-requests",Map.of("content",Map.of("kind","ROLE_GRANT","companyId",f.company(),"items",List.of(line)),"reason","原范围固定申请"),token,ap),201).path("id").stringValue();
        var preview=value(scoped("POST","change-requests/"+draft+"/previews",Map.of("expectedDraftVersion",1),token,ap),200);value(scoped("POST","change-requests/"+draft+"/submissions",Map.of("expectedDraftVersion",1,"previewId",preview.path("preview").path("id").stringValue()),token,ap),202);app.getBean(com.adgn.backend.oa.OaDeliveryService.class).deliver(draft);var fixed=read("change-requests/"+draft);
        replaceSystems(f,scopeId,1,List.of(),login);assertThat(scoped("GET","roles",null,token,ap).statusCode()).isEqualTo(409);replaceSystems(f,scopeId,2,List.of(f.system()),login);
        value(request("POST","oa/final-results",callback(draft,fixed),oaToken(),null),202);app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(draft);assertThat(read("change-requests/"+draft).path("state").stringValue()).isEqualTo("INVALIDATED");assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
        executedGrant(f,role);assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();
    }
    // Both tests hold a named-lock barrier inside an actual write transaction while the competing connection waits for guard.
    private void importExecutionRace(boolean executionFirst)throws Exception {
        var s=story();var f=s.first();String role=fullConfiguredRole(f,true),draft=grantDraft(f,role);approve(draft,1);String batch=disableBatch(f),lock="be33_"+id().replace("-","");
        String trigger="be33_barrier";db.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON "+(executionFirst?"execution_success":"audit_event")+" FOR EACH ROW BEGIN "+(executionFirst?"":"IF NEW.event_type='IMPORT_COMMITTED' THEN ")+"DO GET_LOCK('"+lock+"',15); DO RELEASE_LOCK('"+lock+"'); "+(executionFirst?"":"END IF;")+" END");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var blocker=db.runtimeConnection()) {
            try(var st=blocker.createStatement()){st.execute("SELECT GET_LOCK('"+lock+"',0)");}
            java.util.concurrent.Callable<Void> execution=()->{app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(draft);return null;};java.util.concurrent.Callable<Void> importing=()->{if(executionFirst){var b=read("import-batches/"+batch);value(request("POST","admin/import-batches/"+batch+"/commits",Map.of("expectedVersion",b.path("version").longValue(),"contentDigest",b.path("contentDigest").stringValue()),admin,id()),409);assertThat(read("import-batches/"+batch).path("state").stringValue()).isEqualTo("STALE");}else commitImport(batch);return null;};
            var first=pool.submit(executionFirst?execution:importing);
            try {
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(12);while(Integer.parseInt(db.scalar("SELECT COUNT(*) FROM information_schema.processlist WHERE STATE='User lock'"))==0&&System.nanoTime()<deadline)Thread.sleep(10);
                assertThat(Integer.parseInt(db.scalar("SELECT COUNT(*) FROM information_schema.processlist WHERE STATE='User lock'"))).isPositive();var second=pool.submit(executionFirst?importing:execution);
                assertThatThrownBy(()->second.get(150,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();try(var st=blocker.createStatement()){st.execute("SELECT RELEASE_LOCK('"+lock+"')");}
                first.get(25,java.util.concurrent.TimeUnit.SECONDS);second.get(25,java.util.concurrent.TimeUnit.SECONDS);
            } finally {try(var st=blocker.createStatement()){st.execute("SELECT RELEASE_LOCK('"+lock+"')");}}
        } finally {pool.shutdownNow();pool.awaitTermination(25,java.util.concurrent.TimeUnit.SECONDS);db.execute("DROP TRIGGER "+trigger);}
        if(executionFirst){assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();commitImport(disableBatch(f));}
        assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id JOIN authorization_source s ON s.id=b.source_id WHERE s.employee_id=? AND g.status='ACTIVE'",Long.class,f.employee())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isEqualTo(executionFirst?1:0);assertThat(read("change-requests/"+draft).path("state").stringValue()).isEqualTo(executionFirst?"EXECUTED":"INVALIDATED");
    }
    @Test void executionWinningGuardThenImportDisableLeavesNoAccess()throws Exception {importExecutionRace(true);}
    @Test void importDisableWinningGuardPreventsApprovedExecution()throws Exception {importExecutionRace(false);}
    @Test void employeeRestoreDoesNotRestoreManagerSessionOrAppointment()throws Exception {
        var s=story();var f=s.first();String login=manager(f);createScope(f,login);String token=managerLogin(login,true);value(request("GET","admin/sessions/current",null,token,null),200);
        employeeState(f,"INACTIVE");assertThat(request("GET","admin/sessions/current",null,token,null).statusCode()).isEqualTo(401);employeeState(f,"ACTIVE");
        assertThat(request("GET","admin/sessions/current",null,token,null).statusCode()).isEqualTo(401);value(request("POST","admin/sessions",Map.of("loginName",login,"password","Story-Account-Changed-123456"),null,null),401);
        createScope(f,login);String fresh=managerLogin(login,false);value(request("GET","admin/sessions/current",null,fresh,null),200);assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();
    }
    @Test void failedApprovedExecutionRollsBackGrantAndStartupRecoveryExecutesExactlyOnce()throws Exception {
        var s=story();var f=s.first();String role=fullConfiguredRole(f,false),draft=grantDraft(f,role);approve(draft,1);
        db.execute("CREATE TRIGGER be33_execution_failure BEFORE INSERT ON execution_success FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
        try{app.getBean(com.adgn.backend.execution.ExecutionService.class).execute(draft);}finally{db.execute("DROP TRIGGER be33_execution_failure");}
        assertThat(read("change-requests/"+draft).path("state").stringValue()).isEqualTo("EXECUTION_FAILED");assertThat(business(f).path("canAccessSystem").booleanValue()).isFalse();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isZero();
        // Advance only the retry clock; approved content, claims, targets and business state are untouched.
        jdbc.update("UPDATE change_request SET next_retry_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",draft);
        app.close();app=db.startWeb(Arrays.stream(applicationArgs).map(a->a.equals("--rbac.workflow.scheduling-enabled=false")?"--rbac.workflow.scheduling-enabled=true":a).toArray(String[]::new));refreshWeb();
        assertThat(read("change-requests/"+draft).path("state").stringValue()).isEqualTo("EXECUTED");assertThat(business(f).path("canAccessSystem").booleanValue()).isTrue();app.getBean(com.adgn.backend.execution.ExecutionService.class).scan();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_success WHERE request_id=?",Long.class,draft)).isEqualTo(1);
        app.close();app=db.startWeb(applicationArgs);refreshWeb();
    }
    private void refreshWeb(){jdbc=new JdbcTemplate(app.getBean(DataSource.class));base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/";}
    @Test void everyDeclaredOperationRejectsMissingCredentialsAtItsPublicBoundary()throws Exception {
        var operations=json.readTree(Files.readString(Path.of("../docs/05-detailed-design/operation-map.json")));int checked=0;
        for(var op:operations){if(!op.path("direction").stringValue().equals("INBOUND"))continue;
            String path=op.path("path").stringValue().replaceAll("\\{[^}]+}",id()),method=op.path("method").stringValue(),operation=op.path("operationId").stringValue();
            int expected=operation.equals("login")?422:operation.equals("logout")?204:401;
            var response=request(method,path.substring("/api/v1/".length()),method.equals("GET")||method.equals("DELETE")?null:Map.of(),null,null);
            assertThat(response.statusCode()).as(operation).isEqualTo(expected);checked++;
        }
        assertThat(checked).isEqualTo(67);
    }
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
