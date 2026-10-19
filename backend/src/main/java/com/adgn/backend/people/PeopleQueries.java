package com.adgn.backend.people;

import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.scope.service.ManagementContextResolver;
import com.adgn.backend.identity.security.IdentityFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** People-specific visibility, with current single-appointment qualification supplied by BE-08. */
public final class PeopleQueries {
    public record Context(String appointmentId,String appointmentVersion,String scopeVersion) { }
    public record Filter(String page,String pageSize,String companyId,String departmentId,String status,String q) { }
    public record Page<T>(List<T> items,long page,int pageSize,long total) {
        public Page { items=List.copyOf(items); }
    }
    public record Employee(String id,String employeeKey,String name,String status,long version,Instant createdAt) { }
    public record Employment(String id,String employeeId,String companyId,String departmentId,String positionId,
                             String status,long version,Instant startedAt,Instant endedAt) { }
    private record Access(String company,String department,String scope) {
        boolean global(){return company==null;}
    }
    private final JdbcTemplate jdbc;
    private final ReadBoundary reads;
    private final ManagementContextResolver contexts;
    public PeopleQueries(DataSource source,ReadBoundary reads,ManagementContextResolver contexts){
        this.jdbc=new JdbcTemplate(source);this.reads=reads;this.contexts=contexts;
    }
    public Page<?> list(Jwt token,Context context,String resource,String employeeId,Filter f)throws Exception{
        long page=number(f.page(),1),size=number(f.pageSize(),20);
        if(!Set.of(10L,20L,50L,100L).contains(size))throw IdentityFailure.invalid();
        long offset;try{offset=Math.multiplyExact(page-1,size);}catch(ArithmeticException bad){throw IdentityFailure.invalid();}
        identifier(f.companyId());identifier(f.departmentId());identifier(employeeId);
        String q=trim(f.q());
        if(q!=null && (q.isEmpty() || q.codePointCount(0,q.length())>100))throw IdentityFailure.invalid();
        if(f.status()!=null && !(resource.equals("employments")?Set.of("ACTIVE","ENDED"):Set.of("ACTIVE","INACTIVE")).contains(f.status()))throw IdentityFailure.invalid();
        return reads.read(()->access(token,context),a->{
            checkFilters(a,f);
            var args=new ArrayList<Object>();String from,where=" WHERE 1=1",columns,order;RowMapper<?> mapper;
            switch(resource){
                case "companies" -> {
                    from="company c";columns="c.id,c.code,c.name,c.version";order="c.code,c.id";
                    if(!a.global()){where+=" AND c.id=?";args.add(a.company());}
                    where+=search("c.name","c.code",q,args);
                    mapper=(r,n)->new OrganizationReader.Company(UUID.fromString(r.getString("id")),r.getString("code"),r.getString("name"),r.getLong("version"));
                }
                case "departments" -> {
                    from="department d";columns="d.id,d.company_id,d.code,d.name,d.version";order="d.code,d.id";
                    if(!a.global()){where+=" AND d.id=?";args.add(a.department());}
                    where+=equal("d.company_id",f.companyId(),args)+search("d.name","d.code",q,args);
                    mapper=(r,n)->new OrganizationReader.Department(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("company_id")),r.getString("code"),r.getString("name"),r.getLong("version"));
                }
                case "positions" -> {
                    from="`position` p JOIN department d ON d.id=p.department_id";columns="p.id,p.department_id,p.code,p.name,p.version";order="p.code,p.id";
                    if(!a.global()){where+=" AND d.id=?";args.add(a.department());}
                    where+=equal("d.company_id",f.companyId(),args)+equal("p.department_id",f.departmentId(),args)+search("p.name","p.code",q,args);
                    mapper=(r,n)->new OrganizationReader.Position(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("department_id")),r.getString("code"),r.getString("name"),r.getLong("version"));
                }
                case "employees" -> {
                    from="employee e";columns="e.*";order="e.created_at DESC,e.id DESC";
                    where+=visible(a,f.companyId(),f.departmentId(),args)+equal("e.status",f.status(),args)+search("e.name","e.employee_key",q,args);
                    mapper=PeopleQueries::employee;
                }
                case "employments" -> {
                    if(findEmployee(a,employeeId)==null)throw missing();
                    from="employment em";columns="em.*";order="em.created_at DESC,em.id DESC";
                    where+=equal("em.employee_id",employeeId,args);
                    if(!a.global()){where+=" AND em.company_id=? AND em.department_id=?";args.add(a.company());args.add(a.department());}
                    where+=equal("em.company_id",f.companyId(),args)+equal("em.department_id",f.departmentId(),args)+equal("em.status",f.status(),args);
                    mapper=PeopleQueries::employment;
                }
                default -> throw new IllegalArgumentException("Unknown people resource");
            }
            Long total=jdbc.queryForObject("SELECT COUNT(*) FROM "+from+where,Long.class,args.toArray());
            args.add(size);args.add(offset);
            var items=jdbc.query("SELECT "+columns+" FROM "+from+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",mapper,args.toArray());
            return new Page<>(items,page,(int)size,total==null?0:total);
        });
    }
    public Employee get(Jwt token,Context context,String employeeId)throws Exception{
        identifier(employeeId);
        return reads.read(()->access(token,context),a->{var e=findEmployee(a,employeeId);if(e==null)throw missing();return e;});
    }
    private Access access(Jwt token,Context c){
        var a=contexts.resolve(token,new ManagementContextResolver.Selection(c.appointmentId(),c.appointmentVersion(),c.scopeVersion()));
        return new Access(a.companyId(),a.departmentId(),a.scopeId());
    }
    private static void checkFilters(Access a,Filter f){
        if(!a.global() && ((f.companyId()!=null && !f.companyId().equals(a.company())) || (f.departmentId()!=null && !f.departmentId().equals(a.department()))))
            throw new IdentityFailure(403,"FORBIDDEN");
    }
    private Employee findEmployee(Access a,String id){
        var args=new ArrayList<Object>();args.add(id);
        var rows=jdbc.query("SELECT e.* FROM employee e WHERE e.id=?"+visible(a,null,null,args),PeopleQueries::employee,args.toArray());
        return rows.isEmpty()?null:rows.getFirst();
    }
    private static String visible(Access a,String company,String department,List<Object> args){
        if(a.global() && company==null && department==null)return "";
        String c=a.global()?company:a.company(),d=a.global()?department:a.department();
        String jobs=equal("v.company_id",c,args)+equal("v.department_id",d,args);
        String sources=equal("s.company_id",c,args)+equal("s.department_id",d,args);
        if(!a.global()){sources+=" AND EXISTS(SELECT 1 FROM scope_system ss WHERE ss.scope_id=? AND ss.system_id=s.system_id)";args.add(a.scope());}
        return " AND (EXISTS(SELECT 1 FROM employment v WHERE v.employee_id=e.id AND v.status='ACTIVE'"+jobs+")"
                +" OR EXISTS(SELECT 1 FROM authorization_source s WHERE s.employee_id=e.id"+sources+"))";
    }
    private static String equal(String column,String value,List<Object> args){if(value==null)return "";args.add(value);return " AND "+column+"=?";}
    private static String search(String first,String second,String value,List<Object> args){
        if(value==null)return "";
        String pattern="%"+value.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        args.add(pattern);args.add(pattern);return " AND ("+first+" LIKE ? ESCAPE '!' OR "+second+" LIKE ? ESCAPE '!')";
    }
    private static Employee employee(ResultSet r,int n)throws SQLException{return new Employee(r.getString("id"),r.getString("employee_key"),r.getString("name"),r.getString("status"),r.getLong("version"),instant(r,"created_at"));}
    private static Employment employment(ResultSet r,int n)throws SQLException{return new Employment(r.getString("id"),r.getString("employee_id"),r.getString("company_id"),r.getString("department_id"),r.getString("position_id"),r.getString("status"),r.getLong("version"),instant(r,"started_at"),instant(r,"ended_at"));}
    private static Instant instant(ResultSet r,String field)throws SQLException{var t=r.getTimestamp(field,Calendar.getInstance(TimeZone.getTimeZone("UTC")));return t==null?null:t.toInstant();}
    private static String trim(String value){return value==null?null:value.strip();}
    private static void identifier(String value){if(value!=null && !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw IdentityFailure.invalid();}
    private static long number(String value,long fallback){if(value==null)return fallback;long n=version(value);if(n<1)throw IdentityFailure.invalid();return n;}
    private static long version(String value){try{if(!value.matches("[0-9]+"))throw IdentityFailure.invalid();long n=Long.parseLong(value);if(n>9007199254740991L)throw IdentityFailure.invalid();return n;}catch(NumberFormatException e){throw IdentityFailure.invalid();}}
    private static IdentityFailure missing(){return new IdentityFailure(404,"NOT_FOUND");}
}
