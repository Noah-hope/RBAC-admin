package com.adgn.backend.role.service;

import com.adgn.backend.core.transaction.*;
import com.adgn.backend.query.management.ManagementInput;
import com.adgn.backend.role.repository.RoleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.*;

/** Internal M09 execution port. Never starts a transaction or exposes an HTTP endpoint. */
public final class RoleConfigurationApplier {
    public static final class Rejection extends RuntimeException {
        private final String code;
        public Rejection(String code){super("Role configuration rejected: "+code);this.code=code;}
        public String code(){return code;}
    }
    private record Permission(String id,String code,String name,String permissionType,String functionId,boolean enabled,long version) { }
    private record Node(String id,String parent,String type,boolean enabled) { }
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final RoleRepository roles;
    private final JsonMapper json;
    public RoleConfigurationApplier(DataSource source,RoleRepository roles,JsonMapper json){this.source=source;this.jdbc=new JdbcTemplate(source);this.roles=roles;this.json=json;}

    /**
     * Caller: BE-25 after checking canonical fixed payload, original scope and full request.
     * This port rechecks the approved claim/target and B-owned invariants, applies the exact
     * final set, and appends history. Caller owns audit/success, invalidation and claim release.
     */
    public RoleRepository.View apply(WriteContext<?> context,String requestId,String roleId,long expectedVersion,List<String> permissionIds){
        Objects.requireNonNull(context,"A guarded execution context is required").requireActive(source);
        try{
            ManagementInput.identifier(requestId);ManagementInput.identifier(roleId);ManagementInput.version(expectedVersion);
            if(permissionIds==null || permissionIds.isEmpty())throw reject("EMPTY_CONFIGURATION");
            if(permissionIds.size()>10000 || permissionIds.stream().anyMatch(Objects::isNull) || new HashSet<>(permissionIds).size()!=permissionIds.size())throw reject("VALIDATION_ERROR");
            permissionIds.forEach(ManagementInput::identifier);
            var role=roles.get(roleId);if(role==null)throw reject("STATE_CONFLICT");
            if(role.configurationVersion()!=expectedVersion)throw reject("ROLE_CONFIGURATION_CHANGED");
            requireApprovedTarget(requestId,role,expectedVersion);
            var old=new HashSet<>(role.permissionIds());var desired=new HashSet<>(permissionIds);
            if(old.equals(desired))throw reject("STATE_CONFLICT");
            if(expectedVersion>=ManagementInput.MAX_VERSION || jdbc.queryForObject("SELECT row_version FROM business_role WHERE id=?",Long.class,roleId)>=ManagementInput.MAX_VERSION)throw reject("STATE_CONFLICT");

            var all=jdbc.query("SELECT id,code,name,permission_type,function_id,enabled,version FROM permission WHERE system_id=?",(rs,n)->new Permission(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("permission_type"),rs.getString("function_id"),rs.getBoolean("enabled"),rs.getLong("version")),role.systemId());
            var permissions=new HashMap<String,Permission>();for(var p:all)permissions.put(p.id(),p);
            var nodes=new HashMap<String,Node>();
            jdbc.query("SELECT id,parent_id,node_type,enabled FROM directory_node WHERE system_id=?",rs->{nodes.put(rs.getString("id"),new Node(rs.getString("id"),rs.getString("parent_id"),rs.getString("node_type"),rs.getBoolean("enabled")));},role.systemId());
            boolean systemEnabled=jdbc.queryForObject("SELECT enabled FROM business_system WHERE id=?",Boolean.class,role.systemId());
            for(String id:desired){
                var p=permissions.get(id);if(p==null)throw reject("INVALID_OWNERSHIP");
                if(p.permissionType().equals("FUNCTION_OPERATION")){
                    var function=nodes.get(p.functionId());
                    if(function==null || !function.type().equals("FUNCTION"))throw reject("INVALID_OWNERSHIP");
                }else if(!p.permissionType().equals("SYSTEM_ACCESS") || p.functionId()!=null)throw reject("INVALID_OWNERSHIP");
                if(!old.contains(id) && (!systemEnabled || !p.enabled() || p.functionId()!=null && !enabledPath(nodes,p.functionId())))throw reject("PERMISSION_UNAVAILABLE");
            }
            // All final references and additions are validated before the first business write.
            jdbc.update("DELETE FROM role_permission WHERE role_id=?",roleId);
            for(String id:new TreeSet<>(desired))jdbc.update("INSERT INTO role_permission(role_id,permission_id,system_id) VALUES(?,?,?)",roleId,id,role.systemId());
            if(jdbc.update("UPDATE business_role SET state='EFFECTIVE',configuration_version=configuration_version+1,row_version=row_version+1 WHERE id=? AND configuration_version=?",roleId,expectedVersion)!=1)throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
            var selected=desired.stream().sorted().map(permissions::get).toList();
            String snapshot=json.writeValueAsString(Map.of("schemaVersion",1,"roleId",roleId,"configurationVersion",expectedVersion+1,"permissions",selected));
            jdbc.update("INSERT INTO role_configuration_history(role_id,configuration_version,request_id,configuration_snapshot,applied_at) VALUES(?,?,?,?,?)",ps->{
                ps.setString(1,roleId);ps.setLong(2,expectedVersion+1);ps.setString(3,requestId);ps.setString(4,snapshot);ps.setTimestamp(5,Timestamp.from(context.now()),Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            });
            return roles.get(roleId);
        }catch(RuntimeException failure){context.markFailed(failure);throw failure;}
    }
    private void requireApprovedTarget(String request,RoleRepository.View role,long version){
        int count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM change_request cr
            JOIN role_change_claim claim ON claim.request_id=cr.id AND claim.role_id=?
            JOIN change_target target ON target.request_id=cr.id AND target.line_no=0
            JOIN oa_link link ON link.request_id=cr.id
            JOIN oa_final_result final ON final.request_id=cr.id AND final.decision='APPROVED'
            WHERE cr.id=? AND cr.kind='ROLE_CONFIGURATION' AND cr.company_id=?
              AND cr.state IN ('APPROVED','EXECUTION_FAILED') AND cr.approval_result='APPROVED'
              AND cr.delivery_status='LINKED' AND cr.execution_status IN ('PENDING','FAILED')
              AND target.role_id=? AND target.company_id=? AND target.system_id=?
              AND target.department_id <=> ? AND target.expected_configuration_version=?
              AND NOT EXISTS(SELECT 1 FROM role_configuration_history h WHERE h.request_id=cr.id)
            """,Integer.class,role.id(),request,role.companyId(),role.id(),role.companyId(),role.systemId(),role.departmentId(),version);
        if(count!=1)throw reject("STATE_CONFLICT");
    }
    private static boolean enabledPath(Map<String,Node> nodes,String id){
        Set<String> visited=new HashSet<>();
        while(id!=null){
            if(!visited.add(id))throw reject("INVALID_OWNERSHIP");
            var node=nodes.get(id);if(node==null)throw reject("INVALID_OWNERSHIP");
            if(!node.enabled())return false;
            id=node.parent();
        }
        return true;
    }
    private static Rejection reject(String code){return new Rejection(code);}
}
