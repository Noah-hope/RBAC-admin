package com.adgn.backend.change;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.grant.GrantReader;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.change.ChangeQueryService;
import com.adgn.backend.query.management.*;
import com.adgn.backend.query.preview.*;
import com.adgn.backend.role.repository.RoleRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;
import java.time.temporal.ChronoUnit;

/** 固定提交只写申请/投递待办；任何业务权限均由获批执行器负责。 */
public final class SubmissionService {
    record Plan(JsonNode effective,List<DraftRepository.Target> targets,List<Map<String,Object>> noChange,List<String> added,List<String> removed,Object dependencies,PreviewService.Projection projection){}
    private final JdbcTemplate jdbc;private final CommandBoundary writes;private final ManagementAccess access;private final DraftRepository drafts;private final DraftPlanner planner;private final ClaimService claims;private final GrantReader grants;private final RoleRepository roles;private final PreviewService previews;private final ImpactService impacts;private final JsonMapper json;private final AuditAppender audit;private final ChangeQueryService queries;private final ObjectProvider<IdempotencyCoordinator> coordinator;
    public SubmissionService(DataSource ds,CommandBoundary writes,ManagementAccess access,DraftRepository drafts,DraftPlanner planner,ClaimService claims,GrantReader grants,RoleRepository roles,PreviewService previews,ImpactService impacts,JsonMapper json,AuditAppender audit,ChangeQueryService queries,ObjectProvider<IdempotencyCoordinator> coordinator){this.jdbc=new JdbcTemplate(ds);this.writes=writes;this.access=access;this.drafts=drafts;this.planner=planner;this.claims=claims;this.grants=grants;this.roles=roles;this.previews=previews;this.impacts=impacts;this.json=json;this.audit=audit;this.queries=queries;this.coordinator=coordinator;}
    public Object preview(Jwt jwt,ManagementAccess.Context selection,String id,JsonNode input)throws Exception{
        fields(input,Set.of("expectedDraftVersion"));long version=DraftInput.version(input,"expectedDraftVersion");String request=ManagementInput.identifier(id);
        return writes.execute(()->access.read(jwt,selection).identity(),c->{var row=draft(c.authority(),request,version);var p=plan(c,row);
            var v=previews.create(c,c.authority(),selection,"submitChange",Map.of("draftId",request,"expectedDraftVersion",version),p.dependencies(),p.projection(),Map.of());
            var out=new LinkedHashMap<String,Object>();out.put("preview",v);out.put("draftId",request);out.put("draftVersion",version);out.put("addedPermissionIds",p.added());out.put("removedPermissionIds",p.removed());out.put("effectiveContent",p.effective());out.put("noChangeItems",p.noChange());return out;});
    }
    public IdempotencyCoordinator.Outcome submit(Jwt jwt,ManagementAccess.Context selection,String id,String key,JsonNode input,String correlation)throws Exception{
        fields(input,Set.of("expectedDraftVersion","previewId"));long version=DraftInput.version(input,"expectedDraftVersion");String preview=DraftInput.id(input,"previewId"),request=ManagementInput.identifier(id);
        var idem=coordinator.getIfAvailable();if(idem==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        return idem.execute(new IdempotencyRequest(UUID.fromString(jwt.getSubject()),"submitChange","POST","/api/v1/admin/change-requests/"+request+"/submissions",ManagementInput.key(key),json.writeValueAsBytes(input)),()->access.read(jwt,selection).identity(),(a,b)->authorize(a,b),c->{
            var row=draft(c.authority(),request,version);Plan p;try{p=plan(c,row);}catch(IdentityFailure f){if(f.status()==409||f.status()==422)throw PreviewService.stale();throw f;}
            previews.confirm(c,c.authority(),selection,preview,"submitChange",Map.of("draftId",request,"expectedDraftVersion",version),p.dependencies());
            var basis=basis(drafts.targets(request));
            if(p.effective()==null)return complete(200,Map.of("outcome","NO_CHANGE","request",drafts.view(row),"noChangeItems",p.noChange()),basis);
            if(row.kind().equals("ROLE_CONFIGURATION")&&p.effective().path("permissionIds").isEmpty())throw new IdentityFailure(422,"EMPTY_CONFIGURATION");
            var instances=jdbc.queryForList("SELECT id FROM service_identity WHERE kind='OA' AND enabled=1 ORDER BY id",String.class);if(instances.size()!=1)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
            var authority=original(c.authority(),selection,row.companyId());String at=c.now().truncatedTo(ChronoUnit.MICROS).toString();
            var payload=Map.of("requestId",request,"submittedVersion",row.draftVersion(),"content",p.effective(),"reason",row.reason(),"originalAuthority",authority,"submittedByAccountId",c.actor().id(),"submittedAt",at);
            byte[] fixed=CanonicalPayloadCodec.canonical(json.writeValueAsString(payload)),digest=CanonicalPayloadCodec.digest(fixed);
            claims.replace(c,request,row.kind(),p.targets());drafts.replaceTargets(c,request,row.kind(),p.targets());
            if(jdbc.update("UPDATE change_request SET content=?,submitted_version=?,submitted_at=?,submitted_by_account_id=?,authority_type=?,scope_id=?,appointment_id=?,original_authority=?,fixed_payload_bytes=?,content_digest=?,state='PENDING',delivery_status='PENDING_SEND',status_version=status_version+1,row_version=row_version+1 WHERE id=? AND state='DRAFT' AND draft_version=? AND status_version<9007199254740991 AND row_version<9007199254740991",json.writeValueAsString(p.effective()),row.draftVersion(),java.sql.Timestamp.from(java.time.Instant.parse(at)),c.actor().id(),c.authority().global()?"GLOBAL":"SCOPE",c.authority().scopeId(),selection.appointmentId(),json.writeValueAsString(authority),fixed,digest,request,version)!=1)throw new IdentityFailure(409,"STATE_CONFLICT");
            jdbc.update("INSERT INTO oa_outbox(request_id,oa_instance_id,next_attempt_at) VALUES(?,?,UTC_TIMESTAMP(6))",request,instances.getFirst());
            event(c,"CHANGE_SUBMITTED",row,p.targets(),correlation,Map.of("schemaVersion",1,"content",p.effective(),"contentDigest",HexFormat.of().formatHex(digest),"targets",p.targets()));
            return complete(202,Map.of("outcome","SUBMITTED","request",queries.currentView(request),"noChangeItems",p.noChange()),basis);
        });
    }
    private DraftRepository.Row draft(ManagementAccess.Authority a,String id,long version){var r=drafts.get(id);if(r==null)throw new IdentityFailure(404,"NOT_FOUND");planner.authorize(a,drafts.targets(id),r.kind().equals("ROLE_CONFIGURATION"),true);if(!r.state().equals("DRAFT"))throw new IdentityFailure(409,"STATE_CONFLICT");if(r.draftVersion()!=version)throw new IdentityFailure(409,"VERSION_CONFLICT");return r;}
    private Plan plan(WriteContext<ManagementAccess.Authority> c,DraftRepository.Row row){
        var content=DraftInput.content(row.content());var all=planner.plan(c,content);var actual=new ArrayList<DraftRepository.Target>();var no=new ArrayList<Map<String,Object>>();var added=new ArrayList<String>();var removed=new ArrayList<String>();var config=new TreeMap<String,Set<String>>();var additions=new ArrayList<ImpactPolicy.Addition>();var ended=new TreeSet<String>();var systems=new TreeSet<String>();var items=new ArrayList<JsonNode>();
        if(row.kind().equals("ROLE_CONFIGURATION")){
            String role=content.path("roleId").stringValue();var before=new TreeSet<>(roles.get(role).permissionIds());var after=new TreeSet<>(DraftInput.ids(content.path("permissionIds"),0,10000));
            added.addAll(after.stream().filter(x->!before.contains(x)).toList());removed.addAll(before.stream().filter(x->!after.contains(x)).toList());
            if(before.equals(after)&&!after.isEmpty())no.add(noChange(0,"SAME_CONFIGURATION",null));else{actual.addAll(all);config.put(role,after);}
        }else{
            var ordered=new ArrayList<Integer>();for(int i=0;i<all.size();i++)ordered.add(i);ordered.sort(Comparator.comparing(i->key(all.get(i))));
            for(int i:ordered){var t=all.get(i);if(!t.needsChange()){String grant=t.grant();if(row.kind().equals("ROLE_GRANT"))grant=grants.baseline(new GrantReader.Key(t.employee(),t.role(),t.company(),t.department())).activeGrantId();no.add(noChange(i,row.kind().equals("ROLE_GRANT")?"ALREADY_GRANTED":"ALREADY_ENDED",grant));}
                else{actual.add(t);items.add(content.path("items").get(i));if(t.grant()!=null)ended.add(t.grant());else additions.add(new ImpactPolicy.Addition("candidate_"+i,t.role(),new ImpactPolicy.Source(t.employee(),t.company(),t.department(),t.system())));}}
        }
        all.forEach(t->systems.add(t.system()));var change=new ImpactPolicy.Change(systems,Map.of(),Map.of(),Map.of(),config,Set.of(),ended,Set.of(),Set.of(),additions,Set.of(),List.of());var impact=impacts.analyze(c.authority(),change);
        JsonNode effective=actual.isEmpty()?null:content;if(effective!=null&&!row.kind().equals("ROLE_CONFIGURATION")){var object=json.createObjectNode().put("kind",row.kind()).put("companyId",row.companyId());object.set("items",json.valueToTree(items));effective=object;}
        var snapshotTargets=all.stream().map(this::snapshot).toList();actual=new ArrayList<>(actual.stream().map(this::snapshot).toList());
        var dependencies=Map.of("draftVersion",row.draftVersion(),"reason",row.reason(),"content",content,"targets",snapshotTargets,"noChange",no,"impact",impact.dependencies());
        return new Plan(effective,List.copyOf(actual),no,added,removed,Map.of("planDigest",HexFormat.of().formatHex(previews.digest(dependencies))),impact.projection());
    }
    private DraftRepository.Target snapshot(DraftRepository.Target t){
        var snapshot=(tools.jackson.databind.node.ObjectNode)t.snapshot().deepCopy();
        var input=t.snapshot().path("input");var ids=input.has("permissionIds")?DraftInput.ids(input.path("permissionIds"),0,10000):roles.get(t.role()).permissionIds();var permissions=new ArrayList<Map<String,Object>>();
        for(String id:ids)permissions.addAll(jdbc.queryForList("SELECT p.id,p.code,p.name,p.permission_type AS permissionType,p.function_id AS functionId,p.enabled,p.version,n.name AS functionName,n.version AS functionVersion FROM permission p LEFT JOIN directory_node n ON n.id=p.function_id WHERE p.id=?",id));snapshot.set("permissions",json.valueToTree(permissions));
        snapshot.set("company",json.valueToTree(jdbc.queryForMap("SELECT id,code,name,version FROM company WHERE id=?",t.company())));
        snapshot.set("system",json.valueToTree(jdbc.queryForMap("SELECT id,code,name,version,catalog_version AS catalogVersion FROM business_system WHERE id=?",t.system())));
        if(t.department()!=null)snapshot.set("department",json.valueToTree(jdbc.queryForMap("SELECT id,code,name,version FROM department WHERE id=?",t.department())));
        if(t.employee()!=null){snapshot.set("employee",json.valueToTree(jdbc.queryForMap("SELECT id,employee_key AS employeeKey,name,status,version FROM employee WHERE id=?",t.employee())));
            String filter=t.grant()==null?"e.employee_id=? AND e.company_id=? AND e.department_id=? AND e.status='ACTIVE'":"e.id IN(SELECT employment_id FROM grant_employment_evidence WHERE grant_id=?)";Object[] args=t.grant()==null?new Object[]{t.employee(),t.company(),t.department()}:new Object[]{t.grant()};
            snapshot.set("employments",json.valueToTree(jdbc.queryForList("SELECT e.id,e.status,e.version,e.position_id AS positionId,p.name AS positionName,p.version AS positionVersion,e.department_id AS departmentId,d.name AS departmentName FROM employment e JOIN `position` p ON p.id=e.position_id JOIN department d ON d.id=e.department_id WHERE "+filter+" ORDER BY e.id",args)));
        }
        return new DraftRepository.Target(t.company(),t.department(),t.system(),t.employee(),t.role(),t.source(),t.baseline(),t.grant(),t.configurationVersion(),t.sourceEpoch(),t.relationVersion(),snapshot,t.needsChange());
    }
    private static String key(DraftRepository.Target t){return String.join(":",t.employee(),t.role(),t.company(),t.department(),Objects.toString(t.grant(),""));}
    private static Map<String,Object> noChange(int index,String reason,String grant){var m=new LinkedHashMap<String,Object>();m.put("index",index);m.put("reason",reason);m.put("grantId",grant);return m;}
    private Map<String,Object> original(ManagementAccess.Authority a,ManagementAccess.Context s,String company){var m=new LinkedHashMap<String,Object>();m.put("kind",a.global()?"GLOBAL":"SCOPE");m.put("scopeId",a.scopeId());m.put("scopeVersion",a.global()?null:Long.valueOf(s.scopeVersion()));m.put("appointmentId",a.global()?null:s.appointmentId());m.put("appointmentVersion",a.global()?null:Long.valueOf(s.appointmentVersion()));m.put("companyId",company);m.put("departmentId",a.departmentId());m.put("systemIds",a.systems().stream().sorted().toList());return m;}
    private AuthorizationBasis basis(List<DraftRepository.Target> ts){var set=new LinkedHashSet<AuthorizationBasis.Target>();for(var t:ts){if(t.department()==null)return new AuthorizationBasis("submitChange",AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());set.add(new AuthorizationBasis.Target(UUID.fromString(t.company()),UUID.fromString(t.department()),UUID.fromString(t.system())));var owner=roles.ownership(t.role());if(owner!=null&&owner.departmentId()!=null)set.add(new AuthorizationBasis.Target(UUID.fromString(owner.companyId()),UUID.fromString(owner.departmentId()),UUID.fromString(owner.systemId())));}return new AuthorizationBasis("submitChange",AuthorizationBasis.Policy.SCOPED,List.copyOf(set));}
    private static void authorize(ManagementAccess.Authority a,AuthorizationBasis b){if(!b.operationId().equals("submitChange")||b.policy()==AuthorizationBasis.Policy.GLOBAL_ONLY&&!a.global()||b.policy()==AuthorizationBasis.Policy.SCOPED&&b.targets().stream().anyMatch(t->!a.role(t.companyId().toString(),t.departmentId().toString(),t.systemId().toString(),true)))throw new IdentityFailure(403,"FORBIDDEN");}
    private IdempotencyCoordinator.Completion complete(int status,Object result,AuthorizationBasis b){return new IdempotencyCoordinator.Completion(new SafeResult(status,json.writeValueAsString(result),null),b);}
    static void fields(JsonNode n,Set<String> allowed){if(n==null||!n.isObject()||!n.propertyNames().equals(allowed))throw IdentityFailure.invalid();}
    private void event(WriteContext<?> c,String event,DraftRepository.Row row,List<DraftRepository.Target> ts,String correlation,Object snapshot){var first=ts.getFirst();boolean scoped=first.department()!=null&&ts.stream().allMatch(t->Objects.equals(t.department(),first.department())&&t.system().equals(first.system()));var scope=new AuditRecord.Scope(scoped?AuditRecord.Visibility.SCOPED:AuditRecord.Visibility.GLOBAL,UUID.fromString(row.companyId()),scoped?UUID.fromString(first.department()):null,scoped?UUID.fromString(first.system()):null);audit.append(c,new AuditRecord(event,row.id(),UUID.fromString(row.id()),UUID.fromString(c.actor().id()),scope,"SUCCEEDED",correlation,row.reason(),json.writeValueAsString(snapshot),List.of(new AuditRecord.Subject("CHANGE_REQUEST",row.id()))));}
}
