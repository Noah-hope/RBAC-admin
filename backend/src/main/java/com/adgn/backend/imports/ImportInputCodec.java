package com.adgn.backend.imports;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementInput;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static com.adgn.backend.imports.ImportPlan.*;

/** Strict existing ImportInput contract; normalizes ordering, never guesses source columns. */
public final class ImportInputCodec {
    private static final Set<String> FIELDS=Set.of("employeeId","employeeKey","previousEmployeeKey","name","action","companyCode","departmentCode","positionCode","employmentId","expectedVersion","companyName","departmentName","positionName","targetCompanyCode","employmentState","startedAt","endedAt");
    private ImportInputCodec() { }
    public static Input parse(JsonNode node){
        shape(node,Set.of("fileId","sourceSystem","sourceBatchId","importType","worksheet","fieldMappings","organizationMappings","initialEmploymentState","reason"));
        String file=ManagementInput.identifier(string(node,"fileId",64)),system=string(node,"sourceSystem",100),batch=string(node,"sourceBatchId",100),type=string(node,"importType",32),sheet=string(node,"worksheet",100),initial=string(node,"initialEmploymentState",32),reason=string(node,"reason",500);
        if(!Set.of("ORGANIZATION","EMPLOYEE_INITIALIZATION","PERSONNEL_CHANGES").contains(type)||!Set.of("EXPLICIT_ACTIVE","FROM_SOURCE").contains(initial))throw IdentityFailure.invalid();
        var fields=node.get("fieldMappings");array(fields,1,100);var mappings=new ArrayList<FieldMapping>();var source=new HashSet<String>();var target=new HashSet<String>();
        for(var field:fields){shape(field,Set.of("sourceColumn","targetField"));String column=string(field,"sourceColumn",100),name=string(field,"targetField",100);if(!FIELDS.contains(name)||!source.add(column)||!target.add(name))throw IdentityFailure.invalid();mappings.add(new FieldMapping(column,name));}
        mappings.sort(Comparator.comparing(FieldMapping::targetField));
        var organizations=node.get("organizationMappings");array(organizations,0,1000);var paths=new HashSet<String>();var orgs=new ArrayList<OrganizationMapping>();
        for(var org:organizations){shape(org,Set.of("sourcePath","companyId","departmentId","companyCode","departmentCode"));String path=string(org,"sourcePath",300);if(!paths.add(path))throw IdentityFailure.invalid();orgs.add(new OrganizationMapping(path,nullable(org,"companyId",64,true),nullable(org,"departmentId",64,true),nullable(org,"companyCode",100,false),nullable(org,"departmentCode",100,false)));}
        orgs.sort(Comparator.comparing(OrganizationMapping::sourcePath));return new Input(file,system,batch,type,sheet,List.copyOf(mappings),List.copyOf(orgs),initial,reason);
    }
    private static void shape(JsonNode n,Set<String> keys){if(n==null||!n.isObject()||!n.propertyNames().equals(keys))throw IdentityFailure.invalid();}
    private static void array(JsonNode n,int min,int max){if(n==null||!n.isArray()||n.size()<min||n.size()>max)throw IdentityFailure.invalid();}
    private static String string(JsonNode n,String key,int max){var value=n.get(key);if(value==null||!value.isString())throw IdentityFailure.invalid();return ManagementInput.text(value.stringValue(),1,max);}
    private static String nullable(JsonNode n,String key,int max,boolean identifier){if(n.get(key).isNull())return null;String value=string(n,key,max);if(identifier)return ManagementInput.identifier(value);if(!value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]*"))throw IdentityFailure.invalid();return value;}
}
