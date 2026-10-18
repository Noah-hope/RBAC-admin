package com.adgn.backend.core.idempotency;
import java.util.List;
import java.util.UUID;
public record AuthorizationBasis(String operationId, Policy policy, List<Target> targets) {
    public AuthorizationBasis { targets=targets==null?null:List.copyOf(targets); }
    public enum Policy { GLOBAL_ONLY, SCOPED, PRINCIPAL_ONLY }
    public record Target(UUID companyId, UUID departmentId, UUID systemId) { }
}
