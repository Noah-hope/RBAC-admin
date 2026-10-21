package com.adgn.backend.query.preview;

import com.adgn.backend.query.management.ManagementAccess;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** Pure impact calculation: complete effective sources first, safe projection second. */
public final class ImpactPolicy {
    public record Source(String employeeId,String companyId,String departmentId,String systemId){
        public String key(){return employeeId+":"+companyId+":"+departmentId+":"+systemId;}
        public String employeeKey(){return employeeId+":"+companyId+":"+systemId;}
    }
    public record NodeState(String parentId,boolean enabled,String visibilityPermissionId) { }
    public record PermissionState(boolean enabled,boolean menuEligible) { }
    public record Addition(String id,String roleId,Source source) { }
    public record Change(Set<String> systemIds,Map<String,Boolean> systemStates,Map<String,NodeState> nodes,
            Map<String,PermissionState> permissions,Map<String,Set<String>> rolePermissions,Set<Source> closedSources,
            Set<String> endedGrantIds,Set<String> inactiveEmployeeIds,Set<String> revokedAppointmentIds,List<Addition> additions,
            Set<String> touchedPermissions,List<JsonNode> extraTargets){
        public Change{
            systemIds=Set.copyOf(systemIds);systemStates=Map.copyOf(systemStates);nodes=Map.copyOf(nodes);permissions=Map.copyOf(permissions);
            var copy=new TreeMap<String,Set<String>>();rolePermissions.forEach((k,v)->copy.put(k,Set.copyOf(v)));rolePermissions=Collections.unmodifiableMap(copy);
            closedSources=Set.copyOf(closedSources);endedGrantIds=Set.copyOf(endedGrantIds);inactiveEmployeeIds=Set.copyOf(inactiveEmployeeIds);
            revokedAppointmentIds=Set.copyOf(revokedAppointmentIds);additions=List.copyOf(additions);touchedPermissions=Set.copyOf(touchedPermissions);extraTargets=List.copyOf(extraTargets);
        }
        public static Change system(String id,boolean enabled){return new Change(Set.of(id),Map.of(id,enabled),Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),List.of(),Set.of(),List.of());}
    }
    public record Facts(Map<String,List<Map<String,Object>>> sets){
        public Facts{var copy=new TreeMap<String,List<Map<String,Object>>>();sets.forEach((k,v)->copy.put(k,List.copyOf(v)));sets=Collections.unmodifiableMap(copy);}
        public List<Map<String,Object>> rows(String key){return sets.getOrDefault(key,List.of());}
        public Map<String,Map<String,Object>> index(String key){var result=new LinkedHashMap<String,Map<String,Object>>();for(var row:rows(key))result.put(text(row,"id"),row);return result;}
    }
    private record Grant(String id,String role,Source source,boolean active,boolean current,boolean addition) { }
    private final JsonMapper json;
    public ImpactPolicy(JsonMapper json){this.json=json;}
    public PreviewService.Projection calculate(Facts facts,Change change,ManagementAccess.Authority viewer){
        var systems=facts.index("systems");var nodes=facts.index("nodes");var permissions=facts.index("permissions");var roles=facts.index("roles");
        var sources=facts.index("sources");var baselines=facts.index("baselines");var employees=facts.index("employees");
        var configs=new HashMap<String,Set<String>>();
        for(var row:facts.rows("rolePermissions"))configs.computeIfAbsent(text(row,"role_id"),k->new HashSet<>()).add(text(row,"permission_id"));
        var affected=affectedRoles(facts,change);
        var grants=new ArrayList<Grant>();
        for(var row:facts.rows("grants")){
            var baseline=baselines.get(text(row,"baseline_id"));if(baseline==null)continue;
            var source=sources.get(text(baseline,"source_id"));if(source==null)continue;
            grants.add(new Grant(text(row,"id"),text(baseline,"role_id"),source(source),"ACTIVE".equals(text(row,"status")),
                    number(row,"source_epoch")==number(source,"source_epoch")&&number(row,"relation_version")==number(baseline,"relation_version"),false));
        }
        for(var addition:change.additions())grants.add(new Grant(addition.id(),addition.roleId(),addition.source(),true,true,true));
        var targets=new ArrayList<JsonNode>();var visibleSources=new TreeMap<String,Source>();var impactedEmployees=new TreeMap<String,Source>();var disclosedGrants=new TreeMap<String,List<String>>();
        long roleCount=0,grantCount=0,pendingCount=0,appointmentCount=0;boolean undisclosed=false;
        for(String id:affected){
            var role=roles.get(id);String company=text(role,"company_id"),department=text(role,"department_id"),system=text(role,"system_id");
            if(covers(viewer,company,department,system,true)){roleCount++;targets.add(target("ROLE",id,null,company,department,system,number(role,"configuration_version"),"CONFIGURATION_CHANGE","角色有效功能受影响"));}
            else undisclosed=true;
        }
        for(var grant:grants){
            var s=grant.source();
            if(!grant.active() || !(affected.contains(grant.role())||change.closedSources().contains(s)||change.endedGrantIds().contains(grant.id())||change.inactiveEmployeeIds().contains(s.employeeId())||grant.addition()))continue;
            if(!covers(viewer,s.companyId(),s.departmentId(),s.systemId(),false)){undisclosed=true;continue;}
            impactedEmployees.put(s.employeeKey(),s);visibleSources.put(s.key(),s);disclosedGrants.computeIfAbsent(s.employeeKey(),k->new ArrayList<>()).add(grant.id());grantCount++;
            targets.add(target("GRANT",grant.id(),s,s.companyId(),s.departmentId(),s.systemId(),null,
                    change.closedSources().contains(s)?"CLOSE_SOURCE":change.endedGrantIds().contains(grant.id())?"DISABLE":"CONFIGURATION_CHANGE","授权来源参与影响计算"));
        }
        for(var s:visibleSources.values())targets.add(target("SOURCE",null,s,s.companyId(),s.departmentId(),s.systemId(),null,change.closedSources().contains(s)?"CLOSE_SOURCE":"KEEP_VALID","独立来源保持隔离"));
        var items=new ArrayList<JsonNode>();long losing=0,retaining=0,gaining=0;
        for(var entry:impactedEmployees.entrySet()){
            var key=entry.getValue();boolean before=enter(key,false,grants,facts,change,roles,systems,nodes,permissions,configs,employees);
            boolean after=enter(key,true,grants,facts,change,roles,systems,nodes,permissions,configs,employees);
            if(before&&!after)losing++;if(before&&after)retaining++;if(!before&&after)gaining++;
            boolean hidden=grants.stream().anyMatch(g->g.active()&&g.source().employeeKey().equals(key.employeeKey())&&!covers(viewer,g.source().companyId(),g.source().departmentId(),g.source().systemId(),false));
            var item=json.createObjectNode().put("employeeId",key.employeeId()).put("companyId",key.companyId()).put("systemId",key.systemId())
                    .put("canAccessBefore",before).put("canAccessAfter",after).put("resultCompleteness",hidden?"REDACTED":"COMPLETE");
            item.set("visibleGrantIds",json.valueToTree(disclosedGrants.get(entry.getKey()).stream().distinct().sorted().toList()));items.add(item);undisclosed|=hidden;
        }
        var requestTargets=new HashMap<String,List<Map<String,Object>>>();
        for(var line:facts.rows("pendingTargets"))requestTargets.computeIfAbsent(text(line,"request_id"),k->new ArrayList<>()).add(line);
        var affectedRequests=affectedRequests(facts,change,affected);
        for(var request:facts.rows("pendingRequests")){
            var lines=requestTargets.getOrDefault(text(request,"id"),List.of());
            if(lines.isEmpty()||!affectedRequests.contains(text(request,"id")))continue;
            if(lines.stream().allMatch(line->covers(viewer,text(line,"company_id"),text(line,"department_id"),text(line,"system_id"),false))){
                pendingCount++;targets.add(target("CHANGE_REQUEST",text(request,"id"),null,text(request,"company_id"),null,null,number(request,"status_version"),"CONFIGURATION_CHANGE","固定申请执行时须复核目录依赖"));
            }else undisclosed=true;
        }
        for(var ap:facts.rows("appointments"))if(change.revokedAppointmentIds().contains(text(ap,"id"))){
            if(viewer.global()){appointmentCount++;targets.add(target("APPOINTMENT",text(ap,"id"),null,text(ap,"company_id"),text(ap,"department_id"),null,number(ap,"appointment_version"),"REVOKE_APPOINTMENT","任命将结束"));}else undisclosed=true;
        }
        long extraSourceCount=0;
        if(viewer.global()){
            var seen=new HashSet<String>();for(var target:targets)seen.add(targetKey(target));
            for(var target:change.extraTargets())if(seen.add(targetKey(target))){
                targets.add(target);
                switch(target.path("targetType").asText()){
                    case "ROLE" -> roleCount++;
                    case "GRANT" -> grantCount++;
                    case "SOURCE" -> extraSourceCount++;
                    case "APPOINTMENT" -> appointmentCount++;
                    case "CHANGE_REQUEST" -> pendingCount++;
                    default -> { }
                }
            }
        }else undisclosed|=!change.extraTargets().isEmpty();
        var counts=new LinkedHashMap<String,Object>();counts.put("visibleEmployeeCount",items.size());counts.put("visibleLosingAccessCount",losing);counts.put("visibleRetainingAccessCount",retaining);counts.put("visibleGainingAccessCount",gaining);
        counts.put("visibleRoleCount",roleCount);counts.put("visibleGrantCount",grantCount);counts.put("visibleSourceCount",visibleSources.size()+extraSourceCount);counts.put("visibleAppointmentCount",appointmentCount);counts.put("visiblePendingRequestCount",pendingCount);
        counts.put("visibility",viewer.global()?"COMPLETE":"SCOPED");counts.put("hasUndisclosedImpact",undisclosed);counts.put("warning",undisclosed?"存在不可披露的影响，仅显示当前范围内明细":"");
        return new PreviewService.Projection(counts,items,targets,undisclosed?List.of("存在不可披露的影响"):List.of());
    }
    private static String targetKey(JsonNode target){
        var source=target.path("source");
        return target.path("targetType").asText()+":"+(target.path("targetId").isNull()||target.path("targetId").isMissingNode()
                ?source.path("employeeId").asText()+":"+source.path("companyId").asText()+":"+source.path("departmentId").asText()+":"+source.path("systemId").asText()
                :target.path("targetId").asText());
    }
    public Set<String> affectedRoles(Facts facts,Change change){
        var systems=facts.index("systems");var nodes=facts.index("nodes");var permissions=facts.index("permissions");var configs=new HashMap<String,Set<String>>();
        for(var row:facts.rows("rolePermissions"))configs.computeIfAbsent(text(row,"role_id"),k->new HashSet<>()).add(text(row,"permission_id"));
        var affected=new TreeSet<String>();
        for(var role:facts.rows("roles")){
            String id=text(role,"id"),system=text(role,"system_id");
            if(change.systemStates().containsKey(system)&&flag(systems.get(system),"enabled")!=change.systemStates().get(system)
                    || change.rolePermissions().containsKey(id)||!Collections.disjoint(configs.getOrDefault(id,Set.of()),change.touchedPermissions())
                    || !available(role,false,systems,nodes,permissions,configs,change).equals(available(role,true,systems,nodes,permissions,configs,change)))affected.add(id);
        }
        return affected;
    }
    public Set<String> affectedRequests(Facts facts,Change change,Set<String> roles){
        var result=new TreeSet<String>();var sources=facts.index("sources");var permissions=facts.index("permissions");var changedPermissions=new HashSet<>(change.touchedPermissions());
        change.permissions().forEach((id,state)->{var old=permissions.get(id);if(old==null||state.enabled()!=flag(old,"enabled")||state.menuEligible()!=flag(old,"menu_eligible"))changedPermissions.add(id);});
        for(var line:facts.rows("pendingTargets")){
            String employee=text(line,"employee_id"),grant=text(line,"grant_id"),sid=text(line,"source_id");var source=sources.get(sid);
            if(roles.contains(text(line,"role_id"))||employee!=null&&change.inactiveEmployeeIds().contains(employee)
                    ||grant!=null&&change.endedGrantIds().contains(grant)||source!=null&&change.closedSources().contains(source(source)))result.add(text(line,"request_id"));
        }
        for(var request:facts.rows("pendingRequests")){
            String appointment=text(request,"appointment_id");if(appointment!=null&&change.revokedAppointmentIds().contains(appointment))result.add(text(request,"id"));
            Object raw=request.get("content");if(raw==null)continue;var content=raw instanceof String s?json.readTree(s):json.valueToTree(raw);
            for(var permission:content.path("permissionIds"))if(changedPermissions.contains(permission.stringValue())){result.add(text(request,"id"));break;}
        }
        return result;
    }
    private static boolean enter(Source key,boolean after,List<Grant> grants,Facts facts,Change change,Map<String,Map<String,Object>> roles,
            Map<String,Map<String,Object>> systems,Map<String,Map<String,Object>> nodes,Map<String,Map<String,Object>> permissions,Map<String,Set<String>> configs,Map<String,Map<String,Object>> employees){
        var employee=employees.get(key.employeeId());if(employee==null||!"ACTIVE".equals(text(employee,"status"))||after&&change.inactiveEmployeeIds().contains(key.employeeId()))return false;
        for(var grant:grants){
            if(!key.employeeKey().equals(grant.source().employeeKey())||!grant.active()||!grant.current()||!after&&grant.addition()
                    ||after&&(change.closedSources().contains(grant.source())||change.endedGrantIds().contains(grant.id())))continue;
            var role=roles.get(grant.role());if(role==null||!key.companyId().equals(text(role,"company_id"))||!key.systemId().equals(text(role,"system_id")))continue;
            String department=text(role,"department_id");
            if(department!=null&&facts.rows("employments").stream().noneMatch(em->key.employeeId().equals(text(em,"employee_id"))&&key.companyId().equals(text(em,"company_id"))&&department.equals(text(em,"department_id"))&&"ACTIVE".equals(text(em,"status"))))continue;
            if(facts.rows("employments").stream().noneMatch(em->key.employeeId().equals(text(em,"employee_id"))&&key.companyId().equals(text(em,"company_id"))
                    &&grant.source().departmentId().equals(text(em,"department_id"))&&"ACTIVE".equals(text(em,"status"))))continue;
            for(String permission:available(role,after,systems,nodes,permissions,configs,change))if("SYSTEM_ACCESS".equals(text(permissions.get(permission),"permission_type")))return true;
        }
        return false;
    }
    private static Set<String> available(Map<String,Object> role,boolean after,Map<String,Map<String,Object>> systems,Map<String,Map<String,Object>> nodes,
            Map<String,Map<String,Object>> permissions,Map<String,Set<String>> configs,Change change){
        String system=text(role,"system_id");boolean enabled=after?change.systemStates().getOrDefault(system,flag(systems.get(system),"enabled")):flag(systems.get(system),"enabled");
        if(!enabled||!"EFFECTIVE".equals(text(role,"state")))return Set.of();
        var result=new HashSet<String>();var selected=after?change.rolePermissions().getOrDefault(text(role,"id"),configs.getOrDefault(text(role,"id"),Set.of())):configs.getOrDefault(text(role,"id"),Set.of());
        for(String id:selected){
            var permission=permissions.get(id);if(permission==null||!system.equals(text(permission,"system_id")))continue;
            boolean active=after&&change.permissions().containsKey(id)?change.permissions().get(id).enabled():flag(permission,"enabled");if(!active)continue;
            String function=text(permission,"function_id");if(function==null){if("SYSTEM_ACCESS".equals(text(permission,"permission_type")))result.add(id);continue;}
            boolean effective=true;var visited=new HashSet<String>();
            for(String current=function;current!=null;){
                var node=nodes.get(current);if(node==null||!system.equals(text(node,"system_id"))||!visited.add(current)){effective=false;break;}
                var overlay=after?change.nodes().get(current):null;if(!(overlay==null?flag(node,"enabled"):overlay.enabled())){effective=false;break;}
                current=overlay==null?text(node,"parent_id"):overlay.parentId();
            }
            if(effective)result.add(id);
        }
        return result;
    }
    public static boolean covers(ManagementAccess.Authority a,String company,String department,String system,boolean sharedRole){return a.global()||Objects.equals(a.companyId(),company)&&a.system(system)&&(Objects.equals(a.departmentId(),department)||sharedRole&&department==null);}
    private JsonNode target(String type,String id,Source source,String company,String department,String system,Long version,String effect,String summary){
        var result=json.createObjectNode().put("targetType",type).put("targetId",id).put("companyId",company).put("departmentId",department).put("systemId",system).put("effect",effect).put("summary",summary);
        result.set("targetVersion",json.valueToTree(version));result.set("source",json.valueToTree(source));return result;
    }
    static Source source(Map<String,Object> r){return new Source(text(r,"employee_id"),text(r,"company_id"),text(r,"department_id"),text(r,"system_id"));}
    static String text(Map<String,Object> r,String k){return r==null||r.get(k)==null?null:r.get(k).toString();}
    static long number(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
    static boolean flag(Map<String,Object> r,String k){Object v=r==null?null:r.get(k);return Boolean.TRUE.equals(v)||v instanceof Number n&&n.intValue()!=0;}
}
