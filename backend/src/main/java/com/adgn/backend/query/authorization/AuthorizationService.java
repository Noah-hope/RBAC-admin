package com.adgn.backend.query.authorization;

import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.*;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.query.management.ManagementInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;

/** Independent SQL projections: one fresh primary snapshot, no permission cache or domain commands. */
public final class AuthorizationService {
    public record Menu(String id,String code,String parentId,String name,boolean containerOnly,int sortOrder){ }
    public record Operation(String permissionId,String permissionCode,String functionId,String functionCode){ }
    public record Result(String employeeKey,String companyId,String systemId,String systemCode,boolean canAccessSystem,
                         List<Menu> menus,List<Operation> operations,Instant evaluatedAt,String requestId){ }
    public record Check(String employeeKey,String companyId,String systemId,String permissionCode,String permissionType,
                        boolean hasPermission,Instant evaluatedAt,String requestId){ }
    private record Input(String employee,String company,String code){ }
    private record SystemView(String id,String code,boolean enabled){ }
    private record Node(String id,String code,String parent,String name,String type,boolean enabled,String view,int order){ }
    private record Permission(String id,String code,String type,String function,boolean enabled,boolean menu){ }
    private record Evaluation(Result result,Map<String,Permission> permissions,Set<String> effective){ }
    private final JdbcTemplate jdbc;private final ReadBoundary reads;private final CurrentIdentityReader current;private final Clock clock;
    public AuthorizationService(DataSource source,ReadBoundary reads,CurrentIdentityReader current,Clock clock){this.jdbc=new JdbcTemplate(source);this.reads=reads;this.current=current;this.clock=clock;}
    public Result query(Jwt token,JsonNode raw,String requestId)throws Exception{
        var input=input(raw,false);return reads.read(()->current.service(token,IdentityTokens.Family.BUSINESS),service->evaluate(service.systemId(),input,requestId,true).result());
    }
    public Check check(Jwt token,JsonNode raw,String requestId)throws Exception{
        var input=input(raw,true);
        return reads.read(()->current.service(token,IdentityTokens.Family.BUSINESS),service->{
            var evaluation=evaluate(service.systemId(),input,requestId,false);var r=evaluation.result();
            var permission=evaluation.permissions().values().stream().filter(p->p.code().equals(input.code())).findFirst().orElse(null);
            return new Check(input.employee(),input.company(),service.systemId(),input.code(),permission==null?null:permission.type(),
                r.canAccessSystem()&&permission!=null&&evaluation.effective().contains(permission.id()),r.evaluatedAt(),requestId);
        });
    }
    private Evaluation evaluate(String systemId,Input input,String requestId,boolean fullResponse){
        var systems=jdbc.query("SELECT id,code,enabled FROM business_system WHERE id=?",(r,n)->new SystemView(r.getString(1),r.getString(2),r.getBoolean(3)),systemId);
        if(systems.size()!=1)throw broken();var system=systems.getFirst();if(!system.enabled())throw new IdentityFailure(403,"FORBIDDEN");
        var nodes=new HashMap<String,Node>();for(var node:jdbc.query("SELECT id,code,parent_id,name,node_type,enabled,visibility_permission_id,sort_order FROM directory_node WHERE system_id=?",(r,n)->new Node(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBoolean(6),r.getString(7),r.getInt(8)),systemId))nodes.put(node.id(),node);
        var permissions=new HashMap<String,Permission>();for(var permission:jdbc.query("SELECT id,code,permission_type,function_id,enabled,menu_eligible FROM permission WHERE system_id=?",(r,n)->new Permission(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5),r.getBoolean(6)),systemId))permissions.put(permission.id(),permission);
        validate(nodes,permissions);
        var effective=new HashSet<String>();
        var employees=jdbc.queryForList("SELECT e.id,e.status FROM employee e WHERE e.employee_key=? AND EXISTS(SELECT 1 FROM company c WHERE c.id=?)",input.employee(),input.company());
        if(!employees.isEmpty()&&"ACTIVE".equals(employees.getFirst().get("status"))){
            String employee=employees.getFirst().get("id").toString();var departments=new HashSet<>(jdbc.queryForList("SELECT DISTINCT department_id FROM employment WHERE employee_id=? AND company_id=? AND status='ACTIVE'",String.class,employee,input.company()));
            var roles=new HashSet<String>();
            var grants=jdbc.queryForList("SELECT g.id,g.source_epoch AS granted_epoch,g.relation_version AS granted_version,s.source_epoch,b.relation_version,s.department_id,r.id AS role_id,r.department_id AS role_department,r.state,r.configuration_version FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id JOIN authorization_source s ON s.id=b.source_id JOIN business_role r ON r.id=b.role_id WHERE s.employee_id=? AND s.company_id=? AND s.system_id=? AND b.company_id=s.company_id AND b.system_id=s.system_id AND r.company_id=s.company_id AND r.system_id=s.system_id AND g.status='ACTIVE'",employee,input.company(),systemId);
            for(var grant:grants){
                if(number(grant,"granted_epoch")!=number(grant,"source_epoch")||number(grant,"granted_version")!=number(grant,"relation_version")||!"EFFECTIVE".equals(grant.get("state"))||number(grant,"configuration_version")==0)continue;
                var evidence=jdbc.queryForList("SELECT e.employee_id,e.company_id,e.department_id FROM grant_employment_evidence ge JOIN employment e ON e.id=ge.employment_id WHERE ge.grant_id=?",grant.get("id"));
                if(evidence.isEmpty()||evidence.stream().anyMatch(e->!employee.equals(e.get("employee_id"))||!input.company().equals(e.get("company_id"))||!grant.get("department_id").equals(e.get("department_id"))))throw broken();
                if(!departments.contains(grant.get("department_id"))||grant.get("role_department")!=null&&!departments.contains(grant.get("role_department")))continue;
                roles.add(grant.get("role_id").toString());
            }
            if(!roles.isEmpty()){
                for(var row:jdbc.queryForList("SELECT role_id,permission_id FROM role_permission WHERE system_id=?",systemId))if(roles.contains(row.get("role_id"))){
                    var permission=permissions.get(row.get("permission_id"));if(permission==null)throw broken();
                    if(permission.enabled()&&(permission.function()==null||enabled(permission.function(),nodes)))effective.add(permission.id());
                }
            }
        }
        boolean enter=permissions.values().stream().anyMatch(p->p.type().equals("SYSTEM_ACCESS")&&effective.contains(p.id()));
        var operations=new ArrayList<Operation>();var visible=new HashSet<String>();var openable=new HashSet<String>();
        if(enter){
            for(var p:permissions.values())if(p.type().equals("FUNCTION_OPERATION")&&effective.contains(p.id()))operations.add(new Operation(p.id(),p.code(),p.function(),nodes.get(p.function()).code()));
            for(var node:nodes.values())if(node.view()!=null&&effective.contains(node.view())&&enabled(node.id(),nodes)){
                openable.add(node.id());for(String id=node.id();id!=null;id=nodes.get(id).parent())visible.add(id);
            }
        }
        operations.sort(Comparator.comparing(Operation::permissionCode).thenComparing(Operation::permissionId));
        var menus=new ArrayList<Menu>();var children=new HashMap<String,List<Node>>();
        for(String id:visible){var node=nodes.get(id);children.computeIfAbsent(node.parent()==null?"":node.parent(),k->new ArrayList<>()).add(node);}
        var order=Comparator.comparingInt(Node::order).thenComparing(Node::code).thenComparing(Node::id);children.values().forEach(list->list.sort(order));
        var stack=new ArrayDeque<Node>();pushReverse(stack,children.getOrDefault("",List.of()));
        while(!stack.isEmpty()){var n=stack.pop();menus.add(new Menu(n.id(),n.code(),n.parent(),n.name(),!openable.contains(n.id()),n.order()));pushReverse(stack,children.getOrDefault(n.id(),List.of()));}
        if(fullResponse&&(menus.size()>10000||operations.size()>10000))throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        return new Evaluation(new Result(input.employee(),input.company(),system.id(),system.code(),enter,List.copyOf(menus),List.copyOf(operations),clock.instant(),requestId),permissions,effective);
    }
    private static void pushReverse(Deque<Node> stack,List<Node> nodes){for(int i=nodes.size()-1;i>=0;i--)stack.push(nodes.get(i));}
    private static boolean enabled(String start,Map<String,Node> nodes){for(String id=start;id!=null;id=nodes.get(id).parent())if(!nodes.get(id).enabled())return false;return true;}
    private static void validate(Map<String,Node> nodes,Map<String,Permission> permissions){
        long access=0;for(var p:permissions.values()){
            if(p.type().equals("SYSTEM_ACCESS")){access++;if(p.function()!=null||p.menu())throw broken();}
            else if(!p.type().equals("FUNCTION_OPERATION")||!nodes.containsKey(p.function())||!nodes.get(p.function()).type().equals("FUNCTION"))throw broken();
        }
        if(access!=1)throw broken();
        for(var node:nodes.values()){
            var visited=new HashSet<String>();for(String id=node.id();id!=null;){var n=nodes.get(id);if(n==null||!visited.add(id))throw broken();id=n.parent();}
            if(node.type().equals("NAVIGATION")){if(node.view()!=null)throw broken();}
            else if(!node.type().equals("FUNCTION")||node.enabled()&&node.view()==null)throw broken();
            if(node.view()!=null){var permission=permissions.get(node.view());if(permission==null||!node.id().equals(permission.function())||!permission.menu())throw broken();}
        }
    }
    private static Input input(JsonNode raw,boolean check){
        var allowed=check?Set.of("employeeKey","companyId","permissionCode"):Set.of("employeeKey","companyId");
        if(raw==null||!raw.isObject()||!raw.propertyNames().equals(allowed))throw IdentityFailure.invalid();
        for(String key:allowed)if(!raw.get(key).isString())throw IdentityFailure.invalid();
        String employee=ManagementInput.text(raw.get("employeeKey").stringValue(),1,100),company=ManagementInput.identifier(raw.get("companyId").stringValue());
        String code=check?ManagementInput.text(raw.get("permissionCode").stringValue(),1,100):null;
        if(check&&!code.matches("[a-zA-Z0-9][a-zA-Z0-9_.:-]*"))throw IdentityFailure.invalid();return new Input(employee,company,code);
    }
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static PersistenceFailure broken(){return new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);}
}
