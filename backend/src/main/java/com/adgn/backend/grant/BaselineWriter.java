package com.adgn.backend.grant;

import com.adgn.backend.core.transaction.WriteContext;
import javax.sql.DataSource;

/** M07 drafts and lifecycle orchestration call this only inside their guarded transaction. */
public final class BaselineWriter {
    private final GrantMutationSupport db;
    public BaselineWriter(DataSource source,GrantReader reader){db=new GrantMutationSupport(source,reader);}
    public GrantReader.Source source(WriteContext<?> c,GrantReader.SourceKey key){return db.checked(c,()->{
        GrantMutationSupport.key(key);var existing=db.reader.source(key);
        if(existing.id()!=null)return existing;
        db.jdbc.update("INSERT INTO authorization_source(id,employee_id,company_id,department_id,system_id) VALUES(?,?,?,?,?)",c.newId().toString(),key.employeeId(),key.companyId(),key.departmentId(),key.systemId());
        return db.reader.source(key);
    });}
    public GrantReader.Baseline baseline(WriteContext<?> c,GrantReader.Key key){return db.checked(c,()->{
        GrantMutationSupport.key(key);var existing=db.reader.baseline(key);
        if(existing.id()!=null)return existing;
        var source=source(c,existing.source().key());
        db.jdbc.update("INSERT INTO grant_baseline(id,source_id,role_id,company_id,system_id) VALUES(?,?,?,?,?)",c.newId().toString(),source.id(),key.roleId(),key.sourceCompanyId(),existing.role().systemId());
        return db.reader.baseline(key);
    });}
}
