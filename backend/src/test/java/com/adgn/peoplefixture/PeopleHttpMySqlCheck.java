package com.adgn.peoplefixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.PasswordPolicy;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeopleHttpMySqlCheck {
    @Test void unsupportedInternalResourceIsRejectedInsteadOfBecomingAnUnboundedQuery()throws Exception{
        var jwt=app.getBean(com.adgn.backend.identity.security.IdentityTokens.class).verify(global,com.adgn.backend.identity.security.IdentityTokens.Family.ADMIN);
        var queries=app.getBean(com.adgn.backend.people.PeopleQueries.class);
        assertThatThrownBy(()->queries.list(jwt,new com.adgn.backend.people.PeopleQueries.Context(null,null,null),"unsupported",null,
                new com.adgn.backend.people.PeopleQueries.Filter(null,null,null,null,null,null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(get("employees",global).statusCode()).isEqualTo(200);
    }
    private IsolatedMySqlDatabase db;
    private ConfigurableApplicationContext app;
    private JdbcTemplate jdbc;
    private Path keys;
    private String base,global,scoped,account,actor,company,department,position,otherCompany,otherDepartment,otherPosition,scope,appointment,employee,hidden,history,system;
    private final String password="Fixture-People-123456";
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    private String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start() throws Exception {
        keys=Files.createTempDirectory(Path.of(".tmp"),"be10-http-keys-");
        for(String family:List.of("admin","business","oa")) {
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
        base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
        company=organization("Visible_",null);otherCompany=organization("Hidden",null);organization("VisibleX",null);
        department=organization("VisibleD",company);otherDepartment=organization("HiddenD",otherCompany);
        position=job(department,"VisibleP");otherPosition=job(otherDepartment,"HiddenP");
        actor=person("Actor");employee=person("Visible");hidden=person("Hidden");history=person("History");
        employ(actor,company,department,position,"ACTIVE");employ(employee,company,department,position,"ACTIVE");
        employ(employee,otherCompany,otherDepartment,otherPosition,"ACTIVE");employ(hidden,otherCompany,otherDepartment,otherPosition,"ACTIVE");
        employ(history,company,department,position,"ENDED");
        String hash=new PasswordPolicy().encode(password),superId=id(); account=id();
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",superId,superId,hash);
        jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,must_change_password,created_at) VALUES(?,?,?,?,0,UTC_TIMESTAMP(6))",account,actor,account,hash);
        scope=id();appointment=id();system=id();
        jdbc.update("INSERT INTO management_scope(id,company_id,department_id,scope_version,created_at) VALUES(?,?,?,3,UTC_TIMESTAMP(6))",scope,company,department);
        jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,appointment_version,created_at) VALUES(?,?,?,2,UTC_TIMESTAMP(6))",appointment,scope,account);
        jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",system,system);
        jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,system);
        jdbc.update("INSERT INTO authorization_source(id,employee_id,company_id,department_id,system_id) VALUES(?,?,?,?,?)",id(),history,company,department,system);
        global=login(superId);scoped=login(account);
    }
    private String organization(String code,String companyId){String id=id();if(companyId==null)jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",id,code,code);
        else jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",id,companyId,code,code);return id;}
    private String job(String dept,String code){String id=id();jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",id,dept,code,code);return id;}
    private String person(String name){String id=id();jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",id,id,name);return id;}
    private void employ(String e,String c,String d,String p,String status){jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,status,recorded_end_at,created_at) VALUES(?,?,?,?,?,?,IF(?='ENDED',UTC_TIMESTAMP(6),NULL),UTC_TIMESTAMP(6))",id(),e,c,d,p,status,status);}
    private String login(String login)throws Exception{
        var r=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/admin/sessions")).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("loginName",login,"password",password)))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(201);return body(r).path("accessToken").stringValue();
    }
    private HttpResponse<String> get(String path,String token,String... headers)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+"/api/v1/admin/"+path)).timeout(Duration.ofSeconds(20)).GET();
        if(token!=null)b.header("Authorization","Bearer "+token);
        for(int n=0;n<headers.length;n+=2)b.header(headers[n],headers[n+1]);
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> limited(String path)throws Exception{return get(path,scoped,"X-Admin-Appointment-Id",appointment,"X-Admin-Appointment-Version","2","X-Admin-Scope-Version","3");}
    private JsonNode body(HttpResponse<String> r){return json.readTree(r.body());}
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(db!=null)db.close();}finally{if(keys!=null){for(String f:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(f+".json"));Files.deleteIfExists(keys);}}}}

    @ParameterizedTest @ValueSource(strings={"companies","departments","positions","employees"})
    void scopedListsFilterBeforeCountingAndUseNoStore(String resource)throws Exception{
        var r=limited(resource);assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");assertThat(r.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(body(r).path("total").longValue()).isEqualTo(resource.equals("employees")?3:1);
        assertThat(r.body()).doesNotContain(otherCompany,otherDepartment,otherPosition,hidden,"passwordHash","sourceSystem");
        assertThat(body(r).path("page").intValue()).isEqualTo(1);assertThat(body(r).path("pageSize").intValue()).isEqualTo(20);
    }
    @Test void detailAndEmploymentProjectionNeverExposeOtherJobs()throws Exception{
        var r=limited("employees/"+employee);assertThat(r.statusCode()).isEqualTo(200);
        assertThat(body(r).propertyNames()).containsExactlyInAnyOrder("id","employeeKey","name","status","version","createdAt");
        var jobs=limited("employees/"+employee+"/employments");assertThat(jobs.statusCode()).isEqualTo(200);
        assertThat(body(jobs).path("total").longValue()).isEqualTo(1);assertThat(jobs.body()).doesNotContain(otherDepartment,otherPosition);
        assertThat(body(jobs).path("items").get(0).propertyNames()).containsExactlyInAnyOrder("id","employeeId","companyId","departmentId","positionId","status","version","startedAt","endedAt");
        assertThat(limited("employees/"+hidden).statusCode()).isEqualTo(404);
        assertThat(limited("employees/"+id()).statusCode()).isEqualTo(404);
        assertThat(limited("employees/"+hidden+"/employments").statusCode()).isEqualTo(404);
    }
    @Test void historicalEmployeeNeedsAnAllowedSourceSystemAndEndedJobsStayHistory()throws Exception{
        assertThat(limited("employees/"+history).statusCode()).isEqualTo(200);
        var r=limited("employees/"+history+"/employments?status=ENDED");assertThat(r.statusCode()).isEqualTo(200);assertThat(body(r).path("total").longValue()).isEqualTo(1);
        jdbc.update("DELETE FROM scope_system WHERE scope_id=?",scope);
        try{assertThat(limited("employees/"+history).statusCode()).isEqualTo(404);assertThat(body(limited("employees")).path("total").longValue()).isEqualTo(2);}
        finally{jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",scope,system);}
    }
    @Test void filtersOutsideOneAppointmentAreForbidden()throws Exception{
        for(String p:List.of("departments?companyId="+otherCompany,"positions?departmentId="+otherDepartment,"employees?companyId="+otherCompany,"employees/"+employee+"/employments?departmentId="+otherDepartment))
            assertThat(limited(p).statusCode()).as(p).isEqualTo(403);
    }
    @Test void requiresCompleteVersionedContextAndRejectsStaleOrForeignAppointment()throws Exception{
        assertThat(get("companies",scoped).statusCode()).isEqualTo(422);
        assertThat(get("companies",scoped,"X-Admin-Appointment-Id",appointment).statusCode()).isEqualTo(422);
        assertThat(get("companies",scoped,"X-Admin-Appointment-Id",appointment,"X-Admin-Appointment-Version","1","X-Admin-Scope-Version","3").statusCode()).isEqualTo(409);
        assertThat(get("companies",scoped,"X-Admin-Appointment-Id",appointment,"X-Admin-Appointment-Version","2","X-Admin-Scope-Version","2").statusCode()).isEqualTo(409);
        assertThat(get("companies",global,"X-Admin-Appointment-Id",appointment,"X-Admin-Appointment-Version","2","X-Admin-Scope-Version","3").statusCode()).isEqualTo(403);
    }
    @Test void globalListsArePagedSortedAndSearchIsLiteral()throws Exception{
        var r=get("companies",global);assertThat(r.statusCode()).isEqualTo(200);assertThat(body(r).path("total").longValue()).isEqualTo(3);
        assertThat(body(r).path("items").get(0).path("code").stringValue()).isEqualTo("Hidden");
        var literal=get("companies?q=Visible_",global);assertThat(body(literal).path("total").longValue()).isEqualTo(1);
        var beyond=get("companies?page=2&pageSize=10",global);assertThat(body(beyond).path("items").isEmpty()).isTrue();assertThat(body(beyond).path("total").longValue()).isEqualTo(3);
        assertThat(body(get("companies?q=%25",global)).path("total").longValue()).isZero();
        var filtered=get("employees?companyId="+company+"&departmentId="+department+"&q=Visible",global);assertThat(body(filtered).path("total").longValue()).isEqualTo(1);
    }
    @Test void rejectsMalformedFiltersWithoutReturningData()throws Exception{
        for(String p:List.of("companies?page=0","companies?pageSize=3","companies?page=999999999999999999999","companies?q=%20%20","employees?status=ENDED","employees/"+employee+"/employments?status=INACTIVE","positions?departmentId=%27"))
            assertThat(get(p,global).statusCode()).as(p).isEqualTo(422);
        assertThat(get("companies",null).statusCode()).isEqualTo(401);
    }
    @Test void currentAccountChangesRejectAnOtherwiseValidToken()throws Exception{
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",account);
        try{assertThat(limited("companies").statusCode()).isEqualTo(401);}finally{jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",account);}
    }
    @Test void revokedChosenAppointmentCannotBorrowAnotherValidAppointment()throws Exception{
        String alternative=id(),secondScope=id();
        jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",secondScope,company,department);
        jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",alternative,secondScope,account);
        jdbc.update("UPDATE admin_appointment SET status='REVOKED',ended_at=UTC_TIMESTAMP(6),ended_reason='fixture',appointment_version=3 WHERE id=?",appointment);
        try{var r=limited("employees");assertThat(r.statusCode()).isEqualTo(409);assertThat(body(r).path("code").stringValue()).isEqualTo("CONTEXT_CHANGED");}
        finally{jdbc.update("UPDATE admin_appointment SET status='ACTIVE',ended_at=NULL,ended_reason=NULL,appointment_version=2 WHERE id=?",appointment);jdbc.update("DELETE FROM admin_appointment WHERE id=?",alternative);jdbc.update("DELETE FROM management_scope WHERE id=?",secondScope);}
    }
}
