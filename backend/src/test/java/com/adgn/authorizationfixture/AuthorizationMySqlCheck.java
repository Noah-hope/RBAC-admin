package com.adgn.authorizationfixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationMySqlCheck {
    private IsolatedMySqlDatabase database;private ConfigurableApplicationContext app;private JdbcTemplate jdbc;
    private Path keys;private String base,account;private RSAKey business,oa;private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newHttpClient();
    private static String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"authorization-keys-");
        var admin=new RSAKeyGenerator(2048).keyID("admin").generate();business=new RSAKeyGenerator(2048).keyID("business").generate();oa=new RSAKeyGenerator(2048).keyID("oa").generate();
        Files.writeString(keys.resolve("admin.json"),new JWKSet(admin).toString(false));
        Files.writeString(keys.resolve("business.json"),new JWKSet(business.toPublicJWK()).toString());Files.writeString(keys.resolve("oa.json"),new JWKSet(oa.toPublicJWK()).toString());
        database=new IsolatedMySqlDatabase();app=database.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
            "--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),"--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),"--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath());
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();account=id();
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,'fixture',1,0,UTC_TIMESTAMP(6))",account,account);
    }
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(database!=null)database.close();}finally{if(keys!=null){for(String f:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(f+".json"));Files.deleteIfExists(keys);}}}}
    private record Fixture(String system,String access,String company,String department,String employee,String employment,String token,String service){ }
    private Fixture fixture()throws Exception{
        String s=id(),a=id(),c=id(),d=id(),e=id(),em=id(),p=id(),service=id(),subject=id();
        jdbc.update("INSERT INTO business_system(id,code,name,enabled,created_at) VALUES(?,?, '系统',1,UTC_TIMESTAMP(6))",s,s);
        jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,created_at) VALUES(?,?, 'entry','进入','SYSTEM_ACCESS',UTC_TIMESTAMP(6))",a,s);
        jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'公司',UTC_TIMESTAMP(6))",c,c);
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'部门',UTC_TIMESTAMP(6))",d,c,d);
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'岗位',UTC_TIMESTAMP(6))",p,d,p);
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'员工',UTC_TIMESTAMP(6))",e,e);
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",em,e,c,d,p);
        jdbc.update("INSERT INTO service_identity(id,subject,kind,system_id) VALUES(?,?,'BUSINESS',?)",service,subject,s);
        return new Fixture(s,a,c,d,e,em,token(subject,s,false),service);
    }
    private String token(String subject,String binding,boolean isOa)throws Exception{
        var now=Instant.now();var claims=new JWTClaimsSet.Builder().issuer(isOa?"urn:rbac:oa":"urn:rbac:business").audience(isOa?"rbac-oa-callback":"rbac-business")
            .subject(subject).jwtID(id()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).claim(isOa?"oaInstanceId":"systemId",binding).build();
        var key=isOa?oa:business;var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),claims);jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    private HttpResponse<String> call(String path,Object input,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+"/api/v1/integration/"+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("X-Request-Id","be18-check");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return http.send(b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode query(Fixture f)throws Exception{var r=call("authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company()),f.token());assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    private JsonNode check(Fixture f,String code)throws Exception{var r=call("permission-checks",Map.of("employeeKey",f.employee(),"companyId",f.company(),"permissionCode",code),f.token());assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    private String node(Fixture f,String type,String parent){String n=id();jdbc.update("INSERT INTO directory_node(id,system_id,code,name,node_type,parent_id,enabled,created_at) VALUES(?,?,?,'功能',?,?,0,UTC_TIMESTAMP(6))",n,f.system(),n,type,parent);return n;}
    private String permission(Fixture f,String function,String code,boolean menu){String p=id();jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,function_id,menu_eligible,created_at) VALUES(?,?,?,?,'FUNCTION_OPERATION',?,?,UTC_TIMESTAMP(6))",p,f.system(),code,code,function,menu);return p;}
    private String role(Fixture f,String...permissions){String r=id();jdbc.update("INSERT INTO business_role(id,code,name,company_id,department_id,system_id,state,configuration_version,created_at) VALUES(?,?,'角色',?,?,?,'EFFECTIVE',1,UTC_TIMESTAMP(6))",r,r,f.company(),f.department(),f.system());for(String p:permissions)jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",r,p,f.system());return r;}
    private String grant(Fixture f,String role){
        String source=id(),baseline=id(),grant=id(),request=id();var existing=jdbc.queryForList("SELECT id FROM authorization_source WHERE employee_id=? AND company_id=? AND department_id=? AND system_id=?",String.class,f.employee(),f.company(),f.department(),f.system());
        if(existing.isEmpty())jdbc.update("INSERT INTO authorization_source(id,employee_id,company_id,department_id,system_id) VALUES(?,?,?,?,?)",source,f.employee(),f.company(),f.department(),f.system());else source=existing.getFirst();
        jdbc.update("INSERT INTO grant_baseline(id,source_id,role_id,company_id,system_id,relation_version) VALUES(?,?,?,?,?,1)",baseline,source,role,f.company(),f.system());
        jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,'ROLE_GRANT',?,'{}','夹具授予',UTC_TIMESTAMP(6))",request,f.company());
        jdbc.update("INSERT INTO change_target(request_id,line_no,line_kind,company_id,system_id,department_id,employee_id,role_id,source_id,baseline_id,expected_configuration_version,expected_source_epoch,expected_relation_version,target_snapshot) VALUES(?,0,'ROLE_GRANT',?,?,?,?,?,?,?,1,0,0,'{}')",request,f.company(),f.system(),f.department(),f.employee(),role,source,baseline);
        jdbc.update("UPDATE change_request SET state='PENDING',delivery_status='PENDING_SEND',submitted_version=1,submitted_at=UTC_TIMESTAMP(6),submitted_by_account_id=?,authority_type='GLOBAL',original_authority='{}',fixed_payload_bytes=X'7B7D',content_digest=UNHEX(REPEAT('00',32)) WHERE id=?",account,request);
        String instance=id(),event=id(),audit=id();jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",instance,instance);
        jdbc.update("INSERT INTO oa_link(request_id,oa_instance_id,oa_approval_id,accepted_at,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",request,instance,id());
        jdbc.update("INSERT INTO oa_callback_event(id,oa_instance_id,event_id,request_id,payload_digest,safe_payload,received_at) VALUES(?,?,?,?,UNHEX(REPEAT('00',32)),'{}',UTC_TIMESTAMP(6))",event,instance,id(),request);
        jdbc.update("INSERT INTO oa_final_result(request_id,first_event_id,decision,decided_at,comment,received_at) VALUES(?,?,'APPROVED',UTC_TIMESTAMP(6),'夹具',UTC_TIMESTAMP(6))",request,event);
        jdbc.update("UPDATE change_request SET state='APPROVED',delivery_status='LINKED',approval_result='APPROVED',execution_status='PENDING' WHERE id=?",request);
        jdbc.update("INSERT INTO audit_event(id,occurred_at,event_type,actor_kind,actor_id,target_id,visibility,result,correlation_id,reason,event_snapshot) VALUES(?,UTC_TIMESTAMP(6),'FIXTURE_EXECUTED','ACCOUNT',?,?,'GLOBAL','SUCCEEDED',?,'夹具','{\"schemaVersion\":1}')",audit,account,request,id());
        jdbc.update("INSERT INTO execution_success(request_id,audit_event_id,executed_at,result_snapshot) VALUES(?,?,UTC_TIMESTAMP(6),'{}')",request,audit);jdbc.update("UPDATE change_request SET state='EXECUTED',execution_status='SUCCEEDED' WHERE id=?",request);
        jdbc.update("INSERT INTO role_grant(id,baseline_id,relation_version,source_epoch,granted_by_request_id,created_at) VALUES(?,?,1,0,?,UTC_TIMESTAMP(6))",grant,baseline,request);
        jdbc.update("INSERT INTO grant_employment_evidence(grant_id,employment_id) VALUES(?,?)",grant,f.employment());return grant;
    }
    @Test void emptyEmployeeAndUnknownCodesAreSuccessfulDenials()throws Exception{
        var f=fixture();var q=query(f);assertThat(q.path("canAccessSystem").booleanValue()).isFalse();assertThat(q.path("menus").isEmpty()).isTrue();assertThat(q.path("operations").isEmpty()).isTrue();
        assertThat(q.path("requestId").stringValue()).isEqualTo("be18-check");assertThat(q.path("systemId").stringValue()).isEqualTo(f.system());assertThat(q.path("evaluatedAt").stringValue()).isNotBlank();
        var c=check(f,"missing");assertThat(c.path("hasPermission").booleanValue()).isFalse();assertThat(c.path("permissionType").isNull()).isTrue();
    }
    @Test void editDoesNotInventMenuAndEntryMayComeFromAnotherRole()throws Exception{
        var f=fixture();String root=node(f,"NAVIGATION",null),function=node(f,"FUNCTION",root),view=permission(f,function,"view",true),edit=permission(f,function,"edit",false);
        jdbc.update("UPDATE directory_node SET enabled=1 WHERE id=?",root);jdbc.update("UPDATE directory_node SET visibility_permission_id=?,enabled=1 WHERE id=?",view,function);
        grant(f,role(f,edit));assertThat(check(f,"edit").path("hasPermission").booleanValue()).isFalse();grant(f,role(f,f.access()));
        var q=query(f);assertThat(q.path("canAccessSystem").booleanValue()).isTrue();assertThat(q.path("operations").size()).isEqualTo(1);assertThat(q.path("menus").isEmpty()).isTrue();assertThat(check(f,"edit").path("hasPermission").booleanValue()).isTrue();assertThat(check(f,"view").path("hasPermission").booleanValue()).isFalse();
        grant(f,role(f,view,edit));q=query(f);assertThat(q.path("operations").size()).isEqualTo(2);assertThat(q.path("menus").size()).isEqualTo(2);
        assertThat(q.path("menus").get(0).path("id").stringValue()).isEqualTo(root);assertThat(q.path("menus").get(0).path("containerOnly").booleanValue()).isTrue();assertThat(q.path("menus").get(1).path("containerOnly").booleanValue()).isFalse();
        assertThat(check(f,"entry").path("permissionType").stringValue()).isEqualTo("SYSTEM_ACCESS");assertThat(check(f,"entry").path("hasPermission").booleanValue()).isTrue();
    }
    @Test void disabledAncestorAndOperationFilterCurrentQueries()throws Exception{
        var f=fixture();String root=node(f,"NAVIGATION",null),fn=node(f,"FUNCTION",root),p=permission(f,fn,"view",true);jdbc.update("UPDATE directory_node SET visibility_permission_id=?,enabled=1 WHERE id=?",p,fn);grant(f,role(f,f.access(),p));
        assertThat(query(f).path("operations").isEmpty()).isTrue();jdbc.update("UPDATE directory_node SET enabled=1 WHERE id=?",root);assertThat(query(f).path("operations").size()).isEqualTo(1);
        jdbc.update("UPDATE permission SET enabled=0 WHERE id=?",p);assertThat(query(f).path("operations").isEmpty()).isTrue();assertThat(check(f,"view").path("permissionType").stringValue()).isEqualTo("FUNCTION_OPERATION");
    }
    @Test void staleSourceAndRelationVersionsCannotAuthorizeNewQueries()throws Exception{
        var f=fixture();grant(f,role(f,f.access()));assertThat(query(f).path("canAccessSystem").booleanValue()).isTrue();
        jdbc.update("UPDATE grant_baseline SET relation_version=relation_version+1 WHERE system_id=?",f.system());assertThat(query(f).path("canAccessSystem").booleanValue()).isFalse();
        var g=fixture();grant(g,role(g,g.access()));jdbc.update("UPDATE authorization_source SET source_epoch=source_epoch+1,source_version=source_version+1 WHERE system_id=?",g.system());assertThat(query(g).path("canAccessSystem").booleanValue()).isFalse();
    }
    @Test void unknownCompanyEmployeeAndInactiveEmploymentReturnFalse()throws Exception{
        var f=fixture();grant(f,role(f,f.access()));for(var input:List.of(Map.of("employeeKey",id(),"companyId",f.company()),Map.of("employeeKey",f.employee(),"companyId",id()))){var r=call("authorization-queries",input,f.token());assertThat(r.statusCode()).isEqualTo(200);assertThat(json.readTree(r.body()).path("canAccessSystem").booleanValue()).isFalse();}
        jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6) WHERE id=?",f.employment());assertThat(query(f).path("canAccessSystem").booleanValue()).isFalse();
    }
    @Test void serviceIsCurrentAndDisabledSystemIsAnErrorNotDenial()throws Exception{
        var f=fixture();jdbc.update("UPDATE business_system SET enabled=0 WHERE id=?",f.system());assertThat(call("authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company()),f.token()).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE service_identity SET enabled=0 WHERE id=?",f.service());assertThat(call("permission-checks",Map.of("employeeKey",f.employee(),"companyId",f.company(),"permissionCode","entry"),f.token()).statusCode()).isEqualTo(401);
    }
    @Test void inputCannotSelectOtherSystemOrSupplyBusinessRecords()throws Exception{
        var f=fixture();for(String extra:List.of("systemId","ownerId","recordId","page")){var r=call("authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company(),extra,id()),f.token());assertThat(r.statusCode()).as(r.body()).isEqualTo(422);}
        assertThat(call("permission-checks",Map.of("employeeKey",f.employee(),"companyId",f.company(),"permissionCode","bad code"),f.token()).statusCode()).isEqualTo(422);
        assertThat(call("authorization-queries",Map.of("employeeKey",f.employee()),f.token()).statusCode()).isEqualTo(422);
        assertThat(call("authorization-queries",Map.of(),null).statusCode()).isEqualTo(401);assertThat(call("authorization-queries",Map.of(),token(id(),id(),true)).statusCode()).isEqualTo(401);
    }
    @Test void completeOperationsAreNotCappedAtOneHundred()throws Exception{
        var f=fixture();String fn=node(f,"FUNCTION",null),view=permission(f,fn,"view",true);jdbc.update("UPDATE directory_node SET visibility_permission_id=?,enabled=1 WHERE id=?",view,fn);
        var ps=new ArrayList<String>();ps.add(f.access());ps.add(view);for(int n=0;n<120;n++)ps.add(permission(f,fn,"op"+n,false));grant(f,role(f,ps.toArray(String[]::new)));
        var q=query(f);assertThat(q.path("operations").size()).isEqualTo(121);assertThat(q.path("menus").size()).isEqualTo(1);assertThat(check(f,"op119").path("hasPermission").booleanValue()).isTrue();
    }
    private Fixture anotherSource(Fixture f)throws Exception{
        String d=id(),p=id(),em=id();jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'兼岗部门',UTC_TIMESTAMP(6))",d,f.company(),d);
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'兼岗',UTC_TIMESTAMP(6))",p,d,p);
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",em,f.employee(),f.company(),d,p);
        return new Fixture(f.system(),f.access(),f.company(),d,f.employee(),em,f.token(),f.service());
    }
    @Test void sourcesAreUnionedAndClosingOneDoesNotRemoveOtherDepartment()throws Exception{
        var f=fixture();var other=anotherSource(f);String shared=id();jdbc.update("INSERT INTO business_role(id,code,name,company_id,system_id,state,configuration_version,created_at) VALUES(?,?,'共享',?,?,'EFFECTIVE',1,UTC_TIMESTAMP(6))",shared,shared,f.company(),f.system());
        jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",shared,f.access(),f.system());grant(f,shared);grant(other,shared);
        jdbc.update("UPDATE authorization_source SET source_epoch=source_epoch+1,source_version=source_version+1 WHERE department_id=?",f.department());assertThat(query(f).path("canAccessSystem").booleanValue()).isTrue();
        jdbc.update("UPDATE authorization_source SET source_epoch=source_epoch+1,source_version=source_version+1 WHERE department_id=?",other.department());assertThat(query(f).path("canAccessSystem").booleanValue()).isFalse();
    }
    @Test void departmentRoleAndSourceDepartmentsBothNeedCurrentEmployment()throws Exception{
        var f=fixture();var other=anotherSource(f);String role=role(f,f.access());grant(other,role);assertThat(query(f).path("canAccessSystem").booleanValue()).isTrue();
        jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6) WHERE id=?",f.employment());assertThat(query(f).path("canAccessSystem").booleanValue()).isFalse();
    }
    @Test void inactiveEmployeesAndUnexecutedRolesDoNotAddPermissions()throws Exception{
        var f=fixture();grant(f,role(f,f.access()));jdbc.update("UPDATE employee SET status='INACTIVE' WHERE id=?",f.employee());assertThat(query(f).path("canAccessSystem").booleanValue()).isFalse();
        var g=fixture();String r=role(g,g.access());grant(g,r);jdbc.update("UPDATE business_role SET state='UNCONFIGURED',configuration_version=0 WHERE id=?",r);assertThat(query(g).path("canAccessSystem").booleanValue()).isFalse();
    }
    @Test void anotherCompanyCannotProvideEntryForCurrentCompanyOperations()throws Exception{
        var f=fixture();grant(f,role(f,f.access()));var g=fixture();var r=call("permission-checks",Map.of("employeeKey",f.employee(),"companyId",g.company(),"permissionCode","entry"),f.token());
        assertThat(r.statusCode()).isEqualTo(200);assertThat(json.readTree(r.body()).path("hasPermission").booleanValue()).isFalse();
        String subject=jdbc.queryForObject("SELECT subject FROM service_identity WHERE id=?",String.class,f.service());
        assertThat(call("authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company()),token(subject,g.system(),false)).statusCode()).isEqualTo(401);
    }
    @Test void corruptDirectoryAndMissingGrantEvidenceAreErrorsNotDenials()throws Exception{
        var f=fixture();String root=node(f,"NAVIGATION",null),child=node(f,"NAVIGATION",root);jdbc.update("UPDATE directory_node SET parent_id=? WHERE id=?",child,root);
        var r=call("authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company()),f.token());assertThat(r.statusCode()).as(r.body()).isEqualTo(500);assertThat(json.readTree(r.body()).path("code").stringValue()).isEqualTo("DATA_INTEGRITY_ERROR");
        var g=fixture();String granted=grant(g,role(g,g.access()));
        database.execute("DROP TRIGGER tr_grant_employment_evidence_delete");try{jdbc.update("DELETE FROM grant_employment_evidence WHERE grant_id=?",granted);}finally{database.execute("CREATE TRIGGER tr_grant_employment_evidence_delete BEFORE DELETE ON grant_employment_evidence FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'");}
        assertThat(call("permission-checks",Map.of("employeeKey",g.employee(),"companyId",g.company(),"permissionCode","entry"),g.token()).statusCode()).isEqualTo(500);
    }
    @Test void requiredParentFunctionIsContainerWithoutItsOwnViewGrant()throws Exception{
        var f=fixture();String parent=node(f,"FUNCTION",null),pv=permission(f,parent,"parent-view",true),child=node(f,"FUNCTION",parent),cv=permission(f,child,"child-view",true);
        jdbc.update("UPDATE directory_node SET visibility_permission_id=?,enabled=1 WHERE id=?",pv,parent);jdbc.update("UPDATE directory_node SET visibility_permission_id=?,enabled=1 WHERE id=?",cv,child);grant(f,role(f,f.access(),cv));
        var q=query(f);assertThat(q.path("menus").size()).isEqualTo(2);assertThat(q.path("menus").get(0).path("containerOnly").booleanValue()).isTrue();assertThat(q.path("operations").size()).isEqualTo(1);assertThat(check(f,"parent-view").path("hasPermission").booleanValue()).isFalse();
        var r=call("authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company()),f.token());assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
    }
    @Test void aCommittedChangeCannotSplitAnAlreadyEstablishedSnapshot()throws Exception{
        var f=fixture();grant(f,role(f,f.access()));var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean();
        var data=new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(app.getBean(DataSource.class)){
            @Override public java.sql.Connection getConnection()throws java.sql.SQLException{
                var connection=super.getConnection();
                return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(p,m,args)->{
                    if(m.getName().equals("prepareStatement")&&args[0].toString().startsWith("SELECT id,code,enabled FROM business_system")&&once.compareAndSet(false,true)){entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("snapshot test timed out");}
                    try{return m.invoke(connection,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
            }
        };
        var service=new com.adgn.backend.query.authorization.AuthorizationService(data,app.getBean(com.adgn.backend.core.transaction.ReadBoundary.class),app.getBean(com.adgn.backend.identity.service.CurrentIdentityReader.class),Clock.systemUTC());
        var jwt=app.getBean(com.adgn.backend.identity.service.IdentityService.class).verify(f.token(),com.adgn.backend.identity.security.IdentityTokens.Family.BUSINESS);
        var input=json.valueToTree(Map.of("employeeKey",f.employee(),"companyId",f.company()));
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var pending=executor.submit(()->service.query(jwt,input,"snapshot"));try{assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();jdbc.update("UPDATE permission SET enabled=0 WHERE id=?",f.access());}finally{release.countDown();}
            assertThat(pending.get(10,java.util.concurrent.TimeUnit.SECONDS).canAccessSystem()).isTrue();
        }
        assertThat(query(f).path("canAccessSystem").booleanValue()).isFalse();
    }
    @Test void storageConnectionFailureIsNotReturnedAsPermissionFalse()throws Exception{
        var f=fixture();var data=new org.springframework.jdbc.datasource.AbstractDataSource(){@Override public java.sql.Connection getConnection()throws java.sql.SQLException{throw new java.sql.SQLTransientConnectionException("test unavailable","08001");}@Override public java.sql.Connection getConnection(String u,String p)throws java.sql.SQLException{return getConnection();}};
        var service=new com.adgn.backend.query.authorization.AuthorizationService(data,app.getBean(com.adgn.backend.core.transaction.ReadBoundary.class),app.getBean(com.adgn.backend.identity.service.CurrentIdentityReader.class),Clock.systemUTC());
        var jwt=app.getBean(com.adgn.backend.identity.service.IdentityService.class).verify(f.token(),com.adgn.backend.identity.security.IdentityTokens.Family.BUSINESS);
        assertThatThrownBy(()->service.query(jwt,json.valueToTree(Map.of("employeeKey",f.employee(),"companyId",f.company())),"failure"))
            .isInstanceOfSatisfying(com.adgn.backend.core.transaction.PersistenceFailure.class,e->assertThat(e.kind()).isEqualTo(com.adgn.backend.core.transaction.PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE));
    }
    @Test void fullResponseLimitDoesNotTruncateOrPreventIndividualCheck()throws Exception{
        var f=fixture();String fn=node(f,"FUNCTION",null),view=permission(f,fn,"view",true);jdbc.update("UPDATE directory_node SET visibility_permission_id=?,enabled=1 WHERE id=?",view,fn);String role=role(f,f.access(),view);
        var permissions=new ArrayList<Object[]>();var selections=new ArrayList<Object[]>();for(int n=0;n<10000;n++){String p=id();permissions.add(new Object[]{p,f.system(),"large"+n,"操作"+n,fn});selections.add(new Object[]{role,p,f.system()});}
        jdbc.batchUpdate("INSERT INTO permission(id,system_id,code,name,permission_type,function_id,created_at) VALUES(?,?,?,?,'FUNCTION_OPERATION',?,UTC_TIMESTAMP(6))",permissions);
        jdbc.batchUpdate("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",selections);grant(f,role);
        assertThat(call("authorization-queries",Map.of("employeeKey",f.employee(),"companyId",f.company()),f.token()).statusCode()).isEqualTo(503);
        assertThat(check(f,"large9999").path("hasPermission").booleanValue()).isTrue();
    }
}
