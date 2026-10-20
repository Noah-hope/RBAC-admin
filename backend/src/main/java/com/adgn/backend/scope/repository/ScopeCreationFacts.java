package com.adgn.backend.scope.repository;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.people.OrganizationReader;
import com.adgn.backend.scope.service.ScopeCreationInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.util.*;

/** Only creation-relevant facts: never credentials or a union of management appointments. */
public final class ScopeCreationFacts {
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final OrganizationReader people;
    public ScopeCreationFacts(DataSource source,OrganizationReader people){this.source=source;this.jdbc=new JdbcTemplate(source);this.people=people;}
    public Map<String,Object> qualified(ScopeCreationInput input){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("Scope facts require the caller transaction");
        UUID company=uuid(input.companyId()),department=uuid(input.departmentId());
        var c=people.company(company).orElseThrow(IdentityFailure::invalid);
        var d=people.department(department).orElseThrow(IdentityFailure::invalid);
        if(!d.companyId().equals(company))throw IdentityFailure.invalid();
        var systems=new ArrayList<Map<String,Object>>();
        for(String id:input.systemIds()){
            var rows=jdbc.queryForList("SELECT id,enabled,version FROM business_system WHERE id=?",id);
            if(rows.isEmpty()||!flag(rows.getFirst().get("enabled")))throw IdentityFailure.invalid();
            systems.add(rows.getFirst());
        }
        var accounts=new ArrayList<Map<String,Object>>();
        for(String id:input.accountIds()){
            var rows=jdbc.queryForList("SELECT id,employee_id,enabled,is_super_admin,version FROM admin_account WHERE id=?",id);
            if(rows.isEmpty())throw IdentityFailure.invalid();var a=rows.getFirst();
            if(!flag(a.get("enabled"))||flag(a.get("is_super_admin"))||a.get("employee_id")==null)throw IdentityFailure.invalid();
            UUID employee=uuid(a.get("employee_id").toString());
            var e=people.employee(employee).orElseThrow(IdentityFailure::invalid);
            var jobs=people.applicableEmployments(employee,company,department);
            if(!e.status().equals("ACTIVE")||jobs.isEmpty())throw IdentityFailure.invalid();
            accounts.add(Map.of("account",a,"employee",Map.of("id",e.id(),"status",e.status(),"version",e.version()),"employments",jobs));
        }
        return Map.of("schemaVersion",1,"company",Map.of("id",c.id(),"version",c.version()),"department",Map.of("id",d.id(),"companyId",d.companyId(),"version",d.version()),"systems",systems,"accounts",accounts);
    }
    private static UUID uuid(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw IdentityFailure.invalid();return id;}catch(IllegalArgumentException bad){throw IdentityFailure.invalid();}}
    private static boolean flag(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
}
