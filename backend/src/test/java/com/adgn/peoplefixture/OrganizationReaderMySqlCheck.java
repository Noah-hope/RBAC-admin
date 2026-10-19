package com.adgn.peoplefixture;

import com.adgn.backend.BackendApplication;
import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.people.OrganizationReader;
import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationReaderMySqlCheck {
    private IsolatedMySqlDatabase database;
    private ConfigurableApplicationContext app;
    private OrganizationReader reader;
    private ReadBoundary reads;
    private CommandBoundary commands;
    private JdbcTemplate jdbc;
    private final UUID company=UUID.randomUUID(), otherCompany=UUID.randomUUID(), department=UUID.randomUUID(),
            otherDepartment=UUID.randomUUID(), foreignDepartment=UUID.randomUUID(), position=UUID.randomUUID(),
            secondPosition=UUID.randomUUID(), otherPosition=UUID.randomUUID(), foreignPosition=UUID.randomUUID(),
            employee=UUID.randomUUID(), inactive=UUID.randomUUID(), missing=UUID.randomUUID();

    @BeforeAll void start() throws Exception {
        database=new IsolatedMySqlDatabase();
        app=database.start(BackendApplication.class);
        var source=app.getBean(DataSource.class);
        reader=new OrganizationReader(source);
        jdbc=new JdbcTemplate(source);
        reads=app.getBean(ReadBoundary.class);
        commands=app.getBean(CommandBoundary.class);
        insert("INSERT INTO company(id,code,name,version,created_at) VALUES(?,?,?,7,UTC_TIMESTAMP(6))",company,"C1","Company");
        insert("INSERT INTO company(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",otherCompany,"C2","Company");
        for(var pair:List.of(new UUID[]{department,company},new UUID[]{otherDepartment,company},new UUID[]{foreignDepartment,otherCompany}))
            insert("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Department',UTC_TIMESTAMP(6))",pair[0],pair[1],pair[0].toString());
        for(var pair:List.of(new UUID[]{position,department},new UUID[]{secondPosition,department},new UUID[]{otherPosition,otherDepartment},new UUID[]{foreignPosition,foreignDepartment}))
            insert("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'Same position name',UTC_TIMESTAMP(6))",pair[0],pair[1],pair[0].toString());
        insert("INSERT INTO employee(id,employee_key,name,status,created_at) VALUES(?,'E1','Same name','ACTIVE',UTC_TIMESTAMP(6))",employee);
        insert("INSERT INTO employee(id,employee_key,name,status,created_at) VALUES(?,'E2','Same name','INACTIVE',UTC_TIMESTAMP(6))",inactive);
        employment(employee,company,department,position,"ACTIVE");
        employment(employee,company,department,secondPosition,"ACTIVE");
        employment(employee,company,department,position,"ENDED");
        employment(employee,company,otherDepartment,otherPosition,"ACTIVE");
        employment(employee,otherCompany,foreignDepartment,foreignPosition,"ACTIVE");
        employment(inactive,company,department,position,"ACTIVE");
        insert("INSERT INTO employee_source_identity(id,source_system,source_key,employee_id,version) VALUES(?,'hr','001',?,3)",UUID.randomUUID(),employee);
        insert("INSERT INTO organization_mapping(id,source_system,source_path,object_kind,company_id,department_id,position_id,version) VALUES(?,'hr','C/D/P','POSITION',?,?,?,4)",UUID.randomUUID(),company,department,position);
        insert("INSERT INTO organization_mapping(id,source_system,source_path,object_kind,company_id,version) VALUES(?,'hr','C','COMPANY',?,2)",UUID.randomUUID(),company);
    }
    @AfterAll void stop() throws Exception { if(app!=null)app.close(); if(database!=null)database.close(); }
    private void insert(String sql,Object... args) { jdbc.update(sql,Arrays.stream(args).map(x->x instanceof UUID ? x.toString():x).toArray()); }
    private void employment(UUID e,UUID c,UUID d,UUID p,String status) {
        insert("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,status,recorded_end_at,version,created_at) VALUES(?,?,?,?,?,?,IF(?='ENDED',UTC_TIMESTAMP(6),NULL),5,UTC_TIMESTAMP(6))",
                UUID.randomUUID(),e,c,d,p,status,status);
    }
    private <T> T read(CommandBoundary.CheckedSupplier<T> work) throws Exception { return reads.read(()->true,ignored->work.get()); }

    @Test void registeredAsAnInjectablePort() {
        assertThat(app.getBeanProvider(OrganizationReader.class).getIfAvailable()).isNotNull();
    }
    @Test void stableIdentifiersAndActualParentsAreReturnedWithVersions() throws Exception {
        read(()->{
            assertThat(reader.company(company).orElseThrow()).isEqualTo(new OrganizationReader.Company(company,"C1","Company",7));
            assertThat(reader.department(department).orElseThrow().companyId()).isEqualTo(company);
            assertThat(reader.position(position).orElseThrow().departmentId()).isEqualTo(department);
            assertThat(reader.employee(employee).orElseThrow().employeeKey()).isEqualTo("E1");
            assertThat(reader.employee(inactive).orElseThrow().status()).isEqualTo("INACTIVE");
            assertThat(reader.company(missing)).isEmpty(); assertThat(reader.department(missing)).isEmpty();
            assertThat(reader.position(missing)).isEmpty(); assertThat(reader.employee(missing)).isEmpty();
            return null;
        });
    }
    @Test void returnsAllCompatibleJobsWithoutInferringRolesOrDates() throws Exception {
        long revision=Long.parseLong(database.scalar("SELECT revision FROM mutation_guard"));
        var facts=read(()->reader.applicableEmployments(employee,company,department));
        assertThat(facts).hasSize(2);
        assertThat(facts).extracting(OrganizationReader.Employment::positionId).containsExactlyInAnyOrder(position,secondPosition);
        assertThat(facts).extracting(f->f.id().toString()).isSorted();
        assertThat(facts).allSatisfy(f->{assertThat(f.status()).isEqualTo("ACTIVE");assertThat(f.version()).isEqualTo(5);assertThat(f.startedAt()).isNull();});
        assertThatThrownBy(()->facts.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(database.scalar("SELECT revision FROM mutation_guard")).isEqualTo(Long.toString(revision));
        assertThat(database.scalar("SELECT COUNT(*) FROM role_grant")).isEqualTo("0");
    }
    @Test void exactCompanyAndDepartmentBoundaryExcludesOtherAssignments() throws Exception {
        assertThat(read(()->reader.applicableEmployments(employee,company,otherDepartment))).hasSize(1);
        assertThat(read(()->reader.applicableEmployments(employee,otherCompany,foreignDepartment))).hasSize(1);
        assertThat(read(()->reader.applicableEmployments(employee,company,foreignDepartment))).isEmpty();
        assertThat(read(()->reader.applicableEmployments(employee,otherCompany,department))).isEmpty();
        assertThat(read(()->reader.applicableEmployments(missing,company,department))).isEmpty();
    }
    @Test void inactiveEmployeeCannotSupplyQualificationDespiteActiveJobs() throws Exception {
        assertThat(read(()->reader.applicableEmployments(inactive,company,department))).isEmpty();
    }
    @Test void mappingsMatchExactSourceAndPathInsteadOfNamesOrSqlFragments() throws Exception {
        read(()->{
            var identity=reader.sourceIdentity("hr","001").orElseThrow();
            assertThat(identity.employeeId()).isEqualTo(employee); assertThat(identity.version()).isEqualTo(3);
            var mapping=reader.organizationMapping("hr","C/D/P").orElseThrow();
            assertThat(mapping.positionId()).isEqualTo(position); assertThat(mapping.companyId()).isEqualTo(company);
            assertThat(mapping.departmentId()).isEqualTo(department); assertThat(mapping.objectKind()).isEqualTo("POSITION");
            assertThat(mapping.version()).isEqualTo(4);
            var root=reader.organizationMapping("hr","C").orElseThrow();
            assertThat(root.departmentId()).isNull(); assertThat(root.positionId()).isNull();
            assertThat(reader.sourceIdentity("HR","001")).isEmpty();
            assertThat(reader.sourceIdentity("hr","001' OR '1'='1")).isEmpty();
            assertThat(reader.organizationMapping("hr","c/d/p")).isEmpty();
            assertThat(reader.organizationMapping("hr","C/D")).isEmpty();
            assertThat(reader.organizationMapping("hr","' OR 1=1 --")).isEmpty();
            return null;
        });
    }
    @Test void sameSnapshotDoesNotMixEmployeeStateAcrossConcurrentCommit() throws Exception {
        read(()->{
            assertThat(reader.applicableEmployments(employee,company,department)).hasSize(2);
            try(var other=database.runtimeConnection();var statement=other.prepareStatement("UPDATE employee SET status='INACTIVE' WHERE id=?")) {
                statement.setString(1,employee.toString()); statement.executeUpdate();
            }
            assertThat(reader.employee(employee).orElseThrow().status()).isEqualTo("ACTIVE");
            assertThat(reader.applicableEmployments(employee,company,department)).hasSize(2);
            return null;
        });
        try {
            assertThat(read(()->reader.applicableEmployments(employee,company,department))).isEmpty();
            commands.execute(()->new CommandBoundary.Identity<>("current",new AuditRecord.Actor(AuditRecord.ActorKind.SERVICE,"fixture")),ctx->{
                assertThat(reader.applicableEmployments(employee,company,department)).isEmpty();return null;
            });
        } finally { insert("UPDATE employee SET status='ACTIVE' WHERE id=?",employee); }
    }
    @Test void aTransactionOnAnotherDatasourceDoesNotAuthorizeThisRead() {
        var unrelated=new DriverManagerDataSource("jdbc:unreachable:must-not-connect");
        var wrongReader=new OrganizationReader(unrelated);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(app.getBean(DataSource.class)));
        tx.execute(status->{
            assertThatThrownBy(()->wrongReader.employee(employee)).isInstanceOf(IllegalStateException.class);
            return null;
        });
    }
    @Test void knownBusinessStartIsReadAsUtcWithoutChangingQualification() throws Exception {
        insert("UPDATE employment SET started_at='2026-10-01 03:04:05.123456' WHERE employee_id=? AND position_id=? AND status='ACTIVE'",employee,secondPosition);
        try {
            var facts=read(()->reader.applicableEmployments(employee,company,department));
            assertThat(facts).hasSize(2);
            assertThat(facts.stream().filter(f->f.positionId().equals(secondPosition)).findFirst().orElseThrow().startedAt())
                    .isEqualTo(Instant.parse("2026-10-01T03:04:05.123456Z"));
        } finally { insert("UPDATE employment SET started_at=NULL WHERE employee_id=? AND position_id=? AND status='ACTIVE'",employee,secondPosition); }
    }
    @Test void brokenStoragePropagatesFailureInsteadOfPretendingMappingIsAbsent() throws Exception {
        database.execute("RENAME TABLE organization_mapping TO unavailable_organization_mapping");
        try {
            assertThatThrownBy(()->read(()->reader.organizationMapping("hr","C")))
                    .isInstanceOfSatisfying(PersistenceFailure.class,e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
        } finally { database.execute("RENAME TABLE unavailable_organization_mapping TO organization_mapping"); }
    }
}
