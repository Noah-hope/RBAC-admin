package com.adgn.peoplefixture;

import com.adgn.backend.BackendApplication;
import com.adgn.backend.people.EmploymentWriter;
import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.IdentityFailure;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmploymentWriterMySqlCheck {
    private IsolatedMySqlDatabase db;private ConfigurableApplicationContext app;
    private JdbcTemplate jdbc;private CommandBoundary commands;private EmploymentWriter writer;
    private UUID account,batch;private final String digest="01".repeat(32);
    private UUID id(){return UUID.randomUUID();}
    @BeforeAll void start()throws Exception{
        db=new IsolatedMySqlDatabase();app=db.start(BackendApplication.class);jdbc=new JdbcTemplate(app.getBean(DataSource.class));
        commands=app.getBean(CommandBoundary.class);writer=app.getBeanProvider(EmploymentWriter.class).getIfAvailable();
        if(writer==null)writer=new EmploymentWriter(app.getBean(DataSource.class),app.getBean(com.adgn.backend.core.audit.AuditAppender.class),tools.jackson.databind.json.JsonMapper.builder().build());
        account=id();batch=id();UUID file=id();
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,'fixture-only',1,0,UTC_TIMESTAMP(6))",account.toString(),account.toString());
        jdbc.update("INSERT INTO import_file(id,file_name,sha256,size_bytes,storage_ref,sheet_names,uploaded_by,created_at) VALUES(?,'fixture.csv',UNHEX(?),1,'fixture','[]',?,UTC_TIMESTAMP(6))",file.toString(),digest,account.toString());
        jdbc.update("INSERT INTO import_batch(id,file_id,source_system,source_batch_id,import_type,input_snapshot,input_digest,content_digest,state,planned_counts,created_by,created_at,sealed_at) VALUES(?,?,'fixture',?,'PERSONNEL_CHANGES','{}',UNHEX(?),UNHEX(?),'READY','{}',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",batch.toString(),file.toString(),batch.toString(),digest,digest,account.toString());
    }
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{if(db!=null)db.close();}}
    private <T> T write(CommandBoundary.CheckedFunction<WriteContext<EmploymentWriter.ImportAuthority>,T> work)throws Exception{
        return commands.execute(()->new CommandBoundary.Identity<>(new EmploymentWriter.ImportAuthority(account,batch,1,digest,"fixture import",id().toString()),new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account.toString())),work);
    }
    private EmploymentWriter.Result org(WriteContext<?> c,EmploymentWriter.Kind kind,UUID id,long expected,UUID parent,String code,String name){return writer.organization(c,kind,id,expected,parent,code,name);}
    @Test void candidateIdsStayStableRenamingAdvancesOnlyRealChangesAndAuditsTogether()throws Exception{
        UUID company=id(),dept=id(),job=id(),employee=id();
        write(c->{
            assertThat(org(c,EmploymentWriter.Kind.COMPANY,company,0,null,"C"+company,"Initial").version()).isEqualTo(1);
            org(c,EmploymentWriter.Kind.DEPARTMENT,dept,0,company,"D","Dept");org(c,EmploymentWriter.Kind.POSITION,job,0,dept,"P","Job");
            writer.employee(c,employee,0,"E"+employee,"Person","ACTIVE");
            return null;
        });
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class);
        var renamed=write(c->org(c,EmploymentWriter.Kind.COMPANY,company,1,null,"C"+company,"Renamed"));
        assertThat(renamed.version()).isEqualTo(2);assertThat(renamed.changed()).isTrue();
        var same=write(c->org(c,EmploymentWriter.Kind.COMPANY,company,2,null,"C"+company,"Renamed"));
        assertThat(same.changed()).isFalse();assertThat(same.version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT code FROM company WHERE id=?",String.class,company.toString())).isEqualTo("C"+company);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class)).isEqualTo(before+1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_appointment",Long.class)).isZero();
    }
    @Test void anErrorCannotBeCaughtToCommitPartialRowsOrAudit()throws Exception{
        UUID first=id(),second=id();String code="Duplicate"+first;
        long audits=jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class),revision=jdbc.queryForObject("SELECT revision FROM mutation_guard",Long.class);
        assertThatThrownBy(()->write(c->{org(c,EmploymentWriter.Kind.COMPANY,first,0,null,code,"First");
            try{org(c,EmploymentWriter.Kind.COMPANY,second,0,null,code,"Second");}catch(RuntimeException ignored){}
            return null;
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE id IN (?,?)",Long.class,first.toString(),second.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class)).isEqualTo(audits);
        assertThat(jdbc.queryForObject("SELECT revision FROM mutation_guard",Long.class)).isEqualTo(revision);
    }
    @Test void rejectsWrongVersionsCodesParentsAndUnauthorizedImportContext()throws Exception{
        UUID company=id();write(c->org(c,EmploymentWriter.Kind.COMPANY,company,0,null,"Guard"+company,"Initial"));
        assertThatThrownBy(()->write(c->org(c,EmploymentWriter.Kind.COMPANY,company,9,null,"Guard"+company,"Wrong"))).isInstanceOf(IdentityFailure.class);
        assertThatThrownBy(()->write(c->org(c,EmploymentWriter.Kind.COMPANY,company,1,null,"ChangedCode","Wrong"))).isInstanceOf(IdentityFailure.class);
        assertThatThrownBy(()->write(c->org(c,EmploymentWriter.Kind.DEPARTMENT,id(),0,id(),"D","Wrong"))).isInstanceOf(IdentityFailure.class);
        assertThatThrownBy(()->commands.execute(()->new CommandBoundary.Identity<>("not-import",new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account.toString())),c->org(c,EmploymentWriter.Kind.COMPANY,id(),0,null,"No","Denied"))).isInstanceOf(IdentityFailure.class);
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",account.toString());
        try{assertThatThrownBy(()->write(c->org(c,EmploymentWriter.Kind.COMPANY,id(),0,null,"No","Denied"))).isInstanceOf(IdentityFailure.class);}
        finally{jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",account.toString());}
        jdbc.update("UPDATE import_batch SET version=2 WHERE id=?",batch.toString());
        try{assertThatThrownBy(()->write(c->org(c,EmploymentWriter.Kind.COMPANY,id(),0,null,"No","Denied"))).isInstanceOf(IdentityFailure.class);}
        finally{jdbc.update("UPDATE import_batch SET version=1 WHERE id=?",batch.toString());}
    }
    @Test void explicitSourceAndOrganizationMappingsPreserveIdsAndRejectInvalidOwnership()throws Exception{
        UUID company=id(),dept=id(),job=id(),employee=id(),source=id(),mapping=id();
        write(c->{org(c,EmploymentWriter.Kind.COMPANY,company,0,null,"Map"+company,"Company");org(c,EmploymentWriter.Kind.DEPARTMENT,dept,0,company,"D","Dept");org(c,EmploymentWriter.Kind.POSITION,job,0,dept,"P","Job");
            writer.employee(c,employee,0,"Map"+employee,"Person","ACTIVE");writer.sourceIdentity(c,source,0,"hr","old",employee);
            writer.organizationMapping(c,mapping,0,"hr","old/path","POSITION",company,dept,job);return null;});
        assertThat(write(c->writer.sourceIdentity(c,source,1,"hr","new",employee)).version()).isEqualTo(2);
        assertThat(write(c->writer.organizationMapping(c,mapping,1,"hr","new/path","POSITION",company,dept,job)).version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT source_key FROM employee_source_identity WHERE id=?",String.class,source.toString())).isEqualTo("new");
        assertThatThrownBy(()->write(c->writer.organizationMapping(c,id(),0,"hr","invalid","POSITION",id(),dept,job))).isInstanceOf(IdentityFailure.class);
        assertThatThrownBy(()->write(c->writer.employee(c,employee,1,"Map"+employee,"Person","INACTIVE"))).isInstanceOf(IdentityFailure.class);
    }
    @Test void endingKeepsHistoryAndReturnsLastJobLossWithoutGrantingAnything()throws Exception{
        UUID company=id(),dept=id(),job=id(),otherJob=id(),employee=id(),employment=id(),second=id();
        write(c->{org(c,EmploymentWriter.Kind.COMPANY,company,0,null,"Jobs"+company,"Company");org(c,EmploymentWriter.Kind.DEPARTMENT,dept,0,company,"D","Dept");org(c,EmploymentWriter.Kind.POSITION,job,0,dept,"P","Job");org(c,EmploymentWriter.Kind.POSITION,otherJob,0,dept,"P2","Job2");
            writer.employee(c,employee,0,"Jobs"+employee,"Person","ACTIVE");writer.startEmployment(c,employment,employee,company,dept,job,Instant.parse("2026-10-01T00:00:00Z"));writer.startEmployment(c,second,employee,company,dept,otherJob,null);return null;});
        var first=write(c->writer.endEmployment(c,employment,1,null));assertThat(first.lastDepartmentJobEnded()).isFalse();
        var last=write(c->writer.endEmployment(c,second,1,null));assertThat(last.lastDepartmentJobEnded()).isTrue();assertThat(last.employeeId()).isEqualTo(employee);
        UUID replacement=id();write(c->writer.startEmployment(c,replacement,employee,company,dept,job,null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employment WHERE employee_id=?",Long.class,employee.toString())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT recorded_end_at IS NOT NULL AND ended_at IS NULL FROM employment WHERE id=?",Boolean.class,second.toString())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_grant",Long.class)).isZero();
    }
    @Test void departmentMigrationKeepsMappingIdsAndRejectsAnyEmploymentHistory()throws Exception{
        UUID company=id(),destination=id(),dept=id(),job=id(),mapping=id();
        write(c->{org(c,EmploymentWriter.Kind.COMPANY,company,0,null,"Old"+company,"Old");org(c,EmploymentWriter.Kind.COMPANY,destination,0,null,"New"+destination,"New");org(c,EmploymentWriter.Kind.DEPARTMENT,dept,0,company,"Movable","Dept");org(c,EmploymentWriter.Kind.POSITION,job,0,dept,"P","Job");writer.organizationMapping(c,mapping,0,"hr","move/old","POSITION",company,dept,job);return null;});
        // Creation audit itself is formal history. For the no-history migration fixture, use direct setup without audit.
        UUID clean=id(),cleanJob=id(),cleanMap=id();
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Clean',UTC_TIMESTAMP(6))",clean.toString(),company.toString(),"Clean");
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'Job',UTC_TIMESTAMP(6))",cleanJob.toString(),clean.toString(),"P");
        jdbc.update("INSERT INTO organization_mapping(id,source_system,source_path,object_kind,company_id,department_id,position_id) VALUES(?,'hr','clean/old','POSITION',?,?,?)",cleanMap.toString(),company.toString(),clean.toString(),cleanJob.toString());
        write(c->writer.moveDepartment(c,clean,1,destination,List.of(new EmploymentWriter.MappingMove(cleanMap,1,"clean/new"))));
        assertThat(jdbc.queryForObject("SELECT company_id FROM department WHERE id=?",String.class,clean.toString())).isEqualTo(destination.toString());
        assertThat(jdbc.queryForObject("SELECT version FROM organization_mapping WHERE id=?",Long.class,cleanMap.toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT company_id FROM audit_event WHERE target_id=? AND event_type='IMPORT_DEPARTMENT'",String.class,clean.toString())).isEqualTo(destination.toString());
        assertThat(jdbc.queryForObject("SELECT source_department_id FROM audit_event WHERE target_id=? AND event_type='IMPORT_DEPARTMENT'",String.class,clean.toString())).isEqualTo(clean.toString());
        assertThatThrownBy(()->write(c->writer.moveDepartment(c,dept,1,destination,List.of(new EmploymentWriter.MappingMove(mapping,1,"move/new"))))).isInstanceOf(IdentityFailure.class);
    }
    @Test void noChangeAtMaximumVersionDoesNotRequireAnotherVersionNumber()throws Exception{
        UUID company=id();write(c->org(c,EmploymentWriter.Kind.COMPANY,company,0,null,"Max"+company,"Name"));
        jdbc.update("UPDATE company SET version=9007199254740991 WHERE id=?",company.toString());
        var same=write(c->org(c,EmploymentWriter.Kind.COMPANY,company,9007199254740991L,null,"Max"+company,"Name"));
        assertThat(same.changed()).isFalse();assertThat(same.version()).isEqualTo(9007199254740991L);
        assertThatThrownBy(()->write(c->org(c,EmploymentWriter.Kind.COMPANY,company,9007199254740991L,null,"Max"+company,"Changed"))).isInstanceOf(IdentityFailure.class);
    }
    @Test void invalidEndDateIsRejectedBeforeDatabaseConstraintFailure()throws Exception{
        UUID company=id(),dept=id(),job=id(),employee=id(),employment=id();
        write(c->{org(c,EmploymentWriter.Kind.COMPANY,company,0,null,"Date"+company,"Company");org(c,EmploymentWriter.Kind.DEPARTMENT,dept,0,company,"D","Dept");org(c,EmploymentWriter.Kind.POSITION,job,0,dept,"P","Job");
            writer.employee(c,employee,0,"Date"+employee,"Person","ACTIVE");writer.startEmployment(c,employment,employee,company,dept,job,Instant.parse("2026-10-01T00:00:00Z"));return null;});
        assertThatThrownBy(()->write(c->writer.endEmployment(c,employment,1,Instant.parse("2026-09-01T00:00:00Z")))).isInstanceOfSatisfying(IdentityFailure.class,e->assertThat(e.status()).isEqualTo(422));
        assertThat(jdbc.queryForObject("SELECT status FROM employment WHERE id=?",String.class,employment.toString())).isEqualTo("ACTIVE");
    }
    @Test void metadataEditsKeepStableKeysAndInactiveEmployeesStayInactive()throws Exception{
        UUID employee=id(),company=id(),dept=id(),job=id();
        write(c->{org(c,EmploymentWriter.Kind.COMPANY,company,0,null,"Meta"+company,"Company");org(c,EmploymentWriter.Kind.DEPARTMENT,dept,0,company,"D","Dept");org(c,EmploymentWriter.Kind.POSITION,job,0,dept,"P","Job");writer.employee(c,employee,0,"Meta"+employee,"Initial","INACTIVE");return null;});
        write(c->{org(c,EmploymentWriter.Kind.DEPARTMENT,dept,1,company,"D","Renamed department");org(c,EmploymentWriter.Kind.POSITION,job,1,dept,"P","Renamed position");writer.employee(c,employee,1,"Meta"+employee,"Renamed person",null);return null;});
        assertThat(jdbc.queryForObject("SELECT status FROM employee WHERE id=?",String.class,employee.toString())).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM employee WHERE id=?",Long.class,employee.toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT code FROM `position` WHERE id=?",String.class,job.toString())).isEqualTo("P");
    }
    @Test void injectedWriterRejectsContextEscapingItsOriginalTransaction()throws Exception{
        assertThat(app.getBeanProvider(EmploymentWriter.class).getIfAvailable()).isSameAs(writer);
        var escaped=new java.util.concurrent.atomic.AtomicReference<WriteContext<?>>();write(c->{escaped.set(c);return null;});
        assertThatThrownBy(()->org(escaped.get(),EmploymentWriter.Kind.COMPANY,id(),0,null,"Escaped","Denied")).isInstanceOf(IllegalStateException.class);
    }
    @Test void mappingRowsAreRestoredWhenDestinationCodeConflicts()throws Exception{
        UUID oldCompany=id(),destination=id(),department=id(),collision=id(),mapping=id();
        write(c->{org(c,EmploymentWriter.Kind.COMPANY,oldCompany,0,null,"Rollback"+oldCompany,"Old");org(c,EmploymentWriter.Kind.COMPANY,destination,0,null,"Rollback"+destination,"New");return null;});
        for(UUID d:List.of(department,collision))jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Fixture',UTC_TIMESTAMP(6))",d.toString(),(d.equals(department)?oldCompany:destination).toString(),"SameCode");
        jdbc.update("INSERT INTO organization_mapping(id,source_system,source_path,object_kind,company_id,department_id) VALUES(?,'hr',?,'DEPARTMENT',?,?)",mapping.toString(),mapping.toString(),oldCompany.toString(),department.toString());
        assertThatThrownBy(()->write(c->writer.moveDepartment(c,department,1,destination,List.of(new EmploymentWriter.MappingMove(mapping,1,"NewPath"))))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT company_id FROM department WHERE id=?",String.class,department.toString())).isEqualTo(oldCompany.toString());
        assertThat(jdbc.queryForObject("SELECT source_path FROM organization_mapping WHERE id=?",String.class,mapping.toString())).isEqualTo(mapping.toString());
        assertThat(jdbc.queryForObject("SELECT version FROM organization_mapping WHERE id=?",Long.class,mapping.toString())).isEqualTo(1);
    }
    @Test void endedEmploymentHistoryBlocksMigrationWithoutAnyAuditReference()throws Exception{
        UUID oldCompany=id(),destination=id(),department=id(),job=id(),person=id();
        write(c->{org(c,EmploymentWriter.Kind.COMPANY,oldCompany,0,null,"History"+oldCompany,"Old");org(c,EmploymentWriter.Kind.COMPANY,destination,0,null,"History"+destination,"New");return null;});
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,'D','Fixture',UTC_TIMESTAMP(6))",department.toString(),oldCompany.toString());
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,'P','Fixture',UTC_TIMESTAMP(6))",job.toString(),department.toString());
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",person.toString(),person.toString());
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,status,recorded_end_at,created_at) VALUES(?,?,?,?,?,'ENDED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id().toString(),person.toString(),oldCompany.toString(),department.toString(),job.toString());
        assertThatThrownBy(()->write(c->writer.moveDepartment(c,department,1,destination,List.of()))).isInstanceOfSatisfying(IdentityFailure.class,e->assertThat(e.code()).isEqualTo("IMPORT_BLOCKED"));
    }
}
