package com.adgn.backend.core.audit;

import java.util.List;
import java.util.UUID;

/** Internal, already sanitized event. Visibility/access policies belong to the calling domain. */
public record AuditRecord(String eventType, String targetId, UUID changeRequestId, UUID originalSubmitterId,
                          Scope scope, String result, String correlationId, String reason,
                          String snapshotJson, List<Subject> subjects) {
    public enum ActorKind { ACCOUNT, SERVICE, DEPLOYMENT }
    public enum Visibility { GLOBAL, SCOPED }
    public record Actor(ActorKind kind, String id) { }
    public record Scope(Visibility visibility, UUID companyId, UUID departmentId, UUID systemId) { }
    public record Subject(String type, String id) { }
    public AuditRecord { subjects = List.copyOf(subjects); }
}
