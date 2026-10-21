package com.adgn.backend.change;

import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.grant.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementAccess;
import com.adgn.backend.role.repository.RoleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;

/** 在调用者主库事务内先整份授权，随后校验事实并物化0基线。 */
public final class DraftPlanner {
    private final JdbcTemplate jdbc;
    private final RoleRepository roles;
    private final GrantReader grants;
    private final BaselineWriter baselines;
    private final JsonMapper json;
    public DraftPlanner(DataSource source,RoleRepository roles,GrantReader grants,BaselineWriter baselines,JsonMapper json){this.jdbc=new JdbcTemplate(source);this.roles=roles;this.grants=grants;this.baselines=baselines;this.json=json;}
    public void authorize(ManagementAccess.Authority a,List<DraftRepository.Target> targets,boolean configuration,boolean hidden){
        if(targets.isEmpty())throw new IdentityFailure(500,"DATA_INTEGRITY_ERROR");
        for(var t:targets){
            var owner=roles.ownership(t.role());
            boolean covered=a.role(t.company(),t.department(),t.system(),true)
                && owner!=null&&a.role(owner.companyId(),owner.departmentId(),owner.systemId(),configuration);
            if(!covered)throw new IdentityFailure(hidden?404:403,hidden?"NOT_FOUND":"FORBIDDEN");
        }
    }
    public List<DraftRepository.Target> plan(WriteContext<ManagementAccess.Authority> c,JsonNode content){
        String kind=content.path("kind").stringValue(),company=content.path("companyId").stringValue();
        var targets=new ArrayList<DraftRepository.Target>();var a=c.authority();
        // 第一遍只解析归属及准确历史，不能用“无变化”跳过整份权限校验。
        if(kind.equals("ROLE_CONFIGURATION")){
            var role=role(a,DraftInput.id(content,"roleId"),true);if(!role.companyId().equals(company))throw invalid();
            targets.add(new DraftRepository.Target(company,role.departmentId(),role.systemId(),null,role.id(),null,null,null,DraftInput.version(content,"expectedConfigurationVersion"),null,null,snapshot(role,content),true));
        }else for(var line:content.path("items")){
            if(kind.equals("ROLE_GRANT")){
                var role=role(a,DraftInput.id(line,"roleId"),false);String department=DraftInput.id(line,"sourceDepartmentId");
                if(!role.companyId().equals(company)||!DraftInput.id(line,"sourceCompanyId").equals(company))throw invalid();
                if(!a.role(company,department,role.systemId(),true))throw new IdentityFailure(403,"FORBIDDEN");
                targets.add(new DraftRepository.Target(company,department,role.systemId(),DraftInput.id(line,"employeeId"),role.id(),null,null,null,DraftInput.version(line,"expectedRoleConfigurationVersion"),DraftInput.version(line,"expectedSourceEpoch"),DraftInput.version(line,"expectedRelationVersion"),snapshot(role,line),true));
            }else{
                var grant=grants.grant(DraftInput.id(line,"grantId")).orElseThrow(()->new IdentityFailure(404,"NOT_FOUND"));
                var role=role(a,grant.roleId(),false);if(!grant.companyId().equals(company))throw invalid();
                if(!a.role(company,grant.sourceDepartmentId(),grant.systemId(),true))throw new IdentityFailure(403,"FORBIDDEN");
                targets.add(new DraftRepository.Target(company,grant.sourceDepartmentId(),grant.systemId(),grant.employeeId(),role.id(),null,null,grant.id(),null,null,DraftInput.version(line,"expectedRelationVersion"),snapshot(role,line),grant.status().equals("ACTIVE")));
            }
        }
        var result=new ArrayList<DraftRepository.Target>();var businessKeys=new HashSet<GrantReader.Key>();int i=0;
        for(var t:targets){
            if(kind.equals("ROLE_CONFIGURATION")){
                var role=roles.get(t.role());if(role.configurationVersion()!=t.configurationVersion())throw conflict();
                validatePermissions(role,DraftInput.ids(content.path("permissionIds"),0,10000));result.add(t);
            }else{
                var key=new GrantReader.Key(t.employee(),t.role(),company,t.department());if(!businessKeys.add(key))throw invalid();var baseline=grants.baseline(key);var line=content.path("items").get(i++);
                boolean needs=kind.equals("ROLE_GRANT")?baseline.activeGrantId()==null:t.needsChange();
                if(needs){
                    if(baseline.relationVersion()!=t.relationVersion())throw conflict();
                    if(kind.equals("ROLE_GRANT")){
                        if(baseline.role().configurationVersion()!=t.configurationVersion()||baseline.source().sourceEpoch()!=t.sourceEpoch())throw conflict();
                        if(!baseline.eligibleForGrant())throw invalid();
                        var actual=baseline.employments().stream().map(e->e.id().toString()).sorted().toList();
                        if(!actual.equals(DraftInput.ids(line.path("employmentIds"),1,Integer.MAX_VALUE)))throw conflict();
                    }else if(!t.grant().equals(baseline.activeGrantId()))throw conflict();
                }
                baseline=baselines.baseline(c,key);
                result.add(new DraftRepository.Target(t.company(),t.department(),t.system(),t.employee(),t.role(),baseline.source().id(),baseline.id(),t.grant(),t.configurationVersion(),t.sourceEpoch(),t.relationVersion(),t.snapshot(),needs));
            }
        }
        return List.copyOf(result);
    }
    private RoleRepository.View role(ManagementAccess.Authority a,String id,boolean configuration){
        var owner=roles.ownership(id);
        if(owner==null||!a.role(owner.companyId(),owner.departmentId(),owner.systemId(),false))throw new IdentityFailure(404,"NOT_FOUND");
        if(configuration&&!a.role(owner.companyId(),owner.departmentId(),owner.systemId(),true))throw new IdentityFailure(403,"FORBIDDEN");
        return roles.get(id);
    }
    private JsonNode snapshot(RoleRepository.View role,JsonNode line){return json.valueToTree(Map.of("schemaVersion",1,"role",role,"input",line));}
    public void validatePermissions(RoleRepository.View role,List<String> selected){
        boolean enabled=jdbc.queryForObject("SELECT enabled FROM business_system WHERE id=?",Boolean.class,role.systemId());
        Set<String> old=new HashSet<>(role.permissionIds());
        for(String id:selected){
            var rows=jdbc.queryForList("SELECT permission_type,function_id,enabled FROM permission WHERE id=? AND system_id=?",id,role.systemId());if(rows.isEmpty())throw invalid();
            var p=rows.getFirst();String function=(String)p.get("function_id"),type=(String)p.get("permission_type");
            boolean available=enabled&&((Number)p.get("enabled")).intValue()==1;
            if(type.equals("FUNCTION_OPERATION")){
                Set<String> seen=new HashSet<>();boolean first=true;
                while(function!=null){
                    if(!seen.add(function))throw new IdentityFailure(500,"DATA_INTEGRITY_ERROR");
                    var nodes=jdbc.queryForList("SELECT parent_id,node_type,enabled FROM directory_node WHERE id=? AND system_id=?",function,role.systemId());if(nodes.isEmpty()||first&&!nodes.getFirst().get("node_type").equals("FUNCTION"))throw invalid();
                    var node=nodes.getFirst();available&=((Number)node.get("enabled")).intValue()==1;function=(String)node.get("parent_id");first=false;
                }
                if(first)throw invalid();
            }else if(!type.equals("SYSTEM_ACCESS")||function!=null)throw invalid();
            if(!old.contains(id)&&!available)throw invalid();
        }
    }
    private static IdentityFailure invalid(){return IdentityFailure.invalid();}
    private static IdentityFailure conflict(){return new IdentityFailure(409,"VERSION_CONFLICT");}
}
