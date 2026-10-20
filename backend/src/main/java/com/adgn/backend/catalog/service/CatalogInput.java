package com.adgn.backend.catalog.service;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementInput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;

/** Strict four-way contract parsing, including required nullable reference fields. */
public final class CatalogInput {
    private CatalogInput() { }
    public record Batch(long expectedCatalogVersion,List<JsonNode> commands,String reason){public Batch{commands=List.copyOf(commands);}}
    public static Batch parse(JsonNode input,boolean save){
        fields(input,save?Set.of("expectedCatalogVersion","commands","reason","previewId"):Set.of("expectedCatalogVersion","commands","reason"));
        if(save)identifier(input.get("previewId"),false);
        long version=version(input.get("expectedCatalogVersion"));String reason=text(input.get("reason"),1,500);
        var commands=input.get("commands");if(!commands.isArray()||commands.size()<1||commands.size()>100)throw invalid();
        var result=new ArrayList<JsonNode>();var locals=new HashSet<String>();var updates=new HashSet<String>();
        for(var command:commands){
            String kind=text(command.get("kind"),1,32);
            Set<String> required=switch(kind){
                case "NODE_CREATE"->Set.of("kind","localKey","code","name","nodeType","parentId","parentLocalKey","sortOrder","enabled","visibilityPermissionId","visibilityPermissionLocalKey");
                case "NODE_UPDATE"->Set.of("kind","id","expectedVersion","name","parentId","sortOrder","enabled","visibilityPermissionId");
                case "PERMISSION_CREATE"->Set.of("kind","localKey","functionId","functionLocalKey","code","name","menuEligible","enabled");
                case "PERMISSION_UPDATE"->Set.of("kind","id","expectedVersion","name","menuEligible","enabled");
                default->throw invalid();
            };
            fields(command,required);ObjectNode normalized=(ObjectNode)command.deepCopy();normalized.put("kind",kind);normalized.put("name",text(command.get("name"),1,100));bool(command.get("enabled"));
            if(kind.endsWith("CREATE")){
                String local=identifier(command.get("localKey"),false);if(!locals.add(local))throw invalid();normalized.put("localKey",local);
                String code=text(command.get("code"),1,100);if(!code.matches("[a-zA-Z0-9][a-zA-Z0-9_.:-]*"))throw invalid();normalized.put("code",code);
            }else{
                String id=identifier(command.get("id"),false);normalized.put("id",id);version(command.get("expectedVersion"));if(!updates.add(kind+":"+id))throw invalid();
            }
            if(kind.startsWith("NODE")){
                identifier(command.get("parentId"),true);identifier(command.get("visibilityPermissionId"),true);
                if(!command.get("sortOrder").isIntegralNumber()||!command.get("sortOrder").canConvertToInt())throw invalid();
                if(kind.equals("NODE_CREATE")){
                    String type=text(command.get("nodeType"),1,32);if(!Set.of("NAVIGATION","FUNCTION").contains(type))throw invalid();normalized.put("nodeType",type);
                    exclusive(command.get("parentId"),command.get("parentLocalKey"),false);exclusive(command.get("visibilityPermissionId"),command.get("visibilityPermissionLocalKey"),false);
                    boolean binding=!command.get("visibilityPermissionId").isNull()||!command.get("visibilityPermissionLocalKey").isNull();
                    if(type.equals("NAVIGATION")&&binding||type.equals("FUNCTION")&&bool(command.get("enabled"))&&!binding)throw invalid();
                }
            }else{
                bool(command.get("menuEligible"));
                if(kind.equals("PERMISSION_CREATE"))exclusive(command.get("functionId"),command.get("functionLocalKey"),true);
            }
            for(String field:List.of("id","parentId","parentLocalKey","visibilityPermissionId","visibilityPermissionLocalKey","functionId","functionLocalKey"))
                if(normalized.has(field)&&!normalized.get(field).isNull())normalized.put(field,identifier(normalized.get(field),false));
            result.add(normalized);
        }
        return new Batch(version,result,reason);
    }
    public static void fields(JsonNode input,Set<String> names){if(input==null||!input.isObject()||!input.propertyNames().equals(names))throw invalid();}
    public static long version(JsonNode value){if(value==null||!value.isIntegralNumber()||!value.canConvertToLong())throw invalid();return ManagementInput.version(value.longValue());}
    public static String text(JsonNode value,int min,int max){if(value==null||!value.isString())throw invalid();return ManagementInput.text(value.stringValue(),min,max);}
    public static String identifier(JsonNode value,boolean nullable){if(value==null)throw invalid();if(value.isNull()&&nullable)return null;return ManagementInput.identifier(text(value,1,64));}
    public static boolean bool(JsonNode value){if(value==null||!value.isBoolean())throw invalid();return value.booleanValue();}
    private static void exclusive(JsonNode id,JsonNode local,boolean required){identifier(id,true);identifier(local,true);if(!id.isNull()&&!local.isNull()||required&&id.isNull()&&local.isNull())throw invalid();}
    private static IdentityFailure invalid(){return IdentityFailure.invalid();}
}
