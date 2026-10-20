package com.adgn.backend.query.preview;

import com.adgn.backend.query.management.ManagementAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;

/** Caller-owned snapshot, independent SQL facts, and hashed set/empty-set dependencies. */
public final class ImpactService {
    public record Analysis(Map<String,Object> dependencies,PreviewService.Projection projection) { }
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final PreviewService previews;
    private final ImpactPolicy policy;
    public ImpactService(DataSource source,PreviewService previews,JsonMapper json){this.source=source;this.jdbc=new JdbcTemplate(source);this.previews=previews;this.policy=new ImpactPolicy(json);}
    public Analysis analyze(ManagementAccess.Authority viewer,ImpactPolicy.Change change){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("Impact analysis requires caller transaction");
        if(change.systemIds().isEmpty())throw new IllegalArgumentException("Explicit affected systems required");
        var ids=change.systemIds().stream().sorted().toList();String in="("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";Object[] args=ids.toArray();
        var sets=new TreeMap<String,List<Map<String,Object>>>();
        sets.put("systems",jdbc.queryForList("SELECT id,enabled,version,catalog_version FROM business_system WHERE id IN "+in+" ORDER BY id",args));
        sets.put("nodes",jdbc.queryForList("SELECT id,system_id,parent_id,node_type,enabled,visibility_permission_id,version FROM directory_node WHERE system_id IN "+in+" ORDER BY id",args));
        sets.put("permissions",jdbc.queryForList("SELECT id,system_id,function_id,permission_type,enabled,menu_eligible,version FROM permission WHERE system_id IN "+in+" ORDER BY id",args));
        sets.put("roles",jdbc.queryForList("SELECT id,company_id,department_id,system_id,state,configuration_version FROM business_role WHERE system_id IN "+in+" ORDER BY id",args));
        sets.put("rolePermissions",jdbc.queryForList("SELECT role_id,permission_id,system_id FROM role_permission WHERE system_id IN "+in+" ORDER BY role_id,permission_id",args));
        sets.put("sources",jdbc.queryForList("SELECT id,employee_id,company_id,department_id,system_id,source_epoch,source_version FROM authorization_source WHERE system_id IN "+in+" ORDER BY id",args));
        sets.put("baselines",jdbc.queryForList("SELECT id,source_id,role_id,company_id,system_id,relation_version FROM grant_baseline WHERE system_id IN "+in+" ORDER BY id",args));
        sets.put("grants",jdbc.queryForList("SELECT g.id,g.baseline_id,g.relation_version,g.source_epoch,g.status FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id WHERE b.system_id IN "+in+" ORDER BY g.id",args));
        // Proposed/logical-zero sources must not require prior materialization to see qualifications.
        var employeeArgs=new ArrayList<Object>(ids);var employmentArgs=new ArrayList<Object>(ids);
        var extraEmployees=new TreeSet<>(change.inactiveEmployeeIds());var extraSources=new TreeMap<String,ImpactPolicy.Source>();
        for(var source:change.closedSources())extraSources.put(source.key(),source);
        for(var addition:change.additions())extraSources.put(addition.source().key(),addition.source());
        for(var source:extraSources.values())extraEmployees.add(source.employeeId());
        String extraEmployee="",extraEmployment="";
        if(!extraEmployees.isEmpty()){extraEmployee=" OR e.id IN ("+String.join(",",Collections.nCopies(extraEmployees.size(),"?"))+")";employeeArgs.addAll(extraEmployees);}
        for(var source:extraSources.values()){extraEmployment+=" OR (em.employee_id=? AND em.company_id=?)";employmentArgs.add(source.employeeId());employmentArgs.add(source.companyId());}
        sets.put("employees",jdbc.queryForList("SELECT e.id,e.status,e.version FROM employee e WHERE EXISTS(SELECT 1 FROM authorization_source s WHERE s.employee_id=e.id AND s.system_id IN "+in+")"+extraEmployee+" ORDER BY e.id",employeeArgs.toArray()));
        sets.put("employments",jdbc.queryForList("SELECT em.id,em.employee_id,em.company_id,em.department_id,em.status,em.version FROM employment em WHERE EXISTS(SELECT 1 FROM authorization_source s WHERE s.employee_id=em.employee_id AND s.company_id=em.company_id AND s.system_id IN "+in+")"+extraEmployment+" ORDER BY em.id",employmentArgs.toArray()));
        String pending="cr.state IN ('PENDING','APPROVED','EXECUTION_FAILED') AND EXISTS(SELECT 1 FROM change_target involved WHERE involved.request_id=cr.id AND involved.system_id IN "+in+")";
        sets.put("pendingRequests",jdbc.queryForList("SELECT cr.id,cr.company_id,cr.state,cr.status_version,cr.scope_id,cr.appointment_id,cr.content FROM change_request cr WHERE "+pending+" ORDER BY cr.id",args));
        sets.put("pendingTargets",jdbc.queryForList("SELECT t.request_id,t.line_no,t.company_id,t.department_id,t.system_id,t.role_id,t.employee_id,t.source_id,t.baseline_id,t.grant_id,t.expected_configuration_version,t.expected_source_epoch,t.expected_relation_version FROM change_target t JOIN change_request cr ON cr.id=t.request_id WHERE "+pending+" ORDER BY t.request_id,t.line_no",args));
        if(!change.revokedAppointmentIds().isEmpty())sets.put("appointments",jdbc.queryForList("SELECT DISTINCT ap.id,ap.appointment_version,sc.company_id,sc.department_id FROM admin_appointment ap JOIN management_scope sc ON sc.id=ap.scope_id JOIN scope_system ss ON ss.scope_id=sc.id WHERE ap.status='ACTIVE' AND ss.system_id IN "+in+" ORDER BY ap.id",args));
        var facts=new ImpactPolicy.Facts(sets);var dependencies=dependencies(facts,change);
        var hashes=new TreeMap<String,String>();dependencies.forEach((name,rows)->hashes.put(name,HexFormat.of().formatHex(previews.digest(rows))));
        return new Analysis(Map.of("schemaVersion",1,"systemIds",ids,"sets",hashes),policy.calculate(facts,change,viewer));
    }
    private Map<String,List<Map<String,Object>>> dependencies(ImpactPolicy.Facts facts,ImpactPolicy.Change change){
        var affectedRoles=policy.affectedRoles(facts,change);var roles=new HashSet<>(affectedRoles);
        var sourceRows=facts.index("sources");var baselines=facts.index("baselines");var employeeKeys=new HashSet<String>();
        for(var row:facts.rows("grants")){
            if(!"ACTIVE".equals(ImpactPolicy.text(row,"status")))continue;
            var b=baselines.get(ImpactPolicy.text(row,"baseline_id"));if(b==null)continue;var s=sourceRows.get(ImpactPolicy.text(b,"source_id"));if(s==null)continue;
            var key=ImpactPolicy.source(s);
            if(affectedRoles.contains(ImpactPolicy.text(b,"role_id"))||change.closedSources().contains(key)||change.endedGrantIds().contains(ImpactPolicy.text(row,"id"))||change.inactiveEmployeeIds().contains(key.employeeId()))employeeKeys.add(key.employeeKey());
        }
        for(var addition:change.additions()){employeeKeys.add(addition.source().employeeKey());roles.add(addition.roleId());}
        var sourceIds=new HashSet<String>();
        for(var row:facts.rows("sources"))if(employeeKeys.contains(ImpactPolicy.source(row).employeeKey())||change.closedSources().contains(ImpactPolicy.source(row)))sourceIds.add(ImpactPolicy.text(row,"id"));
        var baselineIds=new HashSet<String>();
        for(var row:facts.rows("baselines"))if(affectedRoles.contains(ImpactPolicy.text(row,"role_id"))||sourceIds.contains(ImpactPolicy.text(row,"source_id"))){
            baselineIds.add(ImpactPolicy.text(row,"id"));sourceIds.add(ImpactPolicy.text(row,"source_id"));roles.add(ImpactPolicy.text(row,"role_id"));
        }
        var employeeIds=new HashSet<>(change.inactiveEmployeeIds());var companies=new HashSet<String>();
        for(var source:change.closedSources()){employeeIds.add(source.employeeId());companies.add(source.employeeId()+":"+source.companyId());}
        for(var addition:change.additions()){employeeIds.add(addition.source().employeeId());companies.add(addition.source().employeeId()+":"+addition.source().companyId());}
        for(String id:sourceIds){var source=sourceRows.get(id);if(source!=null){employeeIds.add(ImpactPolicy.text(source,"employee_id"));companies.add(ImpactPolicy.text(source,"employee_id")+":"+ImpactPolicy.text(source,"company_id"));}}
        var requests=policy.affectedRequests(facts,change,affectedRoles);var result=new TreeMap<String,List<Map<String,Object>>>();
        for(String key:List.of("systems","nodes","permissions"))result.put(key,facts.rows(key));
        result.put("roles",facts.rows("roles").stream().filter(r->roles.contains(ImpactPolicy.text(r,"id"))).toList());
        result.put("rolePermissions",facts.rows("rolePermissions").stream().filter(r->roles.contains(ImpactPolicy.text(r,"role_id"))).toList());
        result.put("sources",facts.rows("sources").stream().filter(r->sourceIds.contains(ImpactPolicy.text(r,"id"))).toList());
        result.put("baselines",facts.rows("baselines").stream().filter(r->baselineIds.contains(ImpactPolicy.text(r,"id"))).toList());
        result.put("grants",facts.rows("grants").stream().filter(r->baselineIds.contains(ImpactPolicy.text(r,"baseline_id"))).toList());
        result.put("employees",facts.rows("employees").stream().filter(r->employeeIds.contains(ImpactPolicy.text(r,"id"))).toList());
        result.put("employments",facts.rows("employments").stream().filter(r->companies.contains(ImpactPolicy.text(r,"employee_id")+":"+ImpactPolicy.text(r,"company_id"))).toList());
        result.put("pendingRequests",facts.rows("pendingRequests").stream().filter(r->requests.contains(ImpactPolicy.text(r,"id"))).toList());
        result.put("pendingTargets",facts.rows("pendingTargets").stream().filter(r->requests.contains(ImpactPolicy.text(r,"request_id"))).toList());
        result.put("appointments",facts.rows("appointments").stream().filter(r->change.revokedAppointmentIds().contains(ImpactPolicy.text(r,"id"))).toList());
        return result;
    }
}
