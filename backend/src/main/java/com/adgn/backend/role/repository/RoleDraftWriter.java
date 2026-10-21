package com.adgn.backend.role.repository;

import com.adgn.backend.core.transaction.WriteContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;

/** C 的编排器通过本角色端口创建或清理未生效角色，不改写有效配置。 */
public final class RoleDraftWriter {
    private final DataSource source;
    private final JdbcTemplate jdbc;
    public RoleDraftWriter(DataSource source){this.source=source;this.jdbc=new JdbcTemplate(source);}
    public String create(WriteContext<?> c,String name,String description,String company,String department,String system){
        c.requireActive(source);String id=c.newId().toString();
        jdbc.update("INSERT INTO business_role(id,code,name,description,company_id,department_id,system_id,created_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",id,"ROLE_"+id.replace("-",""),name,description,company,department,system);return id;
    }
    public void deleteUnreferenced(WriteContext<?> c,String id){
        c.requireActive(source);
        // 全部即时 FK 的引用均须排除；历史从不随草稿删除。
        jdbc.update("DELETE r FROM business_role r WHERE r.id=? AND r.state='UNCONFIGURED' AND r.configuration_version=0 "
                +"AND NOT EXISTS(SELECT 1 FROM role_permission p WHERE p.role_id=r.id) "
                +"AND NOT EXISTS(SELECT 1 FROM role_configuration_history h WHERE h.role_id=r.id) "
                +"AND NOT EXISTS(SELECT 1 FROM grant_baseline b WHERE b.role_id=r.id) "
                +"AND NOT EXISTS(SELECT 1 FROM change_target t WHERE t.role_id=r.id) "
                +"AND NOT EXISTS(SELECT 1 FROM role_change_claim cl WHERE cl.role_id=r.id)",id);
    }
}
