package com.adgn.grantfixture;

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
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrantQueriesMySqlCheck {
    @Test void inconsistentGrantProjectionFailsInsteadOfReturningAFalsePageTotal()throws Exception{
        var f=data();String source=source(f,f.department(),f.system(),0,0),baseline=baseline(f,source,f.role(),1);
        String grant=grant(f,baseline,1,0,"ACTIVE",f.employment());
        var reader=org.mockito.Mockito.spy(app.getBean(com.adgn.backend.grant.GrantReader.class));
        org.mockito.Mockito.doReturn(Optional.empty()).when(reader).grant(grant);
        var queries=new com.adgn.backend.grant.GrantQueries(app.getBean(DataSource.class),app.getBean(com.adgn.backend.core.transaction.ReadBoundary.class),
                app.getBean(com.adgn.backend.scope.service.ManagementContextResolver.class),reader);
        var jwt=app.getBean(com.adgn.backend.identity.security.IdentityTokens.class).verify(token,com.adgn.backend.identity.security.IdentityTokens.Family.ADMIN);
        assertThatThrownBy(()->queries.list(jwt,new com.adgn.backend.scope.service.ManagementContextResolver.Selection(null,null,null),"1","20",
                new com.adgn.backend.grant.GrantQueries.Filter(f.employee(),null,null,null,null,null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("snapshot");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant WHERE id=?",Long.class,grant)).isEqualTo(1);
    }
    private IsolatedMySqlDatabase db;private ConfigurableApplicationContext app;private JdbcTemplate jdbc;
    private Path keys;private String base,account,token,hash;
    private final JsonMapper json=JsonMapper.builder().build();private final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    private final String password="Grant-Fixture-123456";
    private String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"be14-keys-");
        for(String family:List.of("admin","business","oa")){var key=new RSAKeyGenerator(2048).keyID(family).generate();Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));}
        db=new IsolatedMySqlDatabase();app=db.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
            "--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),"--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),
            "--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),"--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000");
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();hash=new PasswordPolicy().encode(password);account=id();
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",account,account,hash);token=login(account);
    }
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(db!=null)db.close();}finally{if(keys!=null){for(String f:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(f+".json"));Files.deleteIfExists(keys);}}}}
    private JsonNode body(HttpResponse<String> r){return json.readTree(r.body());}
    private HttpResponse<String> call(String method,String path,Object input,String bearer,Map<String,String> headers)throws Exception{
        var r=HttpRequest.newBuilder(URI.create(base+"/api/v1/admin"+path));if(bearer!=null)r.header("Authorization","Bearer "+bearer);headers.forEach(r::header);
        if(input!=null)r.header("Content-Type","application/json");return http.send(r.method(method,input==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> call(String method,String path,Object input)throws Exception{return call(method,path,input,token,Map.of());}
    private String login(String name)throws Exception{var r=call("POST","/sessions",Map.of("loginName",name,"password",password),null,Map.of());assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return body(r).path("accessToken").stringValue();}
    private record Data(String employee,String company,String department,String otherDepartment,String system,String otherSystem,String role,String departmentRole,String employment){ }
    private Data data(){
        String e=id(),c=id(),d=id(),other=id(),s=id(),s2=id(),r=id(),r2=id();
        jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",c,c);
        for(String dep:List.of(d,other))jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Fixture',UTC_TIMESTAMP(6))",dep,c,dep);
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",e,e);
        for(String sys:List.of(s,s2))jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",sys,sys);
        role(r,c,null,s);role(r2,c,d,s);return new Data(e,c,d,other,s,s2,r,r2,employment(e,c,d));
    }
    private void role(String r,String c,String d,String s){jdbc.update("INSERT INTO business_role(id,code,name,company_id,department_id,system_id,state,configuration_version,created_at) VALUES(?,?,'Fixture',?,?,?,'EFFECTIVE',4,UTC_TIMESTAMP(6))",r,r,c,d,s);}
    private String employment(String e,String c,String d){String p=id(),job=id();jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'Fixture',UTC_TIMESTAMP(6))",p,d,p);jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",job,e,c,d,p);return job;}
    private Map<String,Object> key(Data f,String role){return Map.of("employeeId",f.employee(),"roleId",role,"sourceCompanyId",f.company(),"sourceDepartmentId",f.department());}
    private Map<String,Object> query(Data f){return Map.of("items",List.of(key(f,f.role())));}
    private Map<String,Object> sourceQuery(Data f,List<String> systems){return Map.of("employeeId",f.employee(),"companyId",f.company(),"departmentId",f.department(),"systemIds",systems);}
    private String source(Data f,String dep,String sys,long epoch,long version){String s=id();jdbc.update("INSERT INTO authorization_source(id,employee_id,company_id,department_id,system_id,source_epoch,source_version) VALUES(?,?,?,?,?,?,?)",s,f.employee(),f.company(),dep,sys,epoch,version);return s;}
    private String baseline(Data f,String source,String role,long version){String b=id();jdbc.update("INSERT INTO grant_baseline(id,source_id,role_id,company_id,system_id,relation_version) VALUES(?,?,?,?,?,?)",b,source,role,f.company(),f.system(),version);return b;}
    private String grant(Data f,String baseline,long version,long epoch,String status,String evidence){
        String request=id(),g=id();
        // A controlled persisted query fixture; it does not claim to exercise C's approval/execution flow.
        jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,'ROLE_GRANT',?,'{}','Fixture only',UTC_TIMESTAMP(6))",request,f.company());
        jdbc.update("INSERT INTO role_grant(id,baseline_id,relation_version,source_epoch,status,granted_by_request_id,ended_at,ended_reason,created_at) VALUES(?,?,?,?,?,?,?, ?,UTC_TIMESTAMP(6))",g,baseline,version,epoch,status,request,"ACTIVE".equals(status)?null:java.sql.Timestamp.from(java.time.Instant.now()),"ACTIVE".equals(status)?null:"Fixture ended");
        if(evidence!=null)jdbc.update("INSERT INTO grant_employment_evidence(grant_id,employment_id) VALUES(?,?)",g,evidence);return g;
    }
    private long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    private record Limited(String token,Map<String,String> headers,String scope,String appointment){ }
    private Limited limited(Data f)throws Exception{
        String e=id(),a=id(),scope=id(),appointment=id();jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Admin',UTC_TIMESTAMP(6))",e,e);employment(e,f.company(),f.department());
        jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,must_change_password,created_at) VALUES(?,?,?,?,0,UTC_TIMESTAMP(6))",a,e,a,hash);
        jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,f.company(),f.department());jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,f.system());
        jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",appointment,scope,a);
        return new Limited(login(a),Map.of("X-Admin-Appointment-Id",appointment,"X-Admin-Appointment-Version","1","X-Admin-Scope-Version","1"),scope,appointment);
    }
    @Test void missingAnchorsReturnLogicalZeroAndCompleteCurrentEmploymentWithoutWrites()throws Exception{
        Data f=data();var expected=new ArrayList<String>();expected.add(f.employment());for(int n=0;n<100;n++)expected.add(employment(f.employee(),f.company(),f.department()));expected.sort(String::compareTo);
        long sources=count("authorization_source"),baselines=count("grant_baseline"),audits=count("audit_event"),idem=count("http_idempotency");
        var r=call("POST","/grant-baselines",query(f));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);var item=body(r).path("items").get(0);
        assertThat(item.propertyNames()).containsExactlyInAnyOrder("key","systemId","roleConfigurationVersion","sourceEpoch","relationVersion","employmentIds","activeGrantId");
        assertThat(item.path("sourceEpoch").longValue()).isZero();assertThat(item.path("relationVersion").longValue()).isZero();assertThat(item.path("roleConfigurationVersion").longValue()).isEqualTo(4);assertThat(item.path("activeGrantId").isNull()).isTrue();
        assertThat(json.readValue(item.path("employmentIds").toString(),String[].class)).containsExactlyElementsOf(expected);assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
        var sr=call("POST","/source-baselines",sourceQuery(f,List.of(f.system(),f.otherSystem())));assertThat(sr.statusCode()).as(sr.body()).isEqualTo(200);assertThat(body(sr).path("items").size()).isEqualTo(2);assertThat(body(sr).path("items").get(1).path("version").longValue()).isZero();
        assertThat(count("authorization_source")).isEqualTo(sources);assertThat(count("grant_baseline")).isEqualTo(baselines);assertThat(count("audit_event")).isEqualTo(audits);assertThat(count("http_idempotency")).isEqualTo(idem);
    }
    @Test void existingSourceSurvivesMissingRelationAndReturnsItsBusinessVersion()throws Exception{
        Data f=data();source(f,f.department(),f.system(),7,13);
        var b=call("POST","/grant-baselines",query(f));assertThat(b.statusCode()).as(b.body()).isEqualTo(200);assertThat(body(b).path("items").get(0).path("sourceEpoch").longValue()).isEqualTo(7);assertThat(body(b).path("items").get(0).path("relationVersion").longValue()).isZero();
        var s=call("POST","/source-baselines",sourceQuery(f,List.of(f.system())));assertThat(s.statusCode()).isEqualTo(200);assertThat(body(s).path("items").get(0).path("version").longValue()).isEqualTo(13);
    }
    @Test void exactGrantVersionsAndImmutableEvidenceAreSeparateFromCurrentBaselineAndJobs()throws Exception{
        Data f=data();String s=source(f,f.department(),f.system(),3,8),b=baseline(f,s,f.role(),9);String old=grant(f,b,5,1,"REVOKED",f.employment()),active=grant(f,b,9,3,"ACTIVE",f.employment());
        jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6) WHERE id=?",f.employment());String current=employment(f.employee(),f.company(),f.department());
        var r=call("GET","/grants?employeeId="+f.employee()+"&pageSize=10",null);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(body(r).path("total").longValue()).isEqualTo(2);
        var values=new HashMap<String,JsonNode>();body(r).path("items").forEach(v->values.put(v.path("id").stringValue(),v));assertThat(values.get(old).path("relationVersion").longValue()).isEqualTo(5);assertThat(values.get(old).path("sourceEpoch").longValue()).isEqualTo(1);assertThat(values.get(active).path("employmentIds").get(0).stringValue()).isEqualTo(f.employment());
        var q=call("POST","/grant-baselines",query(f));assertThat(q.statusCode()).as(q.body()).isEqualTo(200);var item=body(q).path("items").get(0);assertThat(item.path("activeGrantId").stringValue()).isEqualTo(active);assertThat(item.path("relationVersion").longValue()).isEqualTo(9);assertThat(item.path("employmentIds").get(0).stringValue()).isEqualTo(current);
    }
    @Test void scopedHistoryUsesFixedSourceEvenAfterEmployeeLeavesAndCannotSeeOtherSources()throws Exception{
        Data f=data();Limited a=limited(f);String source=source(f,f.department(),f.system(),2,3),b=baseline(f,source,f.role(),4);String old=grant(f,b,4,1,"INVALIDATED",f.employment());
        String second=source(f,f.otherDepartment(),f.system(),0,0),b2=baseline(f,second,f.role(),1);grant(f,b2,1,0,"ACTIVE",employment(f.employee(),f.company(),f.otherDepartment()));
        jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6) WHERE id=?",f.employment());jdbc.update("UPDATE employee SET status='INACTIVE' WHERE id=?",f.employee());
        var r=call("GET","/grants?employeeId="+f.employee(),null,a.token(),a.headers());assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(body(r).path("total").longValue()).isEqualTo(1);assertThat(body(r).path("items").get(0).path("id").stringValue()).isEqualTo(old);
        var zero=call("POST","/source-baselines",sourceQuery(f,List.of(f.system())),a.token(),a.headers());assertThat(zero.statusCode()).as(zero.body()).isEqualTo(200);assertThat(body(zero).path("items").get(0).path("sourceEpoch").longValue()).isEqualTo(2);
        var baseline=call("POST","/grant-baselines",query(f),a.token(),a.headers());assertThat(baseline.statusCode()).as(baseline.body()).isEqualTo(200);assertThat(body(baseline).path("items").get(0).path("employmentIds").size()).isZero();
    }
    @Test void contextVersionsOwnershipAndWholeBatchScopeAreChecked()throws Exception{
        Data f=data();Limited a=limited(f),other=limited(f);
        assertThat(call("POST","/grant-baselines",query(f),a.token(),Map.of()).statusCode()).isEqualTo(422);
        var stale=new HashMap<>(a.headers());stale.put("X-Admin-Scope-Version","0");assertThat(call("POST","/grant-baselines",query(f),a.token(),stale).statusCode()).isEqualTo(409);
        assertThat(call("GET","/grants",null,a.token(),other.headers()).statusCode()).isEqualTo(403);
        assertThat(call("POST","/source-baselines",sourceQuery(f,List.of(f.system(),f.otherSystem())),a.token(),a.headers()).statusCode()).isEqualTo(403);
        assertThat(call("GET","/grants?sourceDepartmentId="+f.otherDepartment(),null,a.token(),a.headers()).statusCode()).isEqualTo(403);
        assertThat(call("POST","/grant-baselines",query(f),a.token(),a.headers()).statusCode()).isEqualTo(200);
        assertThat(call("POST","/grant-baselines",query(f),token,a.headers()).statusCode()).isEqualTo(403);
    }
    @Test void bodyShapeDuplicateKeysAndLimitsAreRejectedRatherThanDeduplicated()throws Exception{
        Data f=data();var k=key(f,f.role());
        for(Object input:List.of(Map.of("items",List.of()),Map.of("items",List.of(k,k)),Map.of("items",List.of(k),"extra",true),Map.of("items",Collections.nCopies(101,k))))assertThat(call("POST","/grant-baselines",input).statusCode()).isEqualTo(422);
        var invalid=new HashMap<>(k);invalid.put("roleId","bad/id");assertThat(call("POST","/grant-baselines",Map.of("items",List.of(invalid))).statusCode()).isEqualTo(422);
        assertThat(call("POST","/source-baselines",sourceQuery(f,List.of(f.system(),f.system()))).statusCode()).isEqualTo(422);
        assertThat(call("GET","/grants?pageSize=3",null).statusCode()).isEqualTo(422);assertThat(call("GET","/grants?status=ENDED",null).statusCode()).isEqualTo(422);
    }
    @Test void authenticationAndCurrentAccountStateApplyToAllThreeReadRoutes()throws Exception{
        Data f=data();for(String path:List.of("/grants","/grant-baselines","/source-baselines")){var input=path.equals("/grant-baselines")?query(f):path.equals("/source-baselines")?sourceQuery(f,List.of(f.system())):null;assertThat(call(input==null?"GET":"POST",path,input,null,Map.of()).statusCode()).isEqualTo(401);}
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",account);try{assertThat(call("GET","/grants",null).statusCode()).isEqualTo(401);}finally{jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",account);}
    }
    @Test void unavailableTargetReturns404AndCrossCompanyRoleIsNotSilentlyProjected()throws Exception{
        Data f=data(),foreign=data();var missing=new HashMap<>(key(f,f.role()));missing.put("employeeId",id());assertThat(call("POST","/grant-baselines",Map.of("items",List.of(missing))).statusCode()).isEqualTo(404);
        assertThat(call("POST","/grant-baselines",Map.of("items",List.of(key(f,foreign.role())))).statusCode()).isEqualTo(422);
    }
    @Test void paginationIsStableAndAccurateAfterFiltering()throws Exception{
        Data f=data();String s=source(f,f.department(),f.system(),0,0),b=baseline(f,s,f.role(),30);for(int n=1;n<=21;n++)grant(f,b,n,0,"REVOKED",f.employment());
        var r=call("GET","/grants?employeeId="+f.employee()+"&status=REVOKED&pageSize=10&page=3",null);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(body(r).path("total").longValue()).isEqualTo(21);assertThat(body(r).path("items").size()).isEqualTo(1);
        var beyond=call("GET","/grants?employeeId="+f.employee()+"&pageSize=100&page=9007199254740991",null);assertThat(beyond.statusCode()).as(beyond.body()).isEqualTo(200);assertThat(body(beyond).path("total").longValue()).isEqualTo(21);assertThat(body(beyond).path("items").size()).isZero();
    }
    @Test void acceptsOneHundredDistinctPairsInTheOriginalOrder()throws Exception{
        Data f=data();var pairs=new ArrayList<Map<String,Object>>();pairs.add(key(f,f.role()));for(int n=1;n<100;n++){String r=id();role(r,f.company(),null,f.system());pairs.add(key(f,r));}
        var r=call("POST","/grant-baselines",Map.of("items",pairs));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(body(r).path("items").size()).isEqualTo(100);
        for(int n=0;n<100;n++)assertThat(body(r).path("items").get(n).path("key").path("roleId").stringValue()).isEqualTo(pairs.get(n).get("roleId"));
    }
    @Test void rejectsDuplicateManagementHeadersAndRestrictedSessions()throws Exception{
        for(String header:List.of("X-Admin-Appointment-Id","X-Admin-Appointment-Version","X-Admin-Scope-Version")){
            var request=HttpRequest.newBuilder(URI.create(base+"/api/v1/admin/grants")).header("Authorization","Bearer "+token).header(header,"1").header(header,"2").GET().build();
            assertThat(http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(422);
        }
        String a=id();jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,1,UTC_TIMESTAMP(6))",a,a,hash);
        assertThat(call("GET","/grants",null,login(a),Map.of()).statusCode()).isEqualTo(403);
    }
    @Test void currentEligibilityUsesRoleSystemEmployeeAndExactDepartmentEmployment()throws Exception{
        Data f=data();var reads=app.getBean(com.adgn.backend.core.transaction.ReadBoundary.class);var facts=app.getBean(com.adgn.backend.grant.GrantReader.class);
        var key=new com.adgn.backend.grant.GrantReader.Key(f.employee(),f.departmentRole(),f.company(),f.department());
        assertThat(reads.read(()->0,a->facts.baseline(key)).eligibleForGrant()).isTrue();
        jdbc.update("UPDATE business_system SET enabled=0 WHERE id=?",f.system());assertThat(reads.read(()->0,a->facts.baseline(key)).eligibleForGrant()).isFalse();jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",f.system());
        jdbc.update("UPDATE business_role SET state='UNCONFIGURED',configuration_version=0 WHERE id=?",f.departmentRole());assertThat(reads.read(()->0,a->facts.baseline(key)).eligibleForGrant()).isFalse();jdbc.update("UPDATE business_role SET state='EFFECTIVE',configuration_version=4 WHERE id=?",f.departmentRole());
        jdbc.update("UPDATE employee SET status='INACTIVE' WHERE id=?",f.employee());assertThat(reads.read(()->0,a->facts.baseline(key)).eligibleForGrant()).isFalse();jdbc.update("UPDATE employee SET status='ACTIVE' WHERE id=?",f.employee());
        jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6) WHERE id=?",f.employment());employment(f.employee(),f.company(),f.otherDepartment());assertThat(reads.read(()->0,a->facts.baseline(key)).eligibleForGrant()).isFalse();
        var other=new com.adgn.backend.grant.GrantReader.Key(f.employee(),f.departmentRole(),f.company(),f.otherDepartment());assertThat(reads.read(()->0,a->facts.baseline(other)).employments()).isEmpty();
    }
    @Test void oneSnapshotDoesNotMixLogicalZeroWithAConcurrentSourceCreation()throws Exception{
        Data f=data();var reads=app.getBean(com.adgn.backend.core.transaction.ReadBoundary.class);var facts=app.getBean(com.adgn.backend.grant.GrantReader.class);
        var key=new com.adgn.backend.grant.GrantReader.SourceKey(f.employee(),f.company(),f.department(),f.system());
        var ready=new java.util.concurrent.CountDownLatch(1);var proceed=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var future=executor.submit(()->reads.read(()->0,a->{var first=facts.source(key);ready.countDown();if(!proceed.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Fixture barrier timed out");return List.of(first.sourceEpoch(),facts.source(key).sourceEpoch());}));
            try{assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();source(f,f.department(),f.system(),7,13);}finally{proceed.countDown();}
            assertThat(future.get(15,java.util.concurrent.TimeUnit.SECONDS)).containsExactly(0L,0L);
        }
        assertThat(reads.read(()->0,a->facts.source(key)).sourceEpoch()).isEqualTo(7);
    }
    @Test void corruptActiveEvidenceIsAnIntegrityFailureRatherThanAValidEmptyGrant()throws Exception{
        Data f=data();String s=source(f,f.department(),f.system(),0,0),b=baseline(f,s,f.role(),1);grant(f,b,1,0,"ACTIVE",null);
        var r=call("GET","/grants?employeeId="+f.employee(),null);assertThat(r.statusCode()).as(r.body()).isEqualTo(500);assertThat(r.body()).doesNotContain("baseline_id","granted_by_request_id","SELECT");
    }
    @Test void rejectsEvidenceFromAnotherDepartmentOrEmployeeInsteadOfExposingItsId()throws Exception{
        for(boolean otherEmployee:List.of(false,true)){
            Data f=data();String evidence;
            if(otherEmployee){String e=id();jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Other fixture',UTC_TIMESTAMP(6))",e,e);evidence=employment(e,f.company(),f.department());}
            else evidence=employment(f.employee(),f.company(),f.otherDepartment());
            String s=source(f,f.department(),f.system(),0,0),b=baseline(f,s,f.role(),1);grant(f,b,1,0,"ACTIVE",evidence);
            var r=call("GET","/grants?employeeId="+f.employee(),null);assertThat(r.statusCode()).as(r.body()).isEqualTo(500);assertThat(r.body()).doesNotContain(evidence);
        }
    }
}
