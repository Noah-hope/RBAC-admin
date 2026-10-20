package com.adgn.backend.catalog.service;

import com.adgn.backend.catalog.repository.CatalogRepository.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementInput;
import com.adgn.backend.query.preview.ImpactPolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** Complete final aggregate validation, independent of command ordering and SQL. */
public final class CatalogPlanner {
    public record Plan(Snapshot after,Map<String,String> bindings,ImpactPolicy.Change impact,boolean changed){public Plan{bindings=Map.copyOf(bindings);}}
    private final JsonMapper json;
    public CatalogPlanner(JsonMapper json){this.json=json;}
    public Plan plan(String system,long catalogVersion,Snapshot before,CatalogInput.Batch input,Map<String,String> bindings){
        if(catalogVersion!=input.expectedCatalogVersion())throw new IdentityFailure(409,"VERSION_CONFLICT");
        var nodes=new LinkedHashMap<>(before.nodes());var permissions=new LinkedHashMap<>(before.permissions());var expectedLocals=new HashSet<String>();
        for(var c:input.commands())if(c.has("localKey"))expectedLocals.add(c.get("localKey").stringValue());
        if(!bindings.keySet().equals(expectedLocals)||new HashSet<>(bindings.values()).size()!=bindings.size())throw invalid();
        for(String id:bindings.values())if(nodes.containsKey(id)||permissions.containsKey(id))throw invalid();
        for(var c:input.commands()){
            String kind=string(c,"kind");
            switch(kind){
                case "NODE_CREATE"->{String id=bindings.get(string(c,"localKey"));nodes.put(id,new Node(id,system,string(c,"code"),string(c,"name"),string(c,"nodeType"),reference(c,"parentId","parentLocalKey",bindings),c.get("sortOrder").intValue(),c.get("enabled").booleanValue(),reference(c,"visibilityPermissionId","visibilityPermissionLocalKey",bindings),1));}
                case "PERMISSION_CREATE"->{String id=bindings.get(string(c,"localKey"));permissions.put(id,new Permission(id,system,string(c,"code"),string(c,"name"),"FUNCTION_OPERATION",reference(c,"functionId","functionLocalKey",bindings),c.get("menuEligible").booleanValue(),c.get("enabled").booleanValue(),1));}
                case "NODE_UPDATE"->{
                    String id=string(c,"id");var old=before.nodes().get(id);if(old==null)throw invalid();checkVersion(old.version(),c);
                    nodes.put(id,new Node(id,system,old.code(),string(c,"name"),old.nodeType(),string(c,"parentId"),c.get("sortOrder").intValue(),c.get("enabled").booleanValue(),string(c,"visibilityPermissionId"),old.version()));
                }
                case "PERMISSION_UPDATE"->{
                    String id=string(c,"id");var old=before.permissions().get(id);if(old==null)throw invalid();checkVersion(old.version(),c);
                    if(old.permissionType().equals("SYSTEM_ACCESS")&&c.get("menuEligible").booleanValue())throw invalid();
                    permissions.put(id,new Permission(id,system,old.code(),string(c,"name"),old.permissionType(),old.functionId(),c.get("menuEligible").booleanValue(),c.get("enabled").booleanValue(),old.version()));
                }
                default->throw invalid();
            }
        }
        var codes=new HashSet<String>();for(var n:nodes.values())if(!codes.add(n.code()))throw conflict();
        codes.clear();var names=new HashSet<String>();int access=0;
        for(var p:permissions.values()){
            if(!codes.add(p.code()))throw conflict();
            if(p.permissionType().equals("SYSTEM_ACCESS")){access++;if(p.functionId()!=null||p.menuEligible())throw invalid();}
            else{var function=nodes.get(p.functionId());if(function==null||!function.nodeType().equals("FUNCTION")||!names.add(p.functionId()+":"+p.name()))throw invalid();}
        }
        if(access!=1)throw invalid();
        for(var n:nodes.values())if(n.parentId()!=null&&!nodes.containsKey(n.parentId()))throw invalid();
        for(var n:nodes.values()){
            var visited=new HashSet<String>();for(String current=n.id();current!=null;current=nodes.get(current).parentId())if(!visited.add(current))throw invalid();
            if(n.nodeType().equals("NAVIGATION")){if(n.visibilityPermissionId()!=null)throw invalid();continue;}
            if(n.enabled()&&n.visibilityPermissionId()==null)throw invalid();
            if(n.visibilityPermissionId()!=null){
                var p=permissions.get(n.visibilityPermissionId());if(p==null||!n.id().equals(p.functionId())||!p.menuEligible())throw invalid();
                var old=before.nodes().get(n.id());boolean activating=old==null||!old.enabled()||!Objects.equals(old.visibilityPermissionId(),n.visibilityPermissionId());
                if(n.enabled()&&activating&&!p.enabled())throw invalid();
            }
        }
        var overlays=new TreeMap<String,ImpactPolicy.NodeState>();var permissionStates=new TreeMap<String,ImpactPolicy.PermissionState>();var touched=new HashSet<String>();var targets=new ArrayList<JsonNode>();
        for(var entry:new ArrayList<>(nodes.entrySet())){
            var n=entry.getValue();var old=before.nodes().get(n.id());if(n.equals(old))continue;
            long version=old==null?1:next(old.version());
            nodes.put(n.id(),new Node(n.id(),system,n.code(),n.name(),n.nodeType(),n.parentId(),n.sortOrder(),n.enabled(),n.visibilityPermissionId(),version));
            overlays.put(n.id(),new ImpactPolicy.NodeState(n.parentId(),n.enabled(),n.visibilityPermissionId()));
            if(old!=null&&(old.enabled()!=n.enabled()||!Objects.equals(old.parentId(),n.parentId())||!Objects.equals(old.visibilityPermissionId(),n.visibilityPermissionId())))
                for(var p:permissions.values())if(p.functionId()!=null&&(descendant(p.functionId(),n.id(),nodes)||descendant(p.functionId(),n.id(),before.nodes())))touched.add(p.id());
            targets.add(target("DIRECTORY_NODE",n.id(),system,old==null?null:old.version(),old!=null&&old.enabled()!=n.enabled()?n.enabled()?"ENABLE":"DISABLE":"CONFIGURATION_CHANGE"));
        }
        for(var entry:new ArrayList<>(permissions.entrySet())){
            var p=entry.getValue();var old=before.permissions().get(p.id());if(p.equals(old))continue;
            long version=old==null?1:next(old.version());
            permissions.put(p.id(),new Permission(p.id(),system,p.code(),p.name(),p.permissionType(),p.functionId(),p.menuEligible(),p.enabled(),version));
            permissionStates.put(p.id(),new ImpactPolicy.PermissionState(p.enabled(),p.menuEligible()));
            if(old!=null&&(old.enabled()!=p.enabled()||old.menuEligible()!=p.menuEligible()))touched.add(p.id());
            targets.add(target("PERMISSION",p.id(),system,old==null?null:old.version(),old!=null&&old.enabled()!=p.enabled()?p.enabled()?"ENABLE":"DISABLE":"CONFIGURATION_CHANGE"));
        }
        boolean changed=!overlays.isEmpty()||!permissionStates.isEmpty();if(changed&&catalogVersion>=ManagementInput.MAX_VERSION)throw conflict();
        var impact=new ImpactPolicy.Change(Set.of(system),Map.of(),overlays,permissionStates,Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),List.of(),touched,targets);
        return new Plan(new Snapshot(nodes,permissions),bindings,impact,changed);
    }
    private static boolean descendant(String start,String ancestor,Map<String,Node> nodes){var visited=new HashSet<String>();for(String id=start;id!=null&&visited.add(id);){if(id.equals(ancestor))return true;var n=nodes.get(id);if(n==null)return false;id=n.parentId();}return false;}
    private JsonNode target(String type,String id,String system,Long version,String effect){var n=json.createObjectNode().put("targetType",type).put("targetId",id).putNull("source").putNull("companyId").putNull("departmentId").put("systemId",system).put("effect",effect).put("summary","目录聚合变更");n.set("targetVersion",json.valueToTree(version));return n;}
    private static String reference(JsonNode c,String id,String local,Map<String,String> bindings){String key=string(c,local);if(key==null)return string(c,id);String result=bindings.get(key);if(result==null)throw invalid();return result;}
    private static String string(JsonNode c,String key){var n=c.get(key);return n==null||n.isNull()?null:n.stringValue();}
    private static void checkVersion(long current,JsonNode c){if(current!=c.get("expectedVersion").longValue())throw new IdentityFailure(409,"VERSION_CONFLICT");}
    private static long next(long n){if(n>=ManagementInput.MAX_VERSION)throw conflict();return n+1;}
    private static IdentityFailure invalid(){return IdentityFailure.invalid();}
    private static IdentityFailure conflict(){return new IdentityFailure(409,"STATE_CONFLICT");}
}
