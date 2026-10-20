package com.adgn.backend.change;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementInput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** 显式解析三类互斥内容；规范集合但保留明确配对的行顺序。 */
public final class DraftInput {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private DraftInput() { }
    public record Draft(JsonNode content,String reason,String copiedFromRequestId,Long expectedDraftVersion) { }
    public record Role(String name,String description,String companyId,String departmentId,String systemId,String reason) { }
    public static Role role(JsonNode n) {
        fields(n,Set.of("name","description","companyId","departmentId","systemId","reason"),Set.of("name","companyId","departmentId","systemId","reason"));
        return new Role(text(n,"name",1,100),n.has("description")?text(n,"description",0,500):"",id(n,"companyId"),n.get("departmentId").isNull()?null:id(n,"departmentId"),id(n,"systemId"),text(n,"reason",1,500));
    }
    public static Draft draft(JsonNode n,boolean edit) {
        fields(n,edit?Set.of("content","reason","expectedDraftVersion"):Set.of("content","reason","copiedFromRequestId"),edit?Set.of("content","reason","expectedDraftVersion"):Set.of("content","reason"));
        return new Draft(content(n.get("content")),text(n,"reason",1,500),!edit&&n.has("copiedFromRequestId")?id(n,"copiedFromRequestId"):null,edit?version(n,"expectedDraftVersion"):null);
    }
    public static JsonNode content(JsonNode n) {
        String kind=text(n,"kind",1,32);var out=new TreeMap<String,Object>();out.put("kind",kind);out.put("companyId",id(n,"companyId"));
        if(kind.equals("ROLE_CONFIGURATION")) {
            fields(n,Set.of("kind","companyId","roleId","expectedConfigurationVersion","permissionIds"),Set.of("kind","companyId","roleId","expectedConfigurationVersion","permissionIds"));
            out.put("roleId",id(n,"roleId"));out.put("expectedConfigurationVersion",version(n,"expectedConfigurationVersion"));out.put("permissionIds",ids(n.get("permissionIds"),0,10000));
        } else if(Set.of("ROLE_GRANT","ROLE_REVOCATION").contains(kind)) {
            fields(n,Set.of("kind","companyId","items"),Set.of("kind","companyId","items"));
            var items=n.get("items");if(!items.isArray()||items.isEmpty()||items.size()>100)throw invalid();
            var lines=new ArrayList<Object>();var keys=new HashSet<List<String>>();
            for(var line:items) {
                var row=new TreeMap<String,Object>();List<String> key;
                if(kind.equals("ROLE_GRANT")) {
                    var names=Set.of("employeeId","roleId","sourceCompanyId","sourceDepartmentId","expectedRoleConfigurationVersion","expectedSourceEpoch","expectedRelationVersion","employmentIds");fields(line,names,names);
                    for(String f:List.of("employeeId","roleId","sourceCompanyId","sourceDepartmentId"))row.put(f,id(line,f));
                    for(String f:List.of("expectedRoleConfigurationVersion","expectedSourceEpoch","expectedRelationVersion"))row.put(f,version(line,f));
                    row.put("employmentIds",ids(line.get("employmentIds"),1,Integer.MAX_VALUE));
                    key=List.of((String)row.get("employeeId"),(String)row.get("roleId"),(String)row.get("sourceCompanyId"),(String)row.get("sourceDepartmentId"));
                } else {
                    fields(line,Set.of("grantId","expectedRelationVersion"),Set.of("grantId","expectedRelationVersion"));row.put("grantId",id(line,"grantId"));row.put("expectedRelationVersion",version(line,"expectedRelationVersion"));key=List.of((String)row.get("grantId"));
                }
                if(!keys.add(key))throw invalid();lines.add(row);
            }
            out.put("items",lines);
        } else throw invalid();
        return JSON.valueToTree(out);
    }
    public static String id(JsonNode n,String f){return ManagementInput.identifier(text(n,f,1,64));}
    public static long version(JsonNode n,String f){var v=n==null?null:n.get(f);if(v==null||!v.isIntegralNumber())throw invalid();try{return ManagementInput.version(v.bigIntegerValue().longValueExact());}catch(ArithmeticException bad){throw invalid();}}
    public static List<String> ids(JsonNode n,int min,int max){
        if(n==null||!n.isArray()||n.size()<min||n.size()>max)throw invalid();var ids=new TreeSet<String>();
        for(var v:n){if(!v.isString()||!ids.add(ManagementInput.identifier(v.stringValue())))throw invalid();}return List.copyOf(ids);
    }
    private static String text(JsonNode n,String f,int min,int max){var v=n==null?null:n.get(f);if(v==null||!v.isString())throw invalid();return ManagementInput.text(v.stringValue(),min,max);}
    private static void fields(JsonNode n,Set<String> allowed,Set<String> required){if(n==null||!n.isObject())throw invalid();for(var f:n.properties())if(!allowed.contains(f.getKey()))throw invalid();for(String f:required)if(!n.has(f))throw invalid();}
    private static IdentityFailure invalid(){return IdentityFailure.invalid();}
}
