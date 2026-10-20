package com.adgn.backend.imports;

import com.adgn.backend.query.preview.ImpactPolicy;
import java.time.Instant;
import java.util.*;

/** Frozen precheck values; candidate IDs and execution instructions stay internal. */
public record ImportPlan(List<Change> changes,List<Issue> issues,Counts counts,Effects effects) {
    public record FieldMapping(String sourceColumn,String targetField) { }
    public record OrganizationMapping(String sourcePath,String companyId,String departmentId,String companyCode,String departmentCode) { }
    public record Input(String fileId,String sourceSystem,String sourceBatchId,String importType,String worksheet,
                        List<FieldMapping> fieldMappings,List<OrganizationMapping> organizationMappings,String initialEmploymentState,String reason) { }
    public record Counts(long created,long updated,long unchanged,long endedEmployments,long disabledEmployees,long restoredEmployees,long invalidatedGrants,long invalidatedAppointments) { }
    public record Issue(int rowNumber,String field,String severity,String code,String message) { }
    public record ValueChange(String field,Object before,Object after) { }
    public record Change(int rowNumber,int planNo,String action,String targetId,String stableCode,Long expectedVersion,
                         String summary,String companyCode,String departmentCode,String positionCode,List<ValueChange> fieldChanges,
                         Map<String,Object> normalizedValues,Map<String,Object> dependencies,boolean newObject) { }
    public record Effects(Set<ImpactPolicy.Source> closedSources,Set<String> endedGrants,Set<String> inactiveEmployees,Set<String> revokedAppointments) { }
    public record Batch(String id,Input input,long version,String contentDigest,String state,Counts plannedCounts,Counts actualCounts,
                        int blockingIssueCount,int warningCount,String failureCode,Instant createdAt) { }
    public record Page<T>(List<T> items,long page,int pageSize,long total) { }
}
