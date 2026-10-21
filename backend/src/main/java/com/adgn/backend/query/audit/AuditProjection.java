package com.adgn.backend.query.audit;

import com.adgn.backend.query.change.ChangeQueryService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** 历史快照的公开摘要；未知字段不透传，不使用当前名称或当前授权重建历史。 */
public final class AuditProjection {
    public record Difference(String field,String before,String after) { }
    private static final Set<String> LEAVES=Set.of("id","name","code","description","state","status","enabled","version","metadataVersion","configurationVersion","permissionIds","systemIds","accountIds","appointmentIds","companyId","departmentId","systemId","employeeId","roleId","grantId","sourceCompanyId","sourceDepartmentId","employmentIds","relationVersion","sourceEpoch","sourceVersion","sourceId","baselineId","positionId","permissionType","functionId","menuEligible","parentId","sortOrder","reason","endedReason","draftVersion","expectedConfigurationVersion","expectedRoleConfigurationVersion","expectedRelationVersion","expectedSourceEpoch");
    private static final Set<String> CONTAINERS=Set.of("role","system","scope","draft","content","targets","items","change","input","sourceBefore","sourceAfter","relations","before","after","snapshot");
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private AuditProjection() { }
    public static List<Difference> differences(JsonNode snapshot){
        if(snapshot==null||!snapshot.isObject()||!snapshot.path("schemaVersion").isIntegralNumber()||snapshot.path("schemaVersion").intValue()!=1)throw ChangeQueryService.integrity();
        var before=new TreeMap<String,String>();var after=new TreeMap<String,String>();
        if(snapshot.has("before")||snapshot.has("after")){flatten(snapshot.get("before"),"",before,0);flatten(snapshot.get("after"),"",after,0);}
        else flatten(snapshot,"",after,0);
        var fields=new TreeSet<>(before.keySet());fields.addAll(after.keySet());var out=new ArrayList<Difference>();
        for(String field:fields)if(!Objects.equals(before.get(field),after.get(field))){if(out.size()==99){out.add(new Difference("摘要说明",null,"其他字段未在本次摘要展开"));break;}out.add(new Difference(bounded(field,200),before.get(field),after.get(field)));}
        return List.copyOf(out);
    }
    private static void flatten(JsonNode n,String path,Map<String,String> out,int depth){
        if(n==null||n.isNull()||depth>12)return;
        if(n.isArray()){int i=0;for(var item:n){flatten(item,path+"["+i+++"]",out,depth+1);if(out.size()>1000)break;}return;}
        if(!n.isObject())return;
        for(var e:n.properties()){
            String name=e.getKey(),field=path.isEmpty()?name:path+"."+name;
            if(LEAVES.contains(name)){
                var value=e.getValue();if(value.isNull())continue;
                // 集合型公开字段只允许字符串/数字/布尔值，禁止用同名嵌套对象夹带内部数据。
                if(value.isContainer()){if(!value.isArray())continue;boolean simple=true;for(var item:value)if(item.isContainer())simple=false;if(!simple)continue;}
                out.put(field,bounded(value.isString()?value.stringValue():JSON.writeValueAsString(value),2000));
            }else if(CONTAINERS.contains(name))flatten(e.getValue(),field,out,depth+1);
        }
    }
    private static String bounded(String value,int max){int count=value.codePointCount(0,value.length());if(count<=max)return value;String marker="…（摘要已截断）";return value.substring(0,value.offsetByCodePoints(0,max-marker.codePointCount(0,marker.length())))+marker;}
}
