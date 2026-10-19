package com.adgn.backend.scope.service;

import java.util.Set;

/** Pure coverage of one already-resolved context; callers must requalify inside their transaction. */
public final class ScopeCoveragePolicy {
    public boolean covers(ManagementContext context,String companyId,String departmentId,Set<String> systemIds){
        if(context==null || companyId==null || systemIds==null)return false;
        return context.global() || (companyId.equals(context.companyId()) && departmentId!=null
            && departmentId.equals(context.departmentId()) && context.systemIds().containsAll(systemIds));
    }
}
