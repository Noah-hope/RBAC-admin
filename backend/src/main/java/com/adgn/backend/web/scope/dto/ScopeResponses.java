package com.adgn.backend.web.scope.dto;

import java.time.Instant;
import java.util.List;

public final class ScopeResponses {
    private ScopeResponses() { }
    public record Page<T>(List<T> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    public record Scope(String id,String companyId,String departmentId,List<String> systemIds,long version,Instant createdAt){public Scope{systemIds=List.copyOf(systemIds);}}
    public record Appointment(String id,String scopeId,String accountId,String employeeId,String status,long version,long scopeVersion,String companyId,String departmentId,List<String> systemIds){public Appointment{systemIds=List.copyOf(systemIds);}}
}
