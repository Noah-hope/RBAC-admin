package com.adgn.backend.role.repository;

import com.adgn.backend.core.transaction.*;
import com.adgn.backend.query.management.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class RoleRepository {
    public record View(String id,String code,String name,String description,String companyId,String departmentId,String systemId,String state,long metadataVersion,long configurationVersion,List<String> permissionIds,String activeChangeRequestId,Instant createdAt){public View{permissionIds=List.copyOf(permissionIds);}}
    public record Page(List<View> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    public record Filter(String company,String department,String system,String state,String q) { }
    public record Ownership(String companyId,String departmentId,String systemId) { }
    private static final String SELECT="SELECT r.*,(SELECT c.request_id FROM role_change_claim c WHERE c.role_id=r.id) AS active_request FROM business_role r";
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public RoleRepository(DataSource source){this.source=source;this.jdbc=new JdbcTemplate(source);}
    public Ownership ownership(String id){
        var rows=jdbc.query("SELECT company_id,department_id,system_id FROM business_role WHERE id=?",(rs,n)->new Ownership(rs.getString("company_id"),rs.getString("department_id"),rs.getString("system_id")),id);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public View get(String id){
        var rows=jdbc.query(SELECT+" WHERE r.id=?",(rs,n)->map(rs),id);
        return rows.isEmpty()?null:permissions(rows).getFirst();
    }
    public Page list(ManagementAccess.Authority a,ManagementInput.Pagination page,Filter filter){
        var where=new StringBuilder(" WHERE 1=1");var args=new ArrayList<Object>();
        if(!a.global()){
            where.append(" AND r.company_id=? AND (r.department_id=? OR r.department_id IS NULL) AND EXISTS(SELECT 1 FROM scope_system ss WHERE ss.scope_id=? AND ss.system_id=r.system_id)");
            args.add(a.companyId());args.add(a.departmentId());args.add(a.scopeId());
        }
        for(var column:List.of("company_id","department_id","system_id","state")){
            String value=switch(column){case "company_id"->filter.company();case "department_id"->filter.department();case "system_id"->filter.system();default->filter.state();};
            if(value!=null){where.append(" AND r.").append(column).append("=?");args.add(value);}
        }
        if(filter.q()!=null){where.append(" AND (r.code LIKE ? ESCAPE '=' OR r.name LIKE ? ESCAPE '=')");args.add(ManagementInput.like(filter.q()));args.add(ManagementInput.like(filter.q()));}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM business_role r"+where,Long.class,args.toArray());
        args.add(page.pageSize());args.add(page.offset());
        var rows=jdbc.query(SELECT+where+" ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",(rs,n)->map(rs),args.toArray());
        return new Page(permissions(rows),page.page(),page.pageSize(),total);
    }
    private List<View> permissions(List<View> roles){
        if(roles.isEmpty())return List.of();
        var ids=roles.stream().map(View::id).toArray();
        var permissions=new HashMap<String,List<String>>();
        jdbc.query("SELECT role_id,permission_id FROM role_permission WHERE role_id IN ("+String.join(",",Collections.nCopies(ids.length,"?"))+") ORDER BY permission_id",rs->{
            permissions.computeIfAbsent(rs.getString("role_id"),ignored->new ArrayList<>()).add(rs.getString("permission_id"));
        },ids);
        return roles.stream().map(r->{
            var selected=permissions.getOrDefault(r.id(),List.of());
            if((r.state().equals("EFFECTIVE") && selected.isEmpty()) || (r.state().equals("UNCONFIGURED") && !selected.isEmpty()))throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
            return new View(r.id(),r.code(),r.name(),r.description(),r.companyId(),r.departmentId(),r.systemId(),r.state(),r.metadataVersion(),r.configurationVersion(),selected,r.activeChangeRequestId(),r.createdAt());
        }).toList();
    }
    public View metadata(WriteContext<?> context,View old,String name,String description){
        context.requireActive(source);
        long row=jdbc.queryForObject("SELECT row_version FROM business_role WHERE id=?",Long.class,old.id());
        if(row>=ManagementInput.MAX_VERSION)throw new com.adgn.backend.identity.security.IdentityFailure(409,"STATE_CONFLICT");
        int changed=jdbc.update("UPDATE business_role SET name=?,description=?,metadata_version=metadata_version+1,row_version=row_version+1 WHERE id=? AND metadata_version=?",name,description,old.id(),old.metadataVersion());
        if(changed!=1)throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
        return get(old.id());
    }
    private static View map(ResultSet rs)throws SQLException{
        return new View(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("description"),rs.getString("company_id"),rs.getString("department_id"),rs.getString("system_id"),rs.getString("state"),rs.getLong("metadata_version"),rs.getLong("configuration_version"),List.of(),rs.getString("active_request"),rs.getTimestamp("created_at",Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant());
    }
}
