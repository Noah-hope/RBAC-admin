package com.adgn.importfixture;

import com.adgn.backend.imports.*;
import com.adgn.backend.query.preview.ImpactPolicy;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.adgn.backend.imports.ImportPlan.*;
import static org.assertj.core.api.Assertions.*;

class ImportPlannerTest {
    private final ImportPlanner planner=new ImportPlanner();
    private static final String C=id(1),D=id(2),P=id(3),E=id(4),JOB=id(5),SRC=id(6),ROLE=id(7),B=id(8),G=id(9),A=id(10),SCOPE=id(11),SYS=id(12),AP=id(13);
    private static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    private static Map<String,Object> row(Object...args){var m=new LinkedHashMap<String,Object>();for(int n=0;n<args.length;n+=2)m.put((String)args[n],args[n+1]);return m;}
    private static Map<String,List<Map<String,Object>>> data(){
        var s=new HashMap<String,List<Map<String,Object>>>();
        s.put("company",List.of(row("id",C,"code","C","name","Company","version",1L)));
        s.put("department",List.of(row("id",D,"company_id",C,"code","D","name","Department","version",1L)));
        s.put("position",List.of(row("id",P,"department_id",D,"code","P","name","Position","version",1L)));
        s.put("employee",List.of(row("id",E,"employee_key","alice","name","Alice","status","ACTIVE","version",1L)));
        s.put("employee_source_identity",List.of(row("id",id(14),"source_system","HR","source_key","alice","employee_id",E,"version",1L)));
        s.put("employment",List.of(row("id",JOB,"employee_id",E,"company_id",C,"department_id",D,"position_id",P,"status","ACTIVE","started_at",null,"ended_at",null,"version",1L)));
        return s;
    }
    private Input input(String type,String...fields){return new Input("file1","HR","batch1",type,"CSV",Arrays.stream(fields).map(f->new FieldMapping(f,f)).toList(),List.of(),"EXPLICIT_ACTIVE","Fixture import");}
    private List<ImportFileParser.Row> sheet(String[][] values){var rows=new ArrayList<ImportFileParser.Row>();for(int n=0;n<values.length;n++){var cells=new LinkedHashMap<Integer,String>();for(int k=0;k<values[n].length;k++)cells.put(k+1,values[n][k]);rows.add(new ImportFileParser.Row(n+1,cells));}return rows;}
    private ImportPlan plan(Input input,String[][] cells,Map<String,List<Map<String,Object>>> s){return planner.plan(input,sheet(cells),s,A);}
    private List<String> actions(ImportPlan p){return p.changes().stream().map(Change::action).toList();}
    private void issue(ImportPlan p,String code){assertThat(p.issues()).extracting(Issue::code).contains(code);}
    @Test void allocatesStableCandidatesForMissingOrganizationCodesWithoutCreatingFormalRows(){
        var snapshot=new HashMap<String,List<Map<String,Object>>>();var p=plan(input("ORGANIZATION","companyName","departmentName","positionName"),new String[][]{{"companyName","departmentName","positionName"},{"First","Shared","Analyst"},{"Second","Shared","Analyst"}},snapshot);
        assertThat(actions(p)).containsExactly("CREATE_COMPANY","CREATE_DEPARTMENT","CREATE_POSITION","CREATE_COMPANY","CREATE_DEPARTMENT","CREATE_POSITION");assertThat(p.counts().created()).isEqualTo(6);assertThat(p.issues()).isEmpty();assertThat(snapshot).isEmpty();
        assertThat(p.changes()).allSatisfy(c->{assertThat(c.targetId()).isNotNull();assertThat(c.newObject()).isTrue();assertThat(c.expectedVersion()).isNull();assertThat(c.stableCode()).matches("[CDP]_[a-f0-9]{32}");assertThat(c.normalizedValues()).containsKeys("schemaVersion","entity","after","mappingWrites");assertThat(c.dependencies()).containsKey("absent");});
    }
    @Test void reusesExistingStableCodesForRenameAndFreezesBeforeAfterAndVersion(){
        var p=plan(input("ORGANIZATION","companyCode","companyName"),new String[][]{{"companyCode","companyName"},{"C","Renamed"}},data());assertThat(actions(p)).containsExactly("RENAME_COMPANY");var change=p.changes().getFirst();assertThat(change.targetId()).isEqualTo(C);assertThat(change.expectedVersion()).isEqualTo(1);assertThat(change.fieldChanges()).containsExactly(new ValueChange("name","Company","Renamed"));assertThat(p.counts().updated()).isEqualTo(1);
    }
    @Test void sameNamesNeverMergeEmployeesAndMultipleJobsKeepOneStableEmployee(){
        var p=plan(input("EMPLOYEE_INITIALIZATION","employeeKey","name","companyCode","departmentCode","positionCode"),new String[][]{{"employeeKey","name","companyCode","departmentCode","positionCode"},{"bob","Same name","C","D","P"},{"carol","Same name","C","D","P"}},data());assertThat(actions(p)).containsExactly("CREATE_EMPLOYEE","ADD_EMPLOYMENT","CREATE_EMPLOYEE","ADD_EMPLOYMENT");assertThat(p.counts().created()).isEqualTo(4);assertThat(p.issues()).isEmpty();assertThat(p.changes().get(0).targetId()).isNotEqualTo(p.changes().get(2).targetId());
    }
    @Test void duplicateInitializationWarnsButDoesNotCreateOrEndAnything(){
        var p=plan(input("EMPLOYEE_INITIALIZATION","employeeKey","name","companyCode","departmentCode","positionCode"),new String[][]{{"employeeKey","name","companyCode","departmentCode","positionCode"},{"alice","Alice","C","D","P"},{"alice","Alice","C","D","P"}},data());assertThat(p.counts().created()).isZero();assertThat(p.counts().endedEmployments()).isZero();assertThat(actions(p)).containsOnly("UNCHANGED");issue(p,"DUPLICATE_ROW");assertThat(p.counts().unchanged()).isEqualTo(2);
    }
    @Test void conflictingEmployeeAttributesBlockTheBatchInsteadOfLastRowWinning(){
        var p=plan(input("EMPLOYEE_INITIALIZATION","employeeKey","name","companyCode","departmentCode","positionCode"),new String[][]{{"employeeKey","name","companyCode","departmentCode","positionCode"},{"bob","Bob","C","D","P"},{"bob","Other","C","D","P"}},data());issue(p,"EMPLOYEE_ATTRIBUTE_CONFLICT");assertThat(p.issues()).anySatisfy(i->assertThat(i.severity()).isEqualTo("BLOCKING"));
    }
    @Test void initializationNeverResurrectsInactiveEmployeeOrEndedEmployment(){
        var s=data();s.get("employee").getFirst().put("status","INACTIVE");s.get("employment").getFirst().put("status","ENDED");
        var p=plan(input("EMPLOYEE_INITIALIZATION","employeeKey","name","companyCode","departmentCode","positionCode"),new String[][]{{"employeeKey","name","companyCode","departmentCode","positionCode"},{"alice","Alice","C","D","P"}},s);assertThat(actions(p)).containsOnly("UNCHANGED");issue(p,"ENDED_EMPLOYMENT_PRESERVED");assertThat(p.counts().restoredEmployees()).isZero();assertThat(p.counts().created()).isZero();
    }
    @Test void missingHeadersAndAmbiguousOrganizationOrUnknownActionsAreBlockingReports(){
        issue(plan(input("EMPLOYEE_INITIALIZATION","employeeKey","name"),new String[][]{{"wrong","name"},{"alice","Alice"}},data()),"MISSING_COLUMN");
        issue(plan(input("ORGANIZATION","companyName"),new String[][]{{"companyName"},{"Ambiguous/Path"}},Map.of()),"AMBIGUOUS_ORGANIZATION_PATH");
        issue(plan(input("PERSONNEL_CHANGES","action"),new String[][]{{"action"},{"DELETE_ALL"}},data()),"INVALID_ACTION");
    }
    @Test void fromSourceRequiresExplicitInitialStateAndKeepsUnknownDatesNull(){
        Input base=input("EMPLOYEE_INITIALIZATION","employeeKey","name","companyCode","departmentCode","positionCode");var source=new Input(base.fileId(),base.sourceSystem(),base.sourceBatchId(),base.importType(),base.worksheet(),base.fieldMappings(),base.organizationMappings(),"FROM_SOURCE",base.reason());
        issue(plan(source,new String[][]{{"employeeKey","name","companyCode","departmentCode","positionCode"},{"bob","Bob","C","D","P"}},data()),"MISSING_EMPLOYMENT_STATE");
        var p=plan(base,new String[][]{{"employeeKey","name","companyCode","departmentCode","positionCode"},{"bob","Bob","C","D","P"}},data());assertThat(actions(p)).contains("ADD_EMPLOYMENT");var after=(Map<?,?>)p.changes().getLast().normalizedValues().get("after");assertThat(after.containsKey("started_at")).isTrue();assertThat(after.get("started_at")).isNull();
    }
    private void authorization(Map<String,List<Map<String,Object>>> s){
        s.put("authorization_source",List.of(row("id",SRC,"employee_id",E,"company_id",C,"department_id",D,"system_id",SYS,"source_epoch",0L,"source_version",0L)));
        s.put("business_role",List.of(row("id",ROLE,"company_id",C,"department_id",D,"system_id",SYS,"configuration_version",1L,"state","EFFECTIVE")));
        s.put("grant_baseline",List.of(row("id",B,"source_id",SRC,"role_id",ROLE,"company_id",C,"system_id",SYS,"relation_version",1L)));
        s.put("role_grant",List.of(row("id",G,"baseline_id",B,"status","ACTIVE","relation_version",1L,"source_epoch",0L)));
        s.put("admin_account",List.of(row("id",A,"employee_id",E,"enabled",true,"is_super_admin",false,"must_change_password",false)));
        s.put("management_scope",List.of(row("id",SCOPE,"company_id",C,"department_id",D,"scope_version",1L)));
        s.put("admin_appointment",List.of(row("id",AP,"account_id",A,"scope_id",SCOPE,"status","ACTIVE","appointment_version",1L)));
    }
    @Test void endingLastJobThenAddingOneKeepsOrderedPersistentLossEffects(){
        var s=data();authorization(s);var p=plan(input("PERSONNEL_CHANGES","action","employeeId","employmentId","expectedVersion","companyCode","departmentCode","positionCode"),new String[][]{{"action","employeeId","employmentId","expectedVersion","companyCode","departmentCode","positionCode"},{"END_EMPLOYMENT",E,JOB,"1","","",""},{"ADD_EMPLOYMENT",E,"","","C","D","P"}},s);
        assertThat(actions(p)).containsExactly("END_EMPLOYMENT","ADD_EMPLOYMENT");assertThat(p.counts().endedEmployments()).isEqualTo(1);assertThat(p.counts().invalidatedGrants()).isEqualTo(1);assertThat(p.counts().invalidatedAppointments()).isEqualTo(1);assertThat(p.effects().closedSources()).containsExactly(new ImpactPolicy.Source(E,C,D,SYS));assertThat(p.effects().endedGrants()).containsExactly(G);assertThat(s.get("employment").getFirst().get("status")).isEqualTo("ACTIVE");
    }
    @Test void anotherApplicableJobPreventsWholeSourceAndAppointmentLoss(){
        var s=data();authorization(s);s.put("employment",List.of(s.get("employment").getFirst(),row("id",id(25),"employee_id",E,"company_id",C,"department_id",D,"position_id",id(26),"status","ACTIVE","version",1L)));
        var p=plan(input("PERSONNEL_CHANGES","action","employmentId","expectedVersion"),new String[][]{{"action","employmentId","expectedVersion"},{"END_EMPLOYMENT",JOB,"1"}},s);assertThat(actions(p)).containsExactly("END_EMPLOYMENT");assertThat(p.effects().closedSources()).isEmpty();assertThat(p.counts().invalidatedGrants()).isZero();assertThat(p.counts().invalidatedAppointments()).isZero();
    }
    @Test void exactEndedEmploymentAndWrongVersionCannotBeSilentlyReplaced(){
        var p=plan(input("PERSONNEL_CHANGES","action","employmentId","expectedVersion"),new String[][]{{"action","employmentId","expectedVersion"},{"END_EMPLOYMENT",JOB,"2"}},data());issue(p,"VERSION_CONFLICT");assertThat(p.counts().endedEmployments()).isZero();
    }
    @Test void sourceKeyRemapNeedsExplicitOldKeyAndNeverClaimsAnotherEmployee(){
        var p=plan(input("PERSONNEL_CHANGES","action","employeeId","employeeKey","previousEmployeeKey","expectedVersion"),new String[][]{{"action","employeeId","employeeKey","previousEmployeeKey","expectedVersion"},{"REMAP_EMPLOYEE_KEY",E,"alice-new","alice","1"}},data());assertThat(actions(p)).containsExactly("REMAP_EMPLOYEE_KEY");assertThat(p.changes().getFirst().fieldChanges()).contains(new ValueChange("employeeKey","alice","alice-new"));
        var bad=plan(input("PERSONNEL_CHANGES","action","employeeId","employeeKey","previousEmployeeKey","expectedVersion"),new String[][]{{"action","employeeId","employeeKey","previousEmployeeKey","expectedVersion"},{"REMAP_EMPLOYEE_KEY",E,"alice-new","someone-else","1"}},data());issue(bad,"IDENTITY_CONFLICT");
    }
    @Test void referencedDepartmentMoveIsBlockedAndEmptyDepartmentMoveKeepsIdentity(){
        var s=data();s.put("company",List.of(s.get("company").getFirst(),row("id",id(30),"code","C2","name","Company2","version",1L)));
        var in=input("ORGANIZATION","action","departmentCode","targetCompanyCode","expectedVersion");String[][] cells={{"action","departmentCode","targetCompanyCode","expectedVersion"},{"MOVE_DEPARTMENT","D","C2","1"}};
        issue(plan(in,cells,s),"DEPARTMENT_REFERENCED");s.put("employment",List.of());var p=plan(in,cells,s);assertThat(actions(p)).containsExactly("MOVE_DEPARTMENT");assertThat(p.changes().getFirst().targetId()).isEqualTo(D);assertThat(p.changes().getFirst().fieldChanges()).contains(new ValueChange("companyCode","C","C2"));
    }
    @Test void globalDisableProtectsCurrentAndLastUsableSuperAdmin(){
        var s=data();s.put("admin_account",List.of(row("id",A,"employee_id",E,"enabled",true,"is_super_admin",true,"must_change_password",false)));
        var in=input("PERSONNEL_CHANGES","action","employeeId","expectedVersion");issue(plan(in,new String[][]{{"action","employeeId","expectedVersion"},{"DISABLE_EMPLOYEE",E,"1"}},s),"ADMIN_SELF_LOCKOUT");
    }
    @Test void endingOneDepartmentDoesNotCloseAnAlreadyUnqualifiedOtherDepartment(){
        var s=data();authorization(s);var other=row("id",id(40),"employee_id",E,"company_id",C,"department_id",id(41),"system_id",SYS,"source_epoch",7L,"source_version",7L);s.put("authorization_source",List.of(s.get("authorization_source").getFirst(),other));
        var p=plan(input("PERSONNEL_CHANGES","action","employmentId","expectedVersion"),new String[][]{{"action","employmentId","expectedVersion"},{"END_EMPLOYMENT",JOB,"1"}},s);assertThat(p.effects().closedSources()).containsExactly(new ImpactPolicy.Source(E,C,D,SYS));
    }
    @Test void aMissingNameUsesTheExplicitParentCodeForTheFullSourcePath(){
        var p=plan(input("ORGANIZATION","companyCode","companyName","departmentName"),new String[][]{{"companyCode","companyName","departmentName"},{"C","","New"}},data());assertThat(actions(p)).contains("CREATE_DEPARTMENT");var dep=p.changes().stream().filter(c->c.action().equals("CREATE_DEPARTMENT")).findFirst().orElseThrow();assertThat(dep.normalizedValues().get("mappingWrites").toString()).contains("source_path=C/New");
    }
    @Test void sourceStorageVersionExhaustionBlocksThePlanBeforeItCanBeReady(){
        var s=data();authorization(s);s.get("authorization_source").getFirst().put("row_version",9007199254740991L);var p=plan(input("PERSONNEL_CHANGES","action","employmentId","expectedVersion"),new String[][]{{"action","employmentId","expectedVersion"},{"END_EMPLOYMENT",JOB,"1"}},s);issue(p,"VERSION_EXHAUSTED");
    }
    @Test void restorationInTheSameBatchNeverUndoesEarlierLossEvents(){
        var s=data();authorization(s);var p=plan(input("PERSONNEL_CHANGES","action","employeeId","expectedVersion"),new String[][]{{"action","employeeId","expectedVersion"},{"DISABLE_EMPLOYEE",E,"1"},{"RESTORE_EMPLOYEE",E,"2"}},s);assertThat(actions(p)).containsExactly("DISABLE_EMPLOYEE","RESTORE_EMPLOYEE");assertThat(p.counts().disabledEmployees()).isEqualTo(1);assertThat(p.counts().restoredEmployees()).isEqualTo(1);assertThat(p.counts().invalidatedGrants()).isEqualTo(1);assertThat(p.effects().endedGrants()).containsExactly(G);
    }
    @Test void explicitCreateCannotSilentlyResolveAnExistingSourceAlias(){
        var s=data();s.get("employee_source_identity").getFirst().put("source_key","alias");var p=plan(input("PERSONNEL_CHANGES","action","employeeKey","name"),new String[][]{{"action","employeeKey","name"},{"CREATE_EMPLOYEE","alias","Alice"}},s);issue(p,"IDENTITY_CONFLICT");
    }
    @Test void conflictingAliasesForOneStableEmployeeDoNotRenameItTwice(){
        var s=data();s.put("employee_source_identity",List.of(s.get("employee_source_identity").getFirst(),row("id",id(45),"source_system","HR","source_key","alias","employee_id",E,"version",1L)));var p=plan(input("EMPLOYEE_INITIALIZATION","employeeKey","name","companyCode","departmentCode","positionCode"),new String[][]{{"employeeKey","name","companyCode","departmentCode","positionCode"},{"alice","Alice","C","D","P"},{"alias","Other","C","D","P"}},s);issue(p,"EMPLOYEE_ATTRIBUTE_CONFLICT");
    }
    @Test void anExplicitStableCodeAllowsALiteralSlashInAnOrganizationName(){
        var p=plan(input("ORGANIZATION","companyCode","companyName"),new String[][]{{"companyCode","companyName"},{"C","Company/Label"}},data());assertThat(actions(p)).containsExactly("RENAME_COMPANY");assertThat(p.issues()).isEmpty();
    }
    @Test void distinctLossEventsAdvanceTheSameSourceTwiceWhileGrantImpactIsDeduplicated(){
        var s=data();authorization(s);var p=plan(input("PERSONNEL_CHANGES","action","employeeId","employmentId","expectedVersion"),new String[][]{{"action","employeeId","employmentId","expectedVersion"},{"END_EMPLOYMENT",E,JOB,"1"},{"DISABLE_EMPLOYEE",E,"","1"}},s);assertThat(actions(p)).containsExactly("END_EMPLOYMENT","DISABLE_EMPLOYEE");var lifecycle=(Map<?,?>)p.changes().getLast().normalizedValues().get("lifecycle");assertThat((List<?>)lifecycle.get("closedSources")).hasSize(1);assertThat(p.counts().invalidatedGrants()).isEqualTo(1);
    }
    @Test void aConfirmedMappingCannotContradictItsCompanyDepartmentOwnership(){
        var s=data();s.put("company",List.of(s.get("company").getFirst(),row("id",id(30),"code","C2","name","Other","version",1L)));var base=input("ORGANIZATION","companyCode","companyName","departmentName");var mapped=new Input(base.fileId(),base.sourceSystem(),base.sourceBatchId(),base.importType(),base.worksheet(),base.fieldMappings(),List.of(new OrganizationMapping("Company/Department",id(30),D,"C2","D")),base.initialEmploymentState(),base.reason());var p=plan(mapped,new String[][]{{"companyCode","companyName","departmentName"},{"C","Company","Department"}},s);issue(p,"INVALID_OWNERSHIP");
    }
    @Test void explicitlyMovingAnUnreferencedMappingKeepsItsIdEvenWhenThePathTextIsUnchanged(){
        var s=data();s.put("employment",List.of());s.put("company",List.of(s.get("company").getFirst(),row("id",id(30),"code","C2","name","Company","version",1L)));s.put("organization_mapping",List.of(row("id",id(31),"source_system","HR","source_path","Company/Department","object_kind","DEPARTMENT","company_id",C,"department_id",D,"position_id",null,"version",1L)));var base=input("ORGANIZATION","action","departmentCode","targetCompanyCode","expectedVersion");var mapped=new Input(base.fileId(),base.sourceSystem(),base.sourceBatchId(),base.importType(),base.worksheet(),base.fieldMappings(),List.of(new OrganizationMapping("Company/Department",id(30),D,"C2","D")),base.initialEmploymentState(),base.reason());var p=plan(mapped,new String[][]{{"action","departmentCode","targetCompanyCode","expectedVersion"},{"MOVE_DEPARTMENT","D","C2","1"}},s);assertThat(actions(p)).containsExactly("MOVE_DEPARTMENT");assertThat(p.issues()).isEmpty();assertThat(p.changes().getFirst().normalizedValues().get("mappingWrites").toString()).contains(id(31),"expectedVersion=1");
    }
    @Test void anExplicitMappingCanChooseTheStableCodeOfANewOrganization(){
        var base=input("ORGANIZATION","companyName");var mapped=new Input(base.fileId(),base.sourceSystem(),base.sourceBatchId(),base.importType(),base.worksheet(),base.fieldMappings(),List.of(new OrganizationMapping("New company",null,null,"C_MANUAL",null)),base.initialEmploymentState(),base.reason());var p=plan(mapped,new String[][]{{"companyName"},{"New company"}},Map.of());assertThat(actions(p)).containsExactly("CREATE_COMPANY");assertThat(p.changes().getFirst().stableCode()).isEqualTo("C_MANUAL");
    }
}
