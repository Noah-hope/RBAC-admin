package com.adgn.backend.people;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Current facts only; the caller owns authorization and its existing primary database transaction. */
public final class OrganizationReader {
    public record Company(UUID id, String code, String name, long version) { }
    public record Department(UUID id, UUID companyId, String code, String name, long version) { }
    public record Position(UUID id, UUID departmentId, String code, String name, long version) { }
    public record Employee(UUID id, String employeeKey, String name, String status, long version) { }
    public record SourceIdentity(UUID id, String sourceSystem, String sourceKey, UUID employeeId, long version) { }
    public record OrganizationMapping(UUID id, String sourceSystem, String sourcePath, String objectKind,
                                      UUID companyId, UUID departmentId, UUID positionId, long version) { }
    public record Employment(UUID id, UUID employeeId, UUID companyId, UUID departmentId, UUID positionId,
                             String status, Instant startedAt, long version) { }
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public OrganizationReader(DataSource source) {
        this.source=Objects.requireNonNull(source);
        this.jdbc=new JdbcTemplate(source);
    }
    public Optional<Company> company(UUID id) {
        return one("SELECT id,code,name,version FROM company WHERE id=?",
                (r,n)->new Company(uuid(r,"id"),r.getString("code"),r.getString("name"),r.getLong("version")),id);
    }
    public Optional<Department> department(UUID id) {
        return one("SELECT id,company_id,code,name,version FROM department WHERE id=?",
                (r,n)->new Department(uuid(r,"id"),uuid(r,"company_id"),r.getString("code"),r.getString("name"),r.getLong("version")),id);
    }
    public Optional<Position> position(UUID id) {
        return one("SELECT id,department_id,code,name,version FROM `position` WHERE id=?",
                (r,n)->new Position(uuid(r,"id"),uuid(r,"department_id"),r.getString("code"),r.getString("name"),r.getLong("version")),id);
    }
    public Optional<Employee> employee(UUID id) {
        return one("SELECT id,employee_key,name,status,version FROM employee WHERE id=?",
                (r,n)->new Employee(uuid(r,"id"),r.getString("employee_key"),r.getString("name"),r.getString("status"),r.getLong("version")),id);
    }
    public Optional<SourceIdentity> sourceIdentity(String system, String key) {
        return one("SELECT id,source_system,source_key,employee_id,version FROM employee_source_identity WHERE source_system=? AND source_key=?",
                (r,n)->new SourceIdentity(uuid(r,"id"),r.getString("source_system"),r.getString("source_key"),uuid(r,"employee_id"),r.getLong("version")),system,key);
    }
    public Optional<OrganizationMapping> organizationMapping(String system, String path) {
        return one("SELECT id,source_system,source_path,object_kind,company_id,department_id,position_id,version FROM organization_mapping WHERE source_system=? AND source_path=?",
                (r,n)->new OrganizationMapping(uuid(r,"id"),r.getString("source_system"),r.getString("source_path"),r.getString("object_kind"),
                        uuid(r,"company_id"),uuid(r,"department_id"),uuid(r,"position_id"),r.getLong("version")),system,path);
    }
    public List<Employment> applicableEmployments(UUID employeeId, UUID companyId, UUID departmentId) {
        requireTransaction();
        var rows=jdbc.query("""
                SELECT em.id,em.employee_id,em.company_id,em.department_id,em.position_id,em.status,em.started_at,em.version
                FROM employment em JOIN employee e ON e.id=em.employee_id
                WHERE e.status='ACTIVE' AND em.status='ACTIVE' AND em.employee_id=? AND em.company_id=? AND em.department_id=?
                ORDER BY em.id
                """,(r,n)->new Employment(uuid(r,"id"),uuid(r,"employee_id"),uuid(r,"company_id"),uuid(r,"department_id"),uuid(r,"position_id"),
                        r.getString("status"),instant(r,"started_at"),r.getLong("version")),
                Objects.requireNonNull(employeeId).toString(),Objects.requireNonNull(companyId).toString(),Objects.requireNonNull(departmentId).toString());
        return List.copyOf(rows);
    }
    private <T> Optional<T> one(String sql,RowMapper<T> mapper,Object... args) {
        requireTransaction();
        Object[] values=Arrays.stream(args).map(value->{
            Objects.requireNonNull(value);
            return value instanceof UUID ? value.toString():value;
        }).toArray();
        return jdbc.query(sql,mapper,values).stream().findFirst();
    }
    private void requireTransaction() {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source))
            throw new IllegalStateException("OrganizationReader requires the caller transaction on its own datasource");
    }
    private static UUID uuid(ResultSet rows,String column) throws SQLException {
        String value=rows.getString(column);
        return value==null ? null:UUID.fromString(value);
    }
    private static Instant instant(ResultSet rows,String column) throws SQLException {
        var value=rows.getTimestamp(column,Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return value==null ? null:value.toInstant();
    }
}
