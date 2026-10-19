package com.adgn.backend.catalog.repository;

import com.adgn.backend.core.transaction.*;
import com.adgn.backend.query.management.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class SystemRepository {
    public record View(String id,String code,String name,String description,boolean enabled,long version,long catalogVersion,String systemAccessPermissionId,Instant createdAt) { }
    public record Page(List<View> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    private static final String SELECT="SELECT s.*, (SELECT p.id FROM permission p WHERE p.system_id=s.id AND p.permission_type='SYSTEM_ACCESS') AS access_id FROM business_system s";
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public SystemRepository(DataSource source){this.source=source;this.jdbc=new JdbcTemplate(source);}
    public boolean duplicate(String code){return jdbc.queryForObject("SELECT COUNT(*) FROM business_system WHERE code=?",Integer.class,code)>0;}
    public View get(String id){
        var rows=jdbc.query(SELECT+" WHERE s.id=?",(rs,n)->map(rs),id);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Page list(ManagementAccess.Authority access,ManagementInput.Pagination page,Boolean enabled,String q){
        var where=new StringBuilder(" WHERE 1=1");var args=new ArrayList<Object>();
        if(!access.global()){
            where.append(" AND EXISTS(SELECT 1 FROM scope_system ss WHERE ss.scope_id=? AND ss.system_id=s.id)");
            args.add(access.scopeId());
        }
        if(enabled!=null){where.append(" AND s.enabled=?");args.add(enabled);}
        if(q!=null){where.append(" AND (s.code LIKE ? ESCAPE '=' OR s.name LIKE ? ESCAPE '=')");args.add(ManagementInput.like(q));args.add(ManagementInput.like(q));}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM business_system s"+where,Long.class,args.toArray());
        args.add(page.pageSize());args.add(page.offset());
        var items=jdbc.query(SELECT+where+" ORDER BY s.created_at DESC,s.id DESC LIMIT ? OFFSET ?",(rs,n)->map(rs),args.toArray());
        return new Page(items,page.page(),page.pageSize(),total);
    }
    public View create(WriteContext<?> context,String code,String name,String description){
        context.requireActive(source);String system=context.newId().toString(),permission=context.newId().toString();
        jdbc.update("INSERT INTO business_system(id,code,name,description,enabled,created_at) VALUES(?,?,?,?,0,?)",ps->{
            ps.setString(1,system);ps.setString(2,code);ps.setString(3,name);ps.setString(4,description);ps.setTimestamp(5,Timestamp.from(context.now()),utc());
        });
        jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,function_id,menu_eligible,enabled,created_at) VALUES(?,?,?,?,'SYSTEM_ACCESS',NULL,0,1,?)",ps->{
            ps.setString(1,permission);ps.setString(2,system);ps.setString(3,code+":access");ps.setString(4,"系统进入");ps.setTimestamp(5,Timestamp.from(context.now()),utc());
        });
        return get(system);
    }
    private static View map(ResultSet rs)throws SQLException{
        String access=rs.getString("access_id");
        if(access==null)throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
        return new View(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("description"),rs.getBoolean("enabled"),rs.getLong("version"),rs.getLong("catalog_version"),access,rs.getTimestamp("created_at",utc()).toInstant());
    }
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
}
