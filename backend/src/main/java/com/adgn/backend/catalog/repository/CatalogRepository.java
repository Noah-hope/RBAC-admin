package com.adgn.backend.catalog.repository;

import com.adgn.backend.core.transaction.*;
import com.adgn.backend.query.management.ManagementInput;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.sql.Timestamp;

/** Catalog aggregate facts and guarded writes; no transaction is opened here. */
public final class CatalogRepository {
    public record Node(String id,String systemId,String code,String name,String nodeType,String parentId,int sortOrder,boolean enabled,String visibilityPermissionId,long version) { }
    public record Permission(String id,String systemId,String code,String name,String permissionType,String functionId,boolean menuEligible,boolean enabled,long version) { }
    public record Snapshot(Map<String,Node> nodes,Map<String,Permission> permissions){public Snapshot{nodes=Collections.unmodifiableMap(new LinkedHashMap<>(nodes));permissions=Collections.unmodifiableMap(new LinkedHashMap<>(permissions));}}
    public record Page<T>(List<T> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    public record Versioned<T>(Page<T> page,long catalogVersion) { }
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public CatalogRepository(DataSource source){this.source=source;this.jdbc=new JdbcTemplate(source);}
    public void requireWritable(String system){if(jdbc.queryForObject("SELECT row_version FROM business_system WHERE id=?",Long.class,system)>=ManagementInput.MAX_VERSION)throw new com.adgn.backend.identity.security.IdentityFailure(409,"STATE_CONFLICT");}
    public Snapshot snapshot(String system){
        var nodes=new LinkedHashMap<String,Node>();for(var n:jdbc.query("SELECT * FROM directory_node WHERE system_id=? ORDER BY id",(rs,i)->new Node(rs.getString("id"),rs.getString("system_id"),rs.getString("code"),rs.getString("name"),rs.getString("node_type"),rs.getString("parent_id"),rs.getInt("sort_order"),rs.getBoolean("enabled"),rs.getString("visibility_permission_id"),rs.getLong("version")),system))nodes.put(n.id(),n);
        var permissions=new LinkedHashMap<String,Permission>();for(var p:jdbc.query("SELECT * FROM permission WHERE system_id=? ORDER BY id",(rs,i)->new Permission(rs.getString("id"),rs.getString("system_id"),rs.getString("code"),rs.getString("name"),rs.getString("permission_type"),rs.getString("function_id"),rs.getBoolean("menu_eligible"),rs.getBoolean("enabled"),rs.getLong("version")),system))permissions.put(p.id(),p);
        return new Snapshot(nodes,permissions);
    }
    public Page<Node> nodes(String system,ManagementInput.Pagination page,String parent,String type,Boolean enabled,boolean roots){
        var all=snapshot(system).nodes().values().stream().filter(n->parent==null||parent.equals(n.parentId())).filter(n->!roots||n.parentId()==null)
                .filter(n->type==null||type.equals(n.nodeType())).filter(n->enabled==null||enabled==n.enabled())
                .sorted(Comparator.comparing((Node n)->n.parentId()==null?"":n.parentId()).thenComparingInt(Node::sortOrder).thenComparing(Node::code).thenComparing(Node::id)).toList();
        return slice(all,page);
    }
    public Page<Permission> permissions(String system,ManagementInput.Pagination page,String function,String type,Boolean enabled,String q){
        var all=snapshot(system).permissions().values().stream().filter(p->function==null||function.equals(p.functionId())).filter(p->type==null||type.equals(p.permissionType()))
                .filter(p->enabled==null||enabled==p.enabled()).filter(p->q==null||p.code().startsWith(q)||p.name().startsWith(q)).sorted(Comparator.comparing(Permission::code).thenComparing(Permission::id)).toList();
        return slice(all,page);
    }
    private static <T> Page<T> slice(List<T> all,ManagementInput.Pagination p){int from=(int)Math.min(p.offset(),all.size()),to=Math.min(from+p.pageSize(),all.size());return new Page<>(all.subList(from,to),p.page(),p.pageSize(),all.size());}
    public void save(WriteContext<?> context,String system,long expectedCatalog,Snapshot before,Snapshot after){
        context.requireActive(source);
        var newNodes=new LinkedHashMap<String,Node>();after.nodes().forEach((id,n)->{if(!before.nodes().containsKey(id))newNodes.put(id,n);});
        var inserted=new HashSet<>(before.nodes().keySet());
        while(!newNodes.isEmpty()){
            boolean progress=false;
            for(var n:new ArrayList<>(newNodes.values()))if(n.parentId()==null||inserted.contains(n.parentId())){
                jdbc.update("INSERT INTO directory_node(id,system_id,code,name,node_type,parent_id,sort_order,enabled,visibility_permission_id,version,created_at) VALUES(?,?,?,?,?,?,?,0,NULL,1,?)",ps->{
                    ps.setString(1,n.id());ps.setString(2,system);ps.setString(3,n.code());ps.setString(4,n.name());ps.setString(5,n.nodeType());ps.setString(6,n.parentId());ps.setInt(7,n.sortOrder());ps.setTimestamp(8,Timestamp.from(context.now()),utc());});
                inserted.add(n.id());newNodes.remove(n.id());progress=true;
            }
            if(!progress)throw new IllegalStateException("Validated final tree must be acyclic");
        }
        // Stage changed names so legal same-function name swaps satisfy immediate UNIQUE constraints.
        var reserved=new HashSet<String>();for(var p:before.permissions().values())reserved.add(p.functionId()+":"+p.name());
        for(var p:after.permissions().values())reserved.add(p.functionId()+":"+p.name());
        for(var p:after.permissions().values()){
            var old=before.permissions().get(p.id());if(old==null||old.name().equals(p.name()))continue;
            String temporary;do{temporary="~catalog-"+context.newId();}while(!reserved.add(p.functionId()+":"+temporary));
            jdbc.update("UPDATE permission SET name=? WHERE id=?",temporary,p.id());
        }
        for(var p:after.permissions().values()){
            var old=before.permissions().get(p.id());
            if(old==null)jdbc.update("INSERT INTO permission(id,system_id,code,name,permission_type,function_id,menu_eligible,enabled,version,created_at) VALUES(?,?,?,?,?,?,?,?,1,?)",ps->{
                ps.setString(1,p.id());ps.setString(2,system);ps.setString(3,p.code());ps.setString(4,p.name());ps.setString(5,p.permissionType());ps.setString(6,p.functionId());ps.setBoolean(7,p.menuEligible());ps.setBoolean(8,p.enabled());ps.setTimestamp(9,Timestamp.from(context.now()),utc());});
            else if(!old.equals(p))jdbc.update("UPDATE permission SET name=?,menu_eligible=?,enabled=?,version=? WHERE id=? AND system_id=?",p.name(),p.menuEligible(),p.enabled(),p.version(),p.id(),system);
        }
        for(var n:after.nodes().values())if(!n.equals(before.nodes().get(n.id())))jdbc.update("UPDATE directory_node SET name=?,parent_id=?,sort_order=?,enabled=?,visibility_permission_id=?,version=? WHERE id=? AND system_id=?",n.name(),n.parentId(),n.sortOrder(),n.enabled(),n.visibilityPermissionId(),n.version(),n.id(),system);
        if(jdbc.update("UPDATE business_system SET catalog_version=catalog_version+1,row_version=row_version+1 WHERE id=? AND catalog_version=? AND row_version<?",system,expectedCatalog,ManagementInput.MAX_VERSION)!=1)throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
    }
    public void system(WriteContext<?> context,SystemRepository.View old,String name,String description,boolean enabled){
        context.requireActive(source);
        if(jdbc.update("UPDATE business_system SET name=?,description=?,enabled=?,version=version+1,row_version=row_version+1 WHERE id=? AND version=? AND row_version<?",name,description,enabled,old.id(),old.version(),ManagementInput.MAX_VERSION)!=1)throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
    }
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
}
