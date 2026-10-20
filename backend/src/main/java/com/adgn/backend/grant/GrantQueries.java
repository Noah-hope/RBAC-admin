package com.adgn.backend.grant;

import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.scope.service.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import javax.sql.DataSource;
import java.util.*;

/** HTTP-safe projections; POST endpoints are read-only and never materialize zero anchors. */
public final class GrantQueries {
    public record BaselineQuery(List<GrantReader.Key> items){ }
    public record SourceQuery(String employeeId,String companyId,String departmentId,List<String> systemIds){ }
    public record Items<T>(List<T> items){public Items{items=List.copyOf(items);}}
    public record BaselineView(GrantReader.Key key,String systemId,long roleConfigurationVersion,long sourceEpoch,long relationVersion,List<String> employmentIds,String activeGrantId){
        public BaselineView{employmentIds=List.copyOf(employmentIds);}
    }
    public record SourceView(GrantReader.SourceKey source,long sourceEpoch,long version){ }
    public record Page(List<GrantReader.Grant> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    public record Filter(String employeeId,String roleId,String companyId,String sourceDepartmentId,String systemId,String status){ }
    private final ReadBoundary reads;private final ManagementContextResolver contexts;private final GrantReader reader;private final JdbcTemplate jdbc;
    public GrantQueries(DataSource source,ReadBoundary reads,ManagementContextResolver contexts,GrantReader reader){this.jdbc=new JdbcTemplate(source);this.reads=reads;this.contexts=contexts;this.reader=reader;}
    public Items<BaselineView> baselines(Jwt token,ManagementContextResolver.Selection selection,BaselineQuery input)throws Exception{
        if(input==null)throw IdentityFailure.invalid();batch(input.items());var unique=new HashSet<GrantReader.Key>();
        for(var k:input.items()){if(k==null || !unique.add(k))throw IdentityFailure.invalid();ScopeInput.identifier(k.employeeId());ScopeInput.identifier(k.roleId());ScopeInput.identifier(k.sourceCompanyId());ScopeInput.identifier(k.sourceDepartmentId());}
        return reads.read(()->contexts.resolve(token,selection),a->{
            for(var k:input.items())cover(a,k.sourceCompanyId(),k.sourceDepartmentId(),Set.of());
            for(var k:input.items()){
                var r=reader.role(k.roleId());cover(a,r.companyId(),k.sourceDepartmentId(),Set.of(r.systemId()));
                if(!a.global() && r.departmentId()!=null && !r.departmentId().equals(a.departmentId()))throw forbidden();
            }
            var views=new ArrayList<BaselineView>();for(var k:input.items()){
                var b=reader.baseline(k);views.add(new BaselineView(k,b.role().systemId(),b.role().configurationVersion(),b.source().sourceEpoch(),b.relationVersion(),b.employments().stream().map(e->e.id().toString()).toList(),b.activeGrantId()));
            }return new Items<>(views);
        });
    }
    public Items<SourceView> sources(Jwt token,ManagementContextResolver.Selection selection,SourceQuery input)throws Exception{
        if(input==null)throw IdentityFailure.invalid();ScopeInput.identifier(input.employeeId());ScopeInput.identifier(input.companyId());ScopeInput.identifier(input.departmentId());batch(input.systemIds());
        var systems=new HashSet<String>();for(String system:input.systemIds())if(!systems.add(ScopeInput.identifier(system)))throw IdentityFailure.invalid();
        return reads.read(()->contexts.resolve(token,selection),a->{
            cover(a,input.companyId(),input.departmentId(),systems);var result=new ArrayList<SourceView>();
            for(String system:input.systemIds()){var s=reader.source(new GrantReader.SourceKey(input.employeeId(),input.companyId(),input.departmentId(),system));result.add(new SourceView(s.key(),s.sourceEpoch(),s.version()));}
            return new Items<>(result);
        });
    }
    public Page list(Jwt token,ManagementContextResolver.Selection selection,String page,String pageSize,Filter f)throws Exception{
        var window=ScopeInput.page(page,pageSize);for(String value:new String[]{f.employeeId(),f.roleId(),f.companyId(),f.sourceDepartmentId(),f.systemId()})if(value!=null)ScopeInput.identifier(value);
        if(f.status()!=null && !Set.of("ACTIVE","REVOKED","INVALIDATED").contains(f.status()))throw IdentityFailure.invalid();
        return reads.read(()->contexts.resolve(token,selection),a->{
            if(!a.global() && (f.companyId()!=null && !f.companyId().equals(a.companyId()) || f.sourceDepartmentId()!=null && !f.sourceDepartmentId().equals(a.departmentId()) || f.systemId()!=null && !a.systemIds().contains(f.systemId())))throw forbidden();
            String from=" FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id JOIN authorization_source s ON s.id=b.source_id WHERE 1=1";var args=new ArrayList<Object>();
            if(!a.global()){from+=" AND s.company_id=? AND s.department_id=? AND EXISTS(SELECT 1 FROM scope_system ss WHERE ss.scope_id=? AND ss.system_id=s.system_id)";args.add(a.companyId());args.add(a.departmentId());args.add(a.scopeId());}
            from+=equal("s.employee_id",f.employeeId(),args)+equal("b.role_id",f.roleId(),args)+equal("s.company_id",f.companyId(),args)+equal("s.department_id",f.sourceDepartmentId(),args)+equal("s.system_id",f.systemId(),args)+equal("g.status",f.status(),args);
            long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
            if(window.offset()>=total)return new Page(List.of(),window.page(),window.size(),total);
            args.add(window.size());args.add(window.offset());var ids=jdbc.queryForList("SELECT g.id"+from+" ORDER BY g.created_at DESC,g.id DESC LIMIT ? OFFSET ?",String.class,args.toArray());
            var result=new ArrayList<GrantReader.Grant>();for(String id:ids)result.add(reader.grant(id).orElseThrow(()->new IllegalStateException("Grant disappeared inside snapshot")));
            return new Page(result,window.page(),window.size(),total);
        });
    }
    private static void batch(List<?> items){if(items==null || items.isEmpty() || items.size()>100)throw IdentityFailure.invalid();}
    private static void cover(ManagementContext a,String company,String department,Set<String> systems){if(!a.global() && (!a.companyId().equals(company) || !a.departmentId().equals(department) || !a.systemIds().containsAll(systems)))throw forbidden();}
    private static String equal(String name,String value,List<Object> args){if(value==null)return "";args.add(value);return " AND "+name+"=?";}
    private static IdentityFailure forbidden(){return new IdentityFailure(403,"FORBIDDEN");}
}
