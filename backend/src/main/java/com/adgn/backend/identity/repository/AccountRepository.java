package com.adgn.backend.identity.repository;

import com.adgn.backend.core.transaction.WriteContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

/** Account administration uses safe projections, never a serialized persistence entity. */
public final class AccountRepository {
    public record View(String id,String employeeId,String loginName,boolean enabled,boolean mustChangePassword,long version,Instant createdAt) { }
    public record Managed(View view,boolean superAdmin,long credentials,long epoch,long rowVersion) { }
    public record Page(List<View> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public AccountRepository(DataSource source){this.source=source;this.jdbc=new JdbcTemplate(source);}
    public boolean activeEmployee(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE id=? AND status='ACTIVE'",Integer.class,id)==1;}
    public boolean duplicate(String login,String employee){return jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE login_name=? OR employee_id=?",Integer.class,login,employee)>0;}
    public View view(String id){
        var rows=jdbc.query("SELECT id,employee_id,login_name,enabled,must_change_password,version,created_at FROM admin_account WHERE id=?",(rs,n)->map(rs),id);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Managed managed(String id){
        var rows=jdbc.query("SELECT id,employee_id,login_name,enabled,must_change_password,version,created_at,is_super_admin,credential_version,session_epoch,row_version FROM admin_account WHERE id=?",
                (rs,n)->new Managed(map(rs),rs.getBoolean("is_super_admin"),rs.getLong("credential_version"),rs.getLong("session_epoch"),rs.getLong("row_version")),id);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Page list(long page,int size,String employee,String prefix,Boolean enabled){
        var where=new StringBuilder(" WHERE 1=1");var args=new ArrayList<Object>();
        if(employee!=null){where.append(" AND employee_id=?");args.add(employee);}
        if(prefix!=null){where.append(" AND login_name LIKE ? ESCAPE '='");args.add(prefix.replace("=","==").replace("%","=%").replace("_","=_")+"%");}
        if(enabled!=null){where.append(" AND enabled=?");args.add(enabled);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM admin_account"+where,Long.class,args.toArray());
        args.add(size);args.add(Math.multiplyExact(page-1,size));
        var rows=jdbc.query("SELECT id,employee_id,login_name,enabled,must_change_password,version,created_at FROM admin_account"+where+" ORDER BY created_at,id LIMIT ? OFFSET ?",(rs,n)->map(rs),args.toArray());
        return new Page(rows,page,size,total);
    }
    public View create(WriteContext<?> context,String employee,String login,String hash){
        context.requireActive(source);String id=context.newId().toString();
        jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,enabled,is_super_admin,must_change_password,version,credential_version,session_epoch,row_version,created_at) VALUES(?,?,?,?,1,0,1,1,1,1,0,?)",
                ps->{ps.setString(1,id);ps.setString(2,employee);ps.setString(3,login);ps.setString(4,hash);ps.setTimestamp(5,Timestamp.from(context.now()),utc());});
        return view(id);
    }
    public View state(WriteContext<?> context,Managed old,boolean enabled){
        context.requireActive(source);
        int count=jdbc.update("UPDATE admin_account SET enabled=?,version=version+1,row_version=row_version+1,session_epoch=session_epoch+? WHERE id=? AND version=? AND is_super_admin=0",
                enabled,enabled?0:1,old.view().id(),old.view().version());
        if(count!=1)throw new IllegalStateException("Account changed outside the guarded boundary");
        return view(old.view().id());
    }
    public View reset(WriteContext<?> context,Managed old,String hash){
        context.requireActive(source);
        int count=jdbc.update("UPDATE admin_account SET password_hash=?,must_change_password=1,credential_version=credential_version+1,session_epoch=session_epoch+1,version=version+1,row_version=row_version+1 WHERE id=? AND version=? AND is_super_admin=0",
                hash,old.view().id(),old.view().version());
        if(count!=1)throw new IllegalStateException("Account changed outside the guarded boundary");
        return view(old.view().id());
    }
    private static View map(java.sql.ResultSet rs)throws java.sql.SQLException{
        return new View(rs.getString("id"),rs.getString("employee_id"),rs.getString("login_name"),rs.getBoolean("enabled"),rs.getBoolean("must_change_password"),rs.getLong("version"),rs.getTimestamp("created_at",utc()).toInstant());
    }
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
}
