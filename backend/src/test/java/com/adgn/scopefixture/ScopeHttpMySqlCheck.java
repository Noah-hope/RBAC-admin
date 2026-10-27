package com.adgn.scopefixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.PasswordPolicy;
import com.adgn.backend.identity.security.IdentityTokens;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.scope.service.*;
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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScopeHttpMySqlCheck {
    @Test void appointmentJpaMappingLoadsPersistedHistoricalStatusAndVersion() {
        var factory=app.getBean(jakarta.persistence.EntityManagerFactory.class);
        try(var manager=factory.createEntityManager()){
            assertThat(manager.find(com.adgn.backend.scope.repository.AppointmentEntity.class,revoked)).isNotNull();
            var row=manager.createQuery("select a.status,a.version from AppointmentReadEntity a where a.id=:id",Object[].class)
                    .setParameter("id",revoked).getSingleResult();
            assertThat(row).containsExactly("REVOKED",9L);
        }
    }
    IsolatedMySqlDatabase db;
    ConfigurableApplicationContext app;
    JdbcTemplate jdbc;
    Path keys;
    String base,global,scoped,restricted,company,department,otherDepartment,noJobDepartment,actor,account,otherAccount;
    String firstScope,secondScope,noJobScope,historyScope,foreignScope,first,second,noJob,revoked,foreign,system1,system2;
    final JsonMapper json=JsonMapper.builder().build();
    final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    final String password="Fixture-Scope-123456";
    String id(){return UUID.randomUUID().toString();}

    @BeforeAll void start()throws Exception {
        keys=Files.createTempDirectory(Path.of(".tmp"),"be08-keys-");
        for(String family:List.of("admin","business","oa")){
            var key=new RSAKeyGenerator(2048).keyID(family).generate();
            Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));
        }
        db=new IsolatedMySqlDatabase();
        app=db.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
            "--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),
            "--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),
            "--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),
            "--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000");
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));
        base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/admin/";
        company=id();jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'Company',UTC_TIMESTAMP(6))",company,company);
        department=department();otherDepartment=department();noJobDepartment=department();
        actor=employee();account=account(actor,false,false);String otherEmployee=employee();otherAccount=account(otherEmployee,false,false);
        employ(actor,department);employ(actor,otherDepartment);employ(otherEmployee,department);
        String superAccount=account(null,true,false),restrictedAccount=account(null,true,true);
        system1=system();system2=system();
        firstScope=scope(department,3,system1);secondScope=scope(otherDepartment,7,system2);
        noJobScope=scope(noJobDepartment,4,system1);historyScope=scope(department,2);foreignScope=scope(department,6,system1);
        first=appointment(firstScope,account,2,"ACTIVE");second=appointment(secondScope,account,5,"ACTIVE");
        noJob=appointment(noJobScope,account,1,"ACTIVE");revoked=appointment(historyScope,account,9,"REVOKED");
        foreign=appointment(foreignScope,otherAccount,1,"ACTIVE");
        global=login(superAccount);scoped=login(account);restricted=login(restrictedAccount);
    }
    String department(){String d=id();jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Dept',UTC_TIMESTAMP(6))",d,company,d);return d;}
    String employee(){String e=id();jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Employee',UTC_TIMESTAMP(6))",e,e);return e;}
    void employ(String e,String d){String p=id();jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'Position',UTC_TIMESTAMP(6))",p,d,p);
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,status,created_at) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6))",id(),e,company,d,p);}
    String account(String employee,boolean superAdmin,boolean mustChange){String a=id();jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",a,employee,a,new PasswordPolicy().encode(password),superAdmin,mustChange);return a;}
    String system(){String s=id();jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,'System',UTC_TIMESTAMP(6))",s,s);return s;}
    String scope(String d,long version,String... systems){String s=id();jdbc.update("INSERT INTO management_scope(id,company_id,department_id,scope_version,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",s,company,d,version);
        for(String system:systems)jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",s,system);return s;}
    String appointment(String s,String a,long version,String status){String p=id();jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,appointment_version,status,ended_at,ended_reason,created_at) VALUES(?,?,?,?,?,IF(?='ACTIVE',NULL,UTC_TIMESTAMP(6)),IF(?='ACTIVE',NULL,'fixture'),UTC_TIMESTAMP(6))",p,s,a,version,status,status,status);return p;}
    String login(String login)throws Exception{var r=http.send(HttpRequest.newBuilder(URI.create(base+"sessions")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("loginName",login,"password",password)))).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(201);return body(r).path("accessToken").stringValue();}
    HttpResponse<String> get(String path,String token,String... headers)throws Exception{var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20)).GET();if(token!=null)b.header("Authorization","Bearer "+token);for(int i=0;i<headers.length;i+=2)b.header(headers[i],headers[i+1]);return http.send(b.build(),HttpResponse.BodyHandlers.ofString());}
    JsonNode body(HttpResponse<String> r){return json.readTree(r.body());}
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(db!=null)db.close();}finally{if(keys!=null){for(String f:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(f+".json"));Files.deleteIfExists(keys);}}}}

    @Test void myAppointmentsContainOnlyCurrentlyQualifiedOwnedItemsWithSeparateVersions()throws Exception{
        var r=get("my-appointments",scoped);assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
        var data=body(r);assertThat(data.path("total").longValue()).isEqualTo(2);
        assertThat(data.path("page").intValue()).isEqualTo(1);assertThat(data.path("pageSize").intValue()).isEqualTo(20);
        assertThat(r.body()).contains(first,second).doesNotContain(noJob,revoked,foreign,"passwordHash","rowVersion","credentialVersion");
        var item=data.path("items").get(0);
        assertThat(item.propertyNames()).containsExactlyInAnyOrder("id","scopeId","accountId","employeeId","status","version","scopeVersion","companyId","departmentId","systemIds");
        for(var row:data.path("items"))if(row.path("id").stringValue().equals(first)){
            assertThat(row.path("version").longValue()).isEqualTo(2);assertThat(row.path("scopeVersion").longValue()).isEqualTo(3);
            assertThat(row.path("systemIds").get(0).stringValue()).isEqualTo(system1);
        }
        var beyond=body(get("my-appointments?page=2&pageSize=10",scoped));assertThat(beyond.path("items").isEmpty()).isTrue();assertThat(beyond.path("total").longValue()).isEqualTo(2);
        assertThat(body(get("my-appointments",global)).path("total").longValue()).isZero();
    }
    @Test void globalScopeQueriesFilterCountAndProjectOnlyContractFields()throws Exception{
        var r=get("management-scopes?companyId="+company+"&departmentId="+department,global);assertThat(r.statusCode()).isEqualTo(200);
        assertThat(body(r).path("total").longValue()).isEqualTo(3);assertThat(r.body()).doesNotContain(otherDepartment,noJobDepartment);
        var detail=get("management-scopes/"+firstScope,global);assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(body(detail).propertyNames()).containsExactlyInAnyOrder("id","companyId","departmentId","systemIds","version","createdAt");
        assertThat(body(detail).path("version").longValue()).isEqualTo(3);assertThat(detail.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(get("management-scopes/"+id(),global).statusCode()).isEqualTo(404);
        assertThat(body(get("management-scopes/"+historyScope,global)).path("systemIds").isEmpty()).isTrue();
    }
    @Test void scopeAppointmentHistoryUsesRequestedStatusAndKeepsBusinessVersions()throws Exception{
        var r=get("management-scopes/"+historyScope+"/appointments?status=REVOKED",global);assertThat(r.statusCode()).isEqualTo(200);
        assertThat(body(r).path("total").longValue()).isEqualTo(1);assertThat(body(r).path("items").get(0).path("version").longValue()).isEqualTo(9);
        assertThat(body(get("management-scopes/"+historyScope+"/appointments?status=ACTIVE",global)).path("total").longValue()).isZero();
        assertThat(get("management-scopes/"+id()+"/appointments",global).statusCode()).isEqualTo(404);
    }
    @Test void scopeAdministrationCannotBeUsedByPermissionAdministrators()throws Exception{
        assertThat(get("management-scopes",scoped).statusCode()).isEqualTo(403);
        assertThat(get("management-scopes/"+firstScope,scoped).statusCode()).isEqualTo(404);
        assertThat(get("management-scopes/"+id(),scoped).statusCode()).isEqualTo(404);
        assertThat(get("management-scopes/"+firstScope+"/appointments",scoped).statusCode()).isEqualTo(404);
    }
    @Test void filtersAndIdentityFailuresNeverReturnScopeData()throws Exception{
        for(String path:List.of("my-appointments?page=0","my-appointments?pageSize=3","management-scopes?companyId=","management-scopes?departmentId=%27","management-scopes?page=999999999999999999999&pageSize=100","management-scopes/"+historyScope+"/appointments?status=ENDED"))assertThat(get(path,global).statusCode()).as(path).isEqualTo(422);
        assertThat(get("my-appointments",null).statusCode()).isEqualTo(401);
        assertThat(get("my-appointments",restricted).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",account);
        try{assertThat(get("my-appointments",scoped).statusCode()).isEqualTo(401);}finally{jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",account);}
    }
    @Test void nextRequestObservesRemovedEmploymentAndDoesNotTreatAnotherDepartmentAsCoverage()throws Exception{
        jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6) WHERE employee_id=? AND department_id=?",actor,department);
        try{var r=get("my-appointments",scoped);assertThat(r.statusCode()).isEqualTo(200);assertThat(body(r).path("total").longValue()).isEqualTo(1);assertThat(r.body()).contains(second).doesNotContain(first);}
        finally{jdbc.update("UPDATE employment SET status='ACTIVE',recorded_end_at=NULL WHERE employee_id=? AND department_id=?",actor,department);}
    }
    ManagementContext context(String token,ManagementContextResolver.Selection selection)throws Exception{
        var jwt=app.getBean(IdentityTokens.class).verify(token,IdentityTokens.Family.ADMIN);
        return app.getBean(ReadBoundary.class).read(()->app.getBean(ManagementContextResolver.class).resolve(jwt,selection),a->a);
    }
    ManagementContextResolver.Selection selection(){return new ManagementContextResolver.Selection(first,"2","3");}
    void refused(String token,ManagementContextResolver.Selection selection,int status,String code){
        assertThatThrownBy(()->context(token,selection)).isInstanceOfSatisfying(IdentityFailure.class,e->{assertThat(e.status()).isEqualTo(status);assertThat(e.code()).isEqualTo(code);});
    }
    @Test void resolvedContextUsesOneAppointmentAndDProvidedEmploymentFacts()throws Exception{
        assertThatNoException().isThrownBy(()->{
            var c=context(scoped,selection());assertThat(c.accountId()).isEqualTo(account);assertThat(c.scopeId()).isEqualTo(firstScope);
            assertThat(c.appointmentVersion()).isEqualTo(2);assertThat(c.scopeVersion()).isEqualTo(3);
            assertThat(c.companyId()).isEqualTo(company);assertThat(c.departmentId()).isEqualTo(department);assertThat(c.systemIds()).containsExactly(system1);
        });
    }
    @Test void globalContextDoesNotBorrowAnotherAccountsAppointment()throws Exception{
        assertThatNoException().isThrownBy(()->assertThat(context(global,new ManagementContextResolver.Selection(null,null,null)).global()).isTrue());
        refused(global,selection(),403,"FORBIDDEN");
    }
    @Test void missingMalformedAndForeignSelectionsHaveDistinctFailureSemantics(){
        for(var s:List.of(new ManagementContextResolver.Selection(null,null,null),new ManagementContextResolver.Selection(first,null,"3"),new ManagementContextResolver.Selection(first,"-1","3"),new ManagementContextResolver.Selection(first,"2","9007199254740992")))refused(scoped,s,422,"VALIDATION_ERROR");
        refused(scoped,new ManagementContextResolver.Selection(foreign,"1","6"),403,"FORBIDDEN");
        refused(scoped,new ManagementContextResolver.Selection(noJob,"1","4"),403,"FORBIDDEN");
    }
    @Test void appointmentAndScopeVersionsAreComparedIndependently(){
        refused(scoped,new ManagementContextResolver.Selection(first,"1","3"),409,"CONTEXT_CHANGED");
        refused(scoped,new ManagementContextResolver.Selection(first,"2","2"),409,"CONTEXT_CHANGED");
        refused(scoped,new ManagementContextResolver.Selection(revoked,"9","2"),409,"CONTEXT_CHANGED");
        refused(scoped,new ManagementContextResolver.Selection(id(),"1","1"),409,"CONTEXT_CHANGED");
    }
    @Test void contextResolutionRequiresTheCallersTransactionAndSupportsGuardedWrites()throws Exception{
        var jwt=app.getBean(IdentityTokens.class).verify(scoped,IdentityTokens.Family.ADMIN);
        assertThatThrownBy(()->app.getBean(ManagementContextResolver.class).resolve(jwt,selection())).isInstanceOf(IllegalStateException.class);
        assertThatNoException().isThrownBy(()->app.getBean(CommandBoundary.class).execute(
            ()->new CommandBoundary.Identity<ManagementContext>(app.getBean(ManagementContextResolver.class).resolve(jwt,selection()),new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account)),
            w->{assertThat(w.authority().scopeId()).isEqualTo(firstScope);return null;}));
    }
    @Test void peopleHttpUsesCompleteSingleContextAndRejectsDuplicateHeaders()throws Exception{
        String[] headers={"X-Admin-Appointment-Id",first,"X-Admin-Appointment-Version","2","X-Admin-Scope-Version","3"};
        assertThat(get("companies",scoped,headers).statusCode()).isEqualTo(200);
        assertThat(get("companies",scoped).statusCode()).isEqualTo(422);
        assertThat(get("companies",scoped,"X-Admin-Appointment-Id",first).statusCode()).isEqualTo(422);
        assertThat(get("companies",scoped,"X-Admin-Appointment-Id",first,"X-Admin-Appointment-Id",first,"X-Admin-Appointment-Version","2","X-Admin-Scope-Version","3").statusCode()).isEqualTo(422);
        headers[5]="2";var stale=get("companies",scoped,headers);assertThat(stale.statusCode()).isEqualTo(409);assertThat(body(stale).path("code").stringValue()).isEqualTo("CONTEXT_CHANGED");
    }
    @Test void newSnapshotSeesCommittedChangeWhileOneRrSnapshotStaysConsistent()throws Exception{
        var jwt=app.getBean(IdentityTokens.class).verify(scoped,IdentityTokens.Family.ADMIN);
        var globalJwt=app.getBean(IdentityTokens.class).verify(global,IdentityTokens.Family.ADMIN);
        var resolver=app.getBean(ManagementContextResolver.class);
        var entered=new CountDownLatch(1);var committed=new CountDownLatch(1);
        try(var worker=Executors.newSingleThreadExecutor()){
            var read=worker.submit(()->app.getBean(ReadBoundary.class).read(()->resolver.resolve(jwt,selection()),before->{
                entered.countDown();assertThat(committed.await(20,TimeUnit.SECONDS)).isTrue();
                var after=resolver.resolve(jwt,selection());assertThat(before.scopeVersion()).isEqualTo(3);assertThat(after.scopeVersion()).isEqualTo(3);
                return after;
            }));
            try{
                assertThat(entered.await(20,TimeUnit.SECONDS)).isTrue();
                app.getBean(CommandBoundary.class).execute(()->new CommandBoundary.Identity<>(resolver.resolve(globalJwt,null),new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,globalJwt.getSubject())),w->{
                    jdbc.update("UPDATE management_scope SET scope_version=4 WHERE id=?",firstScope);return null;
                });
                committed.countDown();assertThat(read.get(20,TimeUnit.SECONDS).scopeVersion()).isEqualTo(3);
                refused(scoped,selection(),409,"CONTEXT_CHANGED");
                assertThat(context(scoped,new ManagementContextResolver.Selection(first,"2","4")).scopeVersion()).isEqualTo(4);
            }finally{committed.countDown();jdbc.update("UPDATE management_scope SET scope_version=3 WHERE id=?",firstScope);read.cancel(true);}
        }
    }
    @Test void guardedContextObservesEarlierCommitEvenWhenJpaAlreadyLoadedAnEntity()throws Exception{
        var jwt=app.getBean(IdentityTokens.class).verify(scoped,IdentityTokens.Family.ADMIN);
        app.getBean(CommandBoundary.class).execute(()->new CommandBoundary.Identity<>(contextIdentity(jwt),new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account)),w->{
            app.getBean(com.adgn.backend.scope.repository.ScopeRepository.class).findById(firstScope).orElseThrow();
            jdbc.update("UPDATE management_scope SET scope_version=4 WHERE id=?",firstScope);
            assertThatThrownBy(()->app.getBean(ManagementContextResolver.class).resolve(jwt,selection())).isInstanceOfSatisfying(IdentityFailure.class,e->assertThat(e.code()).isEqualTo("CONTEXT_CHANGED"));
            jdbc.update("UPDATE management_scope SET scope_version=3 WHERE id=?",firstScope);return null;
        });
    }
    ManagementContext contextIdentity(org.springframework.security.oauth2.jwt.Jwt jwt){return app.getBean(ManagementContextResolver.class).resolve(jwt,selection());}
}
