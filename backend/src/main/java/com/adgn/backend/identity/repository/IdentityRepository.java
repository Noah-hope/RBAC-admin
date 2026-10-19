package com.adgn.backend.identity.repository;

import com.adgn.backend.core.transaction.WriteContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Domain-owned SQL; callers supply the existing guarded transaction for every mutation. */
public final class IdentityRepository {
    public record Account(String id,String employeeId,String hash,boolean enabled,boolean superAdmin,
                          boolean restricted,long credentials,long epoch,boolean qualified) {
        @Override public String toString(){return "Account[redacted]";}
    }
    public record Session(String id,String accountId,String jti,String mode,long credentials,long epoch,Instant expires,boolean revoked) { }
    public record Service(String id,String systemId) { }
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public IdentityRepository(DataSource source) { this.source=source; jdbc=new JdbcTemplate(source); }
    private static final String ACCOUNT="""
        SELECT a.*, (a.enabled=1 AND (a.employee_id IS NULL OR e.status='ACTIVE') AND
          (a.is_super_admin=1 OR (a.employee_id IS NOT NULL AND EXISTS(
            SELECT 1 FROM admin_appointment ap JOIN management_scope sc ON sc.id=ap.scope_id
            JOIN employment em ON em.employee_id=a.employee_id AND em.company_id=sc.company_id
              AND em.department_id=sc.department_id AND em.status='ACTIVE'
            WHERE ap.account_id=a.id AND ap.status='ACTIVE')))) AS qualified
        FROM admin_account a LEFT JOIN employee e ON e.id=a.employee_id
        """;
    public Account byLogin(String login) { return account(ACCOUNT+" WHERE a.login_name=?",login); }
    public Account byId(String id) { return account(ACCOUNT+" WHERE a.id=?",id); }
    private Account account(String sql,String key) {
        var rows=jdbc.query(sql,(rs,n)->new Account(rs.getString("id"),rs.getString("employee_id"),
                rs.getString("password_hash"),rs.getBoolean("enabled"),rs.getBoolean("is_super_admin"),
                rs.getBoolean("must_change_password"),rs.getLong("credential_version"),rs.getLong("session_epoch"),rs.getBoolean("qualified")),key);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public Session session(String id) {
        var rows=jdbc.query("SELECT * FROM admin_session WHERE id=?",(rs,n)->new Session(rs.getString("id"),
                rs.getString("account_id"),rs.getString("token_jti"),rs.getString("mode"),rs.getLong("credential_version"),
                rs.getLong("session_epoch"),rs.getTimestamp("expires_at",utc()).toInstant(),rs.getTimestamp("revoked_at",utc())!=null),id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public Service service(String subject,String kind,String binding) {
        var rows=jdbc.query("SELECT id,system_id FROM service_identity WHERE subject=? AND kind=? AND enabled=1 "
                        + (kind.equals("BUSINESS") ? "AND system_id=?" : "AND id=?"),
                (rs,n)->new Service(rs.getString("id"),rs.getString("system_id")),subject,kind,binding);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public void createSession(WriteContext<?> context,Account account,String sid,String jti,String mode,Instant expires) {
        context.requireActive(source);
        jdbc.update("INSERT INTO admin_session(id,account_id,token_jti,mode,credential_version,session_epoch,expires_at,created_at) VALUES(?,?,?,?,?,?,?,?)",
                ps->{ps.setString(1,sid);ps.setString(2,account.id());ps.setString(3,jti);ps.setString(4,mode);
                    ps.setLong(5,account.credentials());ps.setLong(6,account.epoch());
                    ps.setTimestamp(7,Timestamp.from(expires),utc());ps.setTimestamp(8,Timestamp.from(context.now()),utc());});
    }
    public boolean revokeSession(WriteContext<?> context,String sid,String account) {
        context.requireActive(source);
        return jdbc.update("UPDATE admin_session SET revoked_at=? WHERE id=? AND account_id=? AND revoked_at IS NULL",
                ps->{ps.setTimestamp(1,Timestamp.from(context.now()),utc());ps.setString(2,sid);ps.setString(3,account);})==1;
    }
    public void changePassword(WriteContext<?> context,Account old,String hash) {
        context.requireActive(source);
        int updated=jdbc.update("UPDATE admin_account SET password_hash=?,must_change_password=0,credential_version=credential_version+1,"
                +"session_epoch=session_epoch+1,version=version+1,row_version=row_version+1 WHERE id=? AND credential_version=? AND session_epoch=?",
                hash,old.id(),old.credentials(),old.epoch());
        if(updated!=1)throw new IllegalStateException("Concurrent credential change");
        revokeAll(context,old.id());
    }
    public void revokeAll(WriteContext<?> context,String account) {
        context.requireActive(source);
        jdbc.update("UPDATE admin_session SET revoked_at=? WHERE account_id=? AND revoked_at IS NULL",
                ps->{ps.setTimestamp(1,Timestamp.from(context.now()),utc());ps.setString(2,account);});
    }
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
}
