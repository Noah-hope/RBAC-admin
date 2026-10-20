package com.adgn.backend.change;

import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.identity.security.IdentityFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;

/** 调用方持有同一 guard；先完整检查新增占用，再原子替换。 */
public final class ClaimService {
    private final DataSource source;private final JdbcTemplate jdbc;
    public ClaimService(DataSource source){this.source=source;this.jdbc=new JdbcTemplate(source);}
    public void replace(WriteContext<?> c,String request,String kind,List<DraftRepository.Target> targets){
        c.requireActive(source);
        for(var t:targets){
            if(kind.equals("ROLE_CONFIGURATION"))check("role_change_claim","role_id",t.role(),request,"CHANNEL_OCCUPIED");
            else if(t.needsChange())check("relation_change_claim","baseline_id",t.baseline(),request,"RELATION_OCCUPIED");
        }
        release(c,request);
        for(var t:targets)if(kind.equals("ROLE_CONFIGURATION"))jdbc.update("INSERT INTO role_change_claim(role_id,request_id) VALUES(?,?)",t.role(),request);
        else if(t.needsChange())jdbc.update("INSERT INTO relation_change_claim(baseline_id,request_id) VALUES(?,?)",t.baseline(),request);
    }
    public void release(WriteContext<?> c,String request){c.requireActive(source);jdbc.update("DELETE FROM role_change_claim WHERE request_id=?",request);jdbc.update("DELETE FROM relation_change_claim WHERE request_id=?",request);}
    private void check(String table,String key,String id,String request,String code){var owners=jdbc.queryForList("SELECT request_id FROM "+table+" WHERE "+key+"=?",String.class,id);if(!owners.isEmpty()&&!owners.getFirst().equals(request))throw new IdentityFailure(409,code);}
}
