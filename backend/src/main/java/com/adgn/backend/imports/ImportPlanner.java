package com.adgn.backend.imports;

import java.util.*;
import java.time.Instant;
import com.adgn.backend.query.preview.ImpactPolicy;
import static com.adgn.backend.imports.ImportPlan.*;

/** Pure planning on an immutable caller snapshot. No database or filesystem writes. */
public final class ImportPlanner {
    public ImportPlan plan(Input input,List<ImportFileParser.Row> rows,Map<String,List<Map<String,Object>>> snapshot,String actorId){
        return new Engine(input,snapshot,actorId).run(rows);
    }
    private static final long MAX=9007199254740991L;
    private static final class Problem extends RuntimeException{
        final String field,code;Problem(String field,String code){this.field=field;this.code=code;}
    }
    private static void require(boolean ok,String field,String code){if(!ok)throw new Problem(field,code);}
    private static String text(Map<String,?> r,String key){Object v=r.get(key);return v==null?"":v.toString();}
    private static long number(Map<String,?> r,String key){Object v=r.get(key);return v instanceof Number n?n.longValue():0;}
    private static boolean flag(Map<String,?> r,String key){Object v=r.get(key);return Boolean.TRUE.equals(v)||v instanceof Number n&&n.longValue()!=0;}
    private static Map<String,Object> values(Object... pairs){var map=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)map.put((String)pairs[i],pairs[i+1]);return map;}
    private static String required(Map<String,String> row,String field){String s=row.getOrDefault(field,"");require(!s.isBlank(),field,"MISSING_VALUE");return s;}
    private static Long requestedVersion(Map<String,String> row,boolean mandatory){String raw=row.getOrDefault("expectedVersion","");if(raw.isEmpty()){require(!mandatory,"expectedVersion","MISSING_VALUE");return null;}
        try{long v=Long.parseLong(raw);require(raw.matches("[0-9]+")&&v>=0&&v<=MAX,"expectedVersion","INVALID_VERSION");return v;}catch(NumberFormatException bad){throw new Problem("expectedVersion","INVALID_VERSION");}}
    private static String date(Map<String,String> row,String field){String value=row.getOrDefault(field,"");if(value.isEmpty())return null;try{return Instant.parse(value).toString();}catch(RuntimeException bad){throw new Problem(field,"INVALID_DATE");}}
    private static final class Engine{
        final Input input;final String actor;final Map<String,LinkedHashMap<String,Map<String,Object>>> tables=new HashMap<>();
        final List<Change> changes=new ArrayList<>();final List<Issue> issues=new ArrayList<>();final Set<String> candidates=new HashSet<>();
        final Set<ImpactPolicy.Source> closed=new LinkedHashSet<>();final Set<String> ended=new LinkedHashSet<>(),inactive=new LinkedHashSet<>(),revoked=new LinkedHashSet<>();
        final Map<String,String> employeeNames=new HashMap<>(),resolvedNames=new HashMap<>();final Set<Map<String,String>> seen=new HashSet<>();final Map<Integer,Integer> ordinals=new HashMap<>();
        final List<Map<String,Object>> pendingMappings=new ArrayList<>();int rowNumber;Map<String,String> sourceRow=Map.of();
        Engine(Input input,Map<String,List<Map<String,Object>>> snapshot,String actor){this.input=input;this.actor=actor;snapshot.forEach((name,rows)->{var table=new LinkedHashMap<String,Map<String,Object>>();for(var r:rows)table.put(text(r,"id"),new LinkedHashMap<>(r));tables.put(name,table);});}
        List<Map<String,Object>> all(String table){return new ArrayList<>(tables.computeIfAbsent(table,k->new LinkedHashMap<>()).values());}
        Map<String,Object> get(String table,String id){return tables.computeIfAbsent(table,k->new LinkedHashMap<>()).get(id);}
        void put(String table,Map<String,Object> value){tables.computeIfAbsent(table,k->new LinkedHashMap<>()).put(text(value,"id"),value);}
        Map<String,Object> find(String table,String field,String value){var found=all(table).stream().filter(r->text(r,field).equals(value)).toList();require(found.size()<=1,field,"AMBIGUOUS_IDENTITY");return found.isEmpty()?null:found.getFirst();}
        Map<String,Object> byCode(String table,String code,String parentField,String parent){var found=all(table).stream().filter(r->text(r,"code").equals(code)&&(parentField==null||text(r,parentField).equals(parent))).toList();require(found.size()<=1,"code","AMBIGUOUS_ORGANIZATION");return found.isEmpty()?null:found.getFirst();}
        String fresh(){return UUID.randomUUID().toString();}
        Map<String,Object> created(Object... fields){var result=values(fields);String id=fresh();result.put("id",id);result.put("version",1L);candidates.add(id);return result;}
        Map<String,Object> updated(Map<String,Object> before,Map<String,Object> fields){long v=number(before,"version");require(v<MAX,"expectedVersion","VERSION_EXHAUSTED");var after=new LinkedHashMap<>(before);after.putAll(fields);after.put("version",v+1);return after;}
        void expected(Map<String,Object> target,boolean mandatory){Long supplied=requestedVersion(sourceRow,mandatory);require(supplied==null||supplied==number(target,"version"),"expectedVersion","VERSION_CONFLICT");}
        ImportPlan run(List<ImportFileParser.Row> rows){
            require(rows!=null&&rows.size()<=1001,"file","ROW_LIMIT_EXCEEDED");
            if(rows==null||rows.isEmpty()){report(1,"file","EMPTY_INPUT");return result();}
            var columns=new HashMap<String,Integer>();var ambiguous=new HashSet<String>();
            rows.getFirst().cells().forEach((n,v)->{String name=v.strip();if(columns.put(name,n)!=null)ambiguous.add(name);});
            for(var m:input.fieldMappings()){if(!columns.containsKey(m.sourceColumn()))report(rows.getFirst().number(),m.targetField(),"MISSING_COLUMN");if(ambiguous.contains(m.sourceColumn()))report(rows.getFirst().number(),m.targetField(),"AMBIGUOUS_HEADER");}
            if(!issues.isEmpty())return result();if(rows.size()==1){report(rows.getFirst().number(),"file","EMPTY_INPUT");return result();}
            for(var row:rows.subList(1,rows.size())){
                rowNumber=row.number();pendingMappings.clear();var mapped=new TreeMap<String,String>();for(var m:input.fieldMappings())mapped.put(m.targetField(),row.cells().getOrDefault(columns.get(m.sourceColumn()),"").strip());sourceRow=mapped;
                try{
                    for(var v:mapped.entrySet())require(v.getValue().codePointCount(0,v.getValue().length())<=100,v.getKey(),"VALUE_TOO_LONG");
                    if(input.importType().equals("EMPLOYEE_INITIALIZATION")){String identity=mapped.getOrDefault("employeeId","")+":"+mapped.getOrDefault("employeeKey","");String name=mapped.getOrDefault("name","");String previous=employeeNames.putIfAbsent(identity,name);require(previous==null||previous.equals(name),"name","EMPLOYEE_ATTRIBUTE_CONFLICT");}
                    if(!seen.add(Map.copyOf(mapped))){warning("DUPLICATE_ROW");unchanged("input",null,null,null,null,null);continue;}
                    switch(input.importType()){
                        case "ORGANIZATION"->organization();
                        case "EMPLOYEE_INITIALIZATION"->initialization();
                        case "PERSONNEL_CHANGES"->personnel();
                        default->throw new Problem("importType","INVALID_ACTION");
                    }
                }catch(Problem p){report(rowNumber,p.field,p.code);}
            }
            return result();
        }
        ImportPlan result(){long created=0,updated=0,unchanged=0,ends=0,disabled=0,restored=0;for(var c:changes){String a=c.action();if(a.startsWith("CREATE_")||a.equals("ADD_EMPLOYMENT"))created++;else if(a.startsWith("RENAME_")||a.equals("MOVE_DEPARTMENT")||a.equals("REMAP_EMPLOYEE_KEY"))updated++;else switch(a){case "UNCHANGED"->unchanged++;case "END_EMPLOYMENT"->ends++;case "DISABLE_EMPLOYEE"->disabled++;case "RESTORE_EMPLOYEE"->restored++;default->{}}}
            return new ImportPlan(List.copyOf(changes),List.copyOf(issues),new Counts(created,updated,unchanged,ends,disabled,restored,ended.size(),revoked.size()),new Effects(Set.copyOf(closed),Set.copyOf(ended),Set.copyOf(inactive),Set.copyOf(revoked)));
        }
        void report(int row,String field,String code){issues.add(new Issue(row,field,"BLOCKING",code,"预检未通过："+code));}
        void warning(String code){issues.add(new Issue(rowNumber,"row","WARNING",code,"预检提示："+code));}
        void emit(String action,String table,Map<String,Object> before,Map<String,Object> after,String stable,String c,String d,String p,List<ValueChange> fields,Map<String,Object> extra){
            String id=after==null?null:text(after,"id");var normalized=values("schemaVersion",1,"entity",table,"before",before==null?Map.of():new TreeMap<>(before),"after",after==null?Map.of():new TreeMap<>(after),"source",new TreeMap<>(sourceRow),"mappingWrites",List.copyOf(pendingMappings));normalized.put("newObject",id!=null&&candidates.contains(id));normalized.putAll(extra);pendingMappings.clear();
            var deps=values("schemaVersion",1,"targets",before==null?List.of():List.of(values("entity",table,"id",id,"version",number(before,"version"),"provisional",candidates.contains(id))),"absent",before==null&&after!=null?List.of(values("entity",table,"id",id,"stableCode",stable,"companyCode",c,"departmentCode",d,"positionCode",p,"employeeKey",after.get("employee_key"),"employeeId",after.get("employee_id"))):List.of());
            changes.add(new Change(rowNumber,ordinals.merge(rowNumber,1,Integer::sum)-1,action,id,stable,before==null?null:number(before,"version"),"预检计划："+action,c,d,p,List.copyOf(fields),normalized,deps,id!=null&&candidates.contains(id)));
        }
        void unchanged(String table,Map<String,Object> target,String code,String c,String d,String p){emit("UNCHANGED",table,target,target,code,c,d,p,List.of(),Map.of());}
        String code(Map<String,Object> r){return r==null?null:text(r,"code");}
        Map<String,Object> sourceMapping(String path){return all("organization_mapping").stream().filter(m->text(m,"source_system").equals(input.sourceSystem())&&text(m,"source_path").equals(path)).findFirst().orElse(null);}
        OrganizationMapping explicit(String path){return input.organizationMappings().stream().filter(m->m.sourcePath().equals(path)).findFirst().orElse(null);}
        String part(String nameField,String codeField){String name=sourceRow.getOrDefault(nameField,"");return name.isEmpty()?sourceRow.getOrDefault(codeField,""):name;}
        void mapping(String table,Map<String,Object> desired){
            Map<String,Object> old=table.equals("organization_mapping")?sourceMapping(text(desired,"source_path")):all(table).stream().filter(m->text(m,"source_system").equals(input.sourceSystem())&&text(m,"source_key").equals(text(desired,"source_key"))).findFirst().orElse(null);
            if(old!=null){require(desired.entrySet().stream().allMatch(e->Objects.equals(old.get(e.getKey()),e.getValue())),"organizationMappings","IDENTITY_CONFLICT");return;}
            var next=created();next.putAll(desired);put(table,next);pendingMappings.add(values("entity",table,"id",text(next,"id"),"expectedVersion",0,"after",new TreeMap<>(next)));
        }
        Map<String,Object> org(String type,Map<String,Object> company,Map<String,Object> department,boolean mayCreate,String forcedAction){
            String field=type+"Code",nameField=type+"Name",rawCode=sourceRow.getOrDefault(field,""),name=sourceRow.getOrDefault(nameField,"");String parentField=type.equals("department")?"company_id":type.equals("position")?"department_id":null;String parent=type.equals("department")?text(company,"id"):type.equals("position")?text(department,"id"):null;
            String[] parts=type.equals("company")?new String[]{name.isEmpty()?rawCode:name}:type.equals("department")?new String[]{part("companyName","companyCode"),name.isEmpty()?rawCode:name}:new String[]{part("companyName","companyCode"),part("departmentName","departmentCode"),name.isEmpty()?rawCode:name};
            boolean ambiguous=Arrays.stream(parts).anyMatch(part->part.contains("/"));String fullPath=String.join("/",parts);require(!ambiguous||!rawCode.isEmpty()||explicit(fullPath)!=null,"organizationMappings","AMBIGUOUS_ORGANIZATION_PATH");
            Map<String,Object> existing=rawCode.isEmpty()?null:byCode(type,rawCode,parentField,parent);OrganizationMapping explicit=explicit(fullPath);Map<String,Object> mapped=sourceMapping(fullPath);
            String selectedId=explicit==null?null:type.equals("company")?explicit.companyId():type.equals("department")?explicit.departmentId():null;
            String selectedCode=explicit==null?null:type.equals("company")?explicit.companyCode():type.equals("department")?explicit.departmentCode():null;
            if(selectedId!=null){var selected=get(type,selectedId);require(selected!=null,field,"UNKNOWN_ORGANIZATION");require(existing==null||text(existing,"id").equals(selectedId),field,"INVALID_OWNERSHIP");existing=selected;}
            if(selectedCode!=null){var selected=byCode(type,selectedCode,parentField,parent);require(selected!=null||mayCreate&&selectedId==null,field,"UNKNOWN_ORGANIZATION");require(rawCode.isEmpty()||rawCode.equals(selectedCode),field,"INVALID_OWNERSHIP");if(selected!=null){require(existing==null||text(existing,"id").equals(text(selected,"id")),field,"INVALID_OWNERSHIP");existing=selected;}else rawCode=selectedCode;}
            if(existing==null&&mapped!=null){require(text(mapped,"object_kind").equals(type.toUpperCase(Locale.ROOT)),field,"INVALID_OWNERSHIP");existing=get(type,text(mapped,type.equals("company")?"company_id":type.equals("department")?"department_id":"position_id"));require(existing!=null,field,"UNKNOWN_ORGANIZATION");}
            if(existing!=null&&parentField!=null)require(text(existing,parentField).equals(parent),field,"INVALID_OWNERSHIP");
            if(existing!=null&&!rawCode.isEmpty())require(rawCode.equals(code(existing)),field,"INVALID_OWNERSHIP");
            if(explicit!=null){
                var owner=type.equals("company")?existing:company;
                if(owner!=null)require((explicit.companyId()==null||explicit.companyId().equals(text(owner,"id")))&&(explicit.companyCode()==null||explicit.companyCode().equals(code(owner))),field,"INVALID_OWNERSHIP");
                if(type.equals("company"))require(explicit.departmentId()==null&&explicit.departmentCode()==null,field,"INVALID_OWNERSHIP");
                else{var ownedDepartment=type.equals("department")?existing:department;if(ownedDepartment!=null)require((explicit.departmentId()==null||explicit.departmentId().equals(text(ownedDepartment,"id")))&&(explicit.departmentCode()==null||explicit.departmentCode().equals(code(ownedDepartment))),field,"INVALID_OWNERSHIP");}
            }
            boolean create=existing==null;require(!create||mayCreate,field,"UNKNOWN_ORGANIZATION");if(create){require(!name.isEmpty(),nameField,"MISSING_VALUE");String generated=rawCode.isEmpty()?type.substring(0,1).toUpperCase(Locale.ROOT)+"_"+fresh().replace("-",""):rawCode;require(generated.matches("[A-Za-z0-9][A-Za-z0-9_.:-]*"),field,"INVALID_CODE");existing=created("code",generated,"name",name);if(parentField!=null)existing.put(parentField,parent);put(type,existing);}
            String cc=type.equals("company")?code(existing):code(company),dc=type.equals("department")?code(existing):code(department),pc=type.equals("position")?code(existing):null;
            if(!name.isEmpty()&&(!ambiguous||explicit!=null)){mapping("organization_mapping",values("source_system",input.sourceSystem(),"source_path",fullPath,"object_kind",type.toUpperCase(Locale.ROOT),"company_id",type.equals("company")?text(existing,"id"):text(company,"id"),"department_id",type.equals("department")?text(existing,"id"):type.equals("position")?text(department,"id"):null,"position_id",type.equals("position")?text(existing,"id"):null));}
            String upper=type.toUpperCase(Locale.ROOT);if(create){require(forcedAction==null||!forcedAction.startsWith("RENAME_"),field,"UNKNOWN_ORGANIZATION");emit("CREATE_"+upper,type,null,existing,code(existing),cc,dc,pc,List.of(new ValueChange("name",null,name)),Map.of());}
            else if(!name.isEmpty()&&!name.equals(text(existing,"name"))){require(forcedAction==null||!forcedAction.startsWith("CREATE_"),field,"STATE_CONFLICT");expected(existing,false);var after=updated(existing,values("name",name));put(type,after);emit("RENAME_"+upper,type,existing,after,code(after),cc,dc,pc,List.of(new ValueChange("name",existing.get("name"),name)),Map.of());existing=after;}
            else if(mayCreate)unchanged(type,existing,code(existing),cc,dc,pc);
            return existing;
        }
        void organization(){String action=sourceRow.getOrDefault("action","");if(action.equals("MOVE_DEPARTMENT")){move();return;}
            require(action.isEmpty()||Set.of("CREATE_COMPANY","RENAME_COMPANY","CREATE_DEPARTMENT","RENAME_DEPARTMENT","CREATE_POSITION","RENAME_POSITION").contains(action),"action","INVALID_ACTION");
            if(action.endsWith("COMPANY")){org("company",null,null,true,action);return;}
            var c=org("company",null,null,action.isEmpty(),null);boolean hasDepartment=!sourceRow.getOrDefault("departmentName","").isEmpty()||!sourceRow.getOrDefault("departmentCode","").isEmpty();if(!hasDepartment){require(action.isEmpty(),"departmentCode","MISSING_VALUE");return;}
            var d=org("department",c,null,true,action.endsWith("DEPARTMENT")?action:null);if(action.endsWith("DEPARTMENT"))return;
            if(!sourceRow.getOrDefault("positionName","").isEmpty()||!sourceRow.getOrDefault("positionCode","").isEmpty())org("position",c,d,true,action.isEmpty()?null:action);else require(action.isEmpty(),"positionCode","MISSING_VALUE");
        }
        Map<String,Object> employee(boolean create,boolean rename){
            String key=sourceRow.getOrDefault("employeeKey",""),identifier=sourceRow.getOrDefault("employeeId","");var employee=identifier.isEmpty()?null:get("employee",identifier);require(identifier.isEmpty()||employee!=null,"employeeId","UNKNOWN_EMPLOYEE");
            var identity=all("employee_source_identity").stream().filter(m->text(m,"source_system").equals(input.sourceSystem())&&text(m,"source_key").equals(key)).findFirst().orElse(null);
            if(identity!=null){var mapped=get("employee",text(identity,"employee_id"));require(mapped!=null&&(employee==null||text(employee,"id").equals(text(mapped,"id"))),"employeeKey","IDENTITY_CONFLICT");employee=mapped;}
            if(employee==null&&!key.isEmpty())employee=find("employee","employee_key",key);
            require(employee!=null||create,"employeeId","UNKNOWN_EMPLOYEE");String name=sourceRow.getOrDefault("name","");
            if(employee!=null&&input.importType().equals("EMPLOYEE_INITIALIZATION")){String previous=resolvedNames.putIfAbsent(text(employee,"id"),name);require(previous==null||previous.equals(name),"name","EMPLOYEE_ATTRIBUTE_CONFLICT");}
            if(employee==null){require(!key.isEmpty(),"employeeKey","MISSING_VALUE");require(!name.isEmpty(),"name","MISSING_VALUE");employee=created("employee_key",key,"name",name,"status","ACTIVE");put("employee",employee);if(input.importType().equals("EMPLOYEE_INITIALIZATION"))resolvedNames.put(text(employee,"id"),name);mapping("employee_source_identity",values("source_system",input.sourceSystem(),"source_key",key,"employee_id",text(employee,"id")));emit("CREATE_EMPLOYEE","employee",null,employee,key,null,null,null,List.of(new ValueChange("employeeKey",null,key),new ValueChange("name",null,name),new ValueChange("status",null,"ACTIVE")),Map.of());return employee;}
            if(!key.isEmpty()){require(identity!=null||key.equals(text(employee,"employee_key")),"employeeKey","IDENTITY_CONFLICT");mapping("employee_source_identity",values("source_system",input.sourceSystem(),"source_key",key,"employee_id",text(employee,"id")));}
            if(rename&&!name.isEmpty()&&!name.equals(text(employee,"name"))){expected(employee,false);var after=updated(employee,values("name",name));put("employee",after);emit("RENAME_EMPLOYEE","employee",employee,after,text(after,"employee_key"),null,null,null,List.of(new ValueChange("name",employee.get("name"),name)),Map.of());employee=after;}
            return employee;
        }
        void initialization(){
            String state=input.initialEmploymentState().equals("EXPLICIT_ACTIVE")?"ACTIVE":sourceRow.getOrDefault("employmentState","");require(!state.isEmpty(),"employmentState","MISSING_EMPLOYMENT_STATE");require(Set.of("ACTIVE","ENDED").contains(state),"employmentState","INVALID_EMPLOYMENT_STATE");require(!input.initialEmploymentState().equals("EXPLICIT_ACTIVE")||sourceRow.getOrDefault("employmentState","ACTIVE").isEmpty()||sourceRow.getOrDefault("employmentState","ACTIVE").equals("ACTIVE"),"employmentState","INITIAL_STATE_CONFLICT");
            var e=employee(true,true);add(e,true,state);
        }
        void add(Map<String,Object> employee,boolean initialization,String status){
            var company=org("company",null,null,false,null);var department=org("department",company,null,false,null);var position=org("position",company,department,false,null);String e=text(employee,"id"),c=text(company,"id"),d=text(department,"id"),p=text(position,"id");
            String specified=sourceRow.getOrDefault("employmentId","");require(specified.isEmpty()||get("employment",specified)==null,"employmentId","IDENTITY_CONFLICT");
            var matching=all("employment").stream().filter(j->text(j,"employee_id").equals(e)&&text(j,"company_id").equals(c)&&text(j,"department_id").equals(d)&&text(j,"position_id").equals(p)).toList();var active=matching.stream().filter(j->text(j,"status").equals("ACTIVE")).findFirst();
            if(active.isPresent()){unchanged("employment",active.get(),null,code(company),code(department),code(position));return;}
            if(initialization&&!matching.isEmpty()){warning("ENDED_EMPLOYMENT_PRESERVED");unchanged("employment",matching.getFirst(),null,code(company),code(department),code(position));return;}
            String started=date(sourceRow,"startedAt"),end=date(sourceRow,"endedAt");require(started==null||end==null||!Instant.parse(end).isBefore(Instant.parse(started)),"endedAt","INVALID_DATE");require(!status.equals("ACTIVE")||end==null,"endedAt","INVALID_EMPLOYMENT_STATE");
            var job=created("employee_id",e,"company_id",c,"department_id",d,"position_id",p,"status",status,"started_at",started,"ended_at",end);if(!specified.isEmpty()){candidates.remove(text(job,"id"));job.put("id",specified);candidates.add(specified);}put("employment",job);emit("ADD_EMPLOYMENT","employment",null,job,null,code(company),code(department),code(position),List.of(new ValueChange("employmentState",null,status),new ValueChange("startedAt",null,started)),Map.of("initialEmployment",initialization,"stampRecordedEndAt",status.equals("ENDED")));
        }
        void personnel(){String action=required(sourceRow,"action");switch(action){
            case "CREATE_EMPLOYEE"->{require(sourceRow.getOrDefault("employeeId","").isEmpty(),"employeeId","IDENTITY_CONFLICT");String key=required(sourceRow,"employeeKey");require(find("employee","employee_key",key)==null&&all("employee_source_identity").stream().noneMatch(m->text(m,"source_system").equals(input.sourceSystem())&&text(m,"source_key").equals(key)),"employeeKey","IDENTITY_CONFLICT");employee(true,false);}
            case "RENAME_EMPLOYEE"->{var e=employee(false,false);expected(e,true);String name=required(sourceRow,"name");if(name.equals(text(e,"name")))unchanged("employee",e,text(e,"employee_key"),null,null,null);else{var after=updated(e,values("name",name));put("employee",after);emit(action,"employee",e,after,text(e,"employee_key"),null,null,null,List.of(new ValueChange("name",e.get("name"),name)),Map.of());}}
            case "REMAP_EMPLOYEE_KEY"->remap();
            case "ADD_EMPLOYMENT"->add(employee(false,false),false,"ACTIVE");
            case "END_EMPLOYMENT"->endEmployment();
            case "DISABLE_EMPLOYEE","RESTORE_EMPLOYEE"->state(action);
            default->throw new Problem("action","INVALID_ACTION");
        }}
        void remap(){String previous=required(sourceRow,"previousEmployeeKey"),next=required(sourceRow,"employeeKey");var old=all("employee_source_identity").stream().filter(m->text(m,"source_system").equals(input.sourceSystem())&&text(m,"source_key").equals(previous)).findFirst().orElse(null);require(old!=null,"previousEmployeeKey","IDENTITY_CONFLICT");var e=get("employee",text(old,"employee_id"));require(e!=null&&(sourceRow.getOrDefault("employeeId","").isEmpty()||text(e,"id").equals(sourceRow.get("employeeId"))),"employeeId","IDENTITY_CONFLICT");expected(e,true);
            var collision=find("employee","employee_key",next);require(collision==null||text(collision,"id").equals(text(e,"id")),"employeeKey","IDENTITY_CONFLICT");require(all("employee_source_identity").stream().noneMatch(m->text(m,"source_system").equals(input.sourceSystem())&&text(m,"source_key").equals(next)&&!text(m,"id").equals(text(old,"id"))),"employeeKey","IDENTITY_CONFLICT");
            if(previous.equals(next)){unchanged("employee",e,text(e,"employee_key"),null,null,null);return;}
            var mapping=updated(old,values("source_key",next));put("employee_source_identity",mapping);pendingMappings.add(values("entity","employee_source_identity","id",text(old,"id"),"expectedVersion",number(old,"version"),"after",new TreeMap<>(mapping)));var after=updated(e,values("employee_key",next));put("employee",after);emit("REMAP_EMPLOYEE_KEY","employee",e,after,next,null,null,null,List.of(new ValueChange("employeeKey",previous,next)),Map.of());
        }
        void endEmployment(){String id=required(sourceRow,"employmentId");var job=get("employment",id);require(job!=null,"employmentId","UNKNOWN_EMPLOYMENT");expected(job,true);require(text(job,"status").equals("ACTIVE"),"employmentId","EMPLOYMENT_ENDED");
            String employee=sourceRow.getOrDefault("employeeId","");require(employee.isEmpty()||employee.equals(text(job,"employee_id")),"employeeId","IDENTITY_CONFLICT");if(!sourceRow.getOrDefault("employeeKey","").isEmpty())require(text(employee(false,false),"id").equals(text(job,"employee_id")),"employeeKey","IDENTITY_CONFLICT");String end=date(sourceRow,"endedAt");String start=text(job,"started_at");require(end==null||start.isEmpty()||!Instant.parse(end).isBefore(Instant.parse(start)),"endedAt","INVALID_DATE");
            var after=updated(job,values("status","ENDED","ended_at",end));put("employment",after);var effects=loss(text(job,"employee_id"),text(job,"company_id"),text(job,"department_id"));var company=get("company",text(job,"company_id"));var dep=get("department",text(job,"department_id"));var position=get("position",text(job,"position_id"));emit("END_EMPLOYMENT","employment",job,after,null,code(company),code(dep),code(position),List.of(new ValueChange("employmentState","ACTIVE","ENDED"),new ValueChange("endedAt",job.get("ended_at"),end)),effects);
        }
        void state(String action){var e=employee(false,false);expected(e,true);String desired=action.equals("DISABLE_EMPLOYEE")?"INACTIVE":"ACTIVE";if(desired.equals(text(e,"status"))){unchanged("employee",e,text(e,"employee_key"),null,null,null);return;}
            if(desired.equals("INACTIVE")){var targetAccounts=all("admin_account").stream().filter(a->text(a,"employee_id").equals(text(e,"id"))&&usableSuper(a)).toList();boolean self=targetAccounts.stream().anyMatch(a->text(a,"id").equals(actor));long remaining=all("admin_account").stream().filter(this::usableSuper).filter(a->!text(a,"employee_id").equals(text(e,"id"))).count();require(!self&&(targetAccounts.isEmpty()||remaining>0),"employeeId","ADMIN_SELF_LOCKOUT");}
            var after=updated(e,values("status",desired));put("employee",after);Map<String,Object> effects=Map.of();if(desired.equals("INACTIVE")){inactive.add(text(e,"id"));effects=loss(text(e,"id"),null,null);}emit(action,"employee",e,after,text(e,"employee_key"),null,null,null,List.of(new ValueChange("status",e.get("status"),desired)),effects);
        }
        boolean usableSuper(Map<String,Object> a){String eid=text(a,"employee_id");var e=get("employee",eid);return flag(a,"enabled")&&flag(a,"is_super_admin")&&!flag(a,"must_change_password")&&(eid.isEmpty()||e!=null&&text(e,"status").equals("ACTIVE"));}
        boolean job(String e,String c,String d){var employee=get("employee",e);return employee!=null&&text(employee,"status").equals("ACTIVE")&&all("employment").stream().anyMatch(j->text(j,"employee_id").equals(e)&&text(j,"company_id").equals(c)&&text(j,"department_id").equals(d)&&text(j,"status").equals("ACTIVE"));}
        Map<String,Object> loss(String employee,String lostCompany,String lostDepartment){
            var oldEnded=new HashSet<>(ended);var oldRevoked=new HashSet<>(revoked);var eventClosed=new ArrayList<ImpactPolicy.Source>();var sourceVersions=new ArrayList<Map<String,Object>>();
            for(var s:all("authorization_source"))if(text(s,"employee_id").equals(employee)&&(lostDepartment==null||text(s,"company_id").equals(lostCompany)&&text(s,"department_id").equals(lostDepartment))&&!job(employee,text(s,"company_id"),text(s,"department_id"))){
                var key=new ImpactPolicy.Source(employee,text(s,"company_id"),text(s,"department_id"),text(s,"system_id"));closed.add(key);eventClosed.add(key);
                require(number(s,"source_epoch")<MAX&&number(s,"source_version")<MAX&&number(s,"row_version")<MAX,"source","VERSION_EXHAUSTED");var after=new LinkedHashMap<>(s);after.put("source_epoch",number(s,"source_epoch")+1);after.put("source_version",number(s,"source_version")+1);after.put("row_version",number(s,"row_version")+1);put("authorization_source",after);sourceVersions.add(values("id",text(s,"id"),"beforeEpoch",number(s,"source_epoch"),"afterEpoch",number(after,"source_epoch"),"beforeVersion",number(s,"source_version"),"afterVersion",number(after,"source_version")));
            }
            for(var g:all("role_grant")){
                if(!text(g,"status").equals("ACTIVE"))continue;var b=get("grant_baseline",text(g,"baseline_id"));if(b==null)continue;var s=get("authorization_source",text(b,"source_id"));var r=get("business_role",text(b,"role_id"));if(s==null||r==null||!text(s,"employee_id").equals(employee))continue;String roleDept=text(r,"department_id");
                boolean relevant=lostDepartment==null||text(s,"company_id").equals(lostCompany)&&(text(s,"department_id").equals(lostDepartment)||roleDept.equals(lostDepartment));if(!relevant)continue;
                boolean qualified=job(employee,text(s,"company_id"),text(s,"department_id"))&&(roleDept.isEmpty()||job(employee,text(r,"company_id"),roleDept));
                if(!qualified&&ended.add(text(g,"id"))){require(number(b,"relation_version")<MAX&&number(b,"row_version")<MAX,"grant","VERSION_EXHAUSTED");var next=new LinkedHashMap<>(g);next.put("status","INVALIDATED");next.put("relation_version",number(b,"relation_version")+1);put("role_grant",next);var anchor=new LinkedHashMap<>(b);anchor.put("relation_version",number(b,"relation_version")+1);anchor.put("row_version",number(b,"row_version")+1);put("grant_baseline",anchor);}
            }
            for(var ap:all("admin_appointment")){
                if(!text(ap,"status").equals("ACTIVE"))continue;var account=get("admin_account",text(ap,"account_id"));var scope=get("management_scope",text(ap,"scope_id"));
                if(account!=null&&scope!=null&&text(account,"employee_id").equals(employee)&&(lostDepartment==null||text(scope,"company_id").equals(lostCompany)&&text(scope,"department_id").equals(lostDepartment))&&!job(employee,text(scope,"company_id"),text(scope,"department_id"))){
                    require(number(ap,"appointment_version")<MAX&&number(ap,"row_version")<MAX,"appointment","VERSION_EXHAUSTED");revoked.add(text(ap,"id"));var next=new LinkedHashMap<>(ap);next.put("status","INVALIDATED");next.put("appointment_version",number(ap,"appointment_version")+1);next.put("row_version",number(ap,"row_version")+1);put("admin_appointment",next);
                }
            }
            return values("lifecycle",values("closedSources",List.copyOf(eventClosed),"sourceBaselines",sourceVersions,"endedGrantIds",ended.stream().filter(s->!oldEnded.contains(s)).sorted().toList(),"invalidatedAppointmentIds",revoked.stream().filter(s->!oldRevoked.contains(s)).sorted().toList()));
        }
        boolean referenced(String department){
            if(all("departmentReferences").stream().anyMatch(r->text(r,"id").equals(department)&&flag(r,"referenced")))return true;
            for(String table:List.of("employment","management_scope","business_role","authorization_source","change_target"))if(all(table).stream().anyMatch(r->text(r,"department_id").equals(department)))return true;
            return false;
        }
        void move(){String dc=required(sourceRow,"departmentCode"),target=required(sourceRow,"targetCompanyCode");String sourceCompany=sourceRow.getOrDefault("companyCode","");var parent=sourceCompany.isEmpty()?null:byCode("company",sourceCompany,null,null);require(sourceCompany.isEmpty()||parent!=null,"companyCode","UNKNOWN_ORGANIZATION");var found=all("department").stream().filter(d->text(d,"code").equals(dc)&&(parent==null||text(d,"company_id").equals(text(parent,"id")))).toList();require(found.size()==1,"departmentCode","AMBIGUOUS_ORGANIZATION");var dep=found.getFirst();expected(dep,true);var company=byCode("company",target,null,null);require(company!=null,"targetCompanyCode","UNKNOWN_ORGANIZATION");String id=text(dep,"id");if(text(dep,"company_id").equals(text(company,"id"))){unchanged("department",dep,dc,target,dc,null);return;}
            require(!referenced(id),"departmentCode","DEPARTMENT_REFERENCED");require(byCode("department",dc,"company_id",text(company,"id"))==null,"departmentCode","IDENTITY_CONFLICT");
            for(var mapping:all("organization_mapping"))if(text(mapping,"department_id").equals(id)){
                String suffix=text(mapping,"object_kind").equals("POSITION")?"/"+text(get("position",text(mapping,"position_id")),"name"):"";String newPath=text(company,"name")+"/"+text(dep,"name")+suffix;
                boolean explicit=input.organizationMappings().stream().anyMatch(m->m.sourcePath().equals(newPath)&&(text(company,"id").equals(m.companyId())||target.equals(m.companyCode()))&&(id.equals(m.departmentId())||dc.equals(m.departmentCode())));require(explicit,"organizationMappings","MAPPING_REQUIRED");var collision=sourceMapping(newPath);require(collision==null||text(collision,"id").equals(text(mapping,"id")),"organizationMappings","IDENTITY_CONFLICT");
                var after=updated(mapping,values("company_id",text(company,"id"),"source_path",newPath));put("organization_mapping",after);pendingMappings.add(values("entity","organization_mapping","id",text(mapping,"id"),"expectedVersion",number(mapping,"version"),"after",new TreeMap<>(after)));
            }
            var beforeCompany=get("company",text(dep,"company_id"));var after=updated(dep,values("company_id",text(company,"id")));put("department",after);emit("MOVE_DEPARTMENT","department",dep,after,dc,target,dc,null,List.of(new ValueChange("companyCode",code(beforeCompany),target)),Map.of("departmentReferenceCheck",true));
        }
    }
}
