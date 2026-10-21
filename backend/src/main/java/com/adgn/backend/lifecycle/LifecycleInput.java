package com.adgn.backend.lifecycle;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementInput;
import com.adgn.backend.scope.service.ScopeCreationInput;

import java.util.List;
import java.util.Set;

public final class LifecycleInput {
    private LifecycleInput() { }
    public record EmployeeState(Long expectedVersion,String status,String reason) {
        public EmployeeState normalized() {
            ManagementInput.version(expectedVersion);
            if(status==null||!Set.of("ACTIVE","INACTIVE").contains(status))throw IdentityFailure.invalid();
            return new EmployeeState(expectedVersion,status,ManagementInput.text(reason,1,500));
        }
    }
    public record ScopeEdit(Long expectedVersion,String companyId,String departmentId,List<String> systemIds,List<String> accountIds,String reason) {
        public ScopeEdit normalized() {
            ManagementInput.version(expectedVersion);
            var in=ScopeCreationInput.normalized(new ScopeCreationInput(companyId,departmentId,systemIds,accountIds,reason));
            return new ScopeEdit(expectedVersion,in.companyId(),in.departmentId(),in.systemIds(),in.accountIds(),in.reason());
        }
    }
    public record EmployeeSave(EmployeeState input,String previewId) { }
    public record ScopeSave(ScopeEdit input,String previewId) { }
}
