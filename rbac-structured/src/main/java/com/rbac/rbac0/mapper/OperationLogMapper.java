package com.rbac.rbac0.mapper;

import java.util.List;

import com.rbac.rbac0.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OperationLogMapper extends JpaRepository<OperationLog, Long> {

    String MATCH_LOG_FIELDS = """
            (:keyword = '' or locate(:keyword, entry.actorName) > 0
                or locate(:keyword, entry.action) > 0 or locate(:keyword, entry.detail) > 0)
            """;

    @Query("select entry from OperationLog entry where (" + MATCH_LOG_FIELDS + """
            or exists (
                select 1 from OperationLogTarget target
                where target.operationLogId = entry.id and locate(:keyword, target.targetName) > 0
            ))
            order by entry.occurredAt desc, entry.id desc
            """)
    Page<OperationLog> searchAll(String keyword, Pageable pageable);

    @Query("""
            select entry from OperationLog entry
            where entry.scope = 'USER_SCOPE' and exists (
                select 1 from OperationLogTarget target
                where target.operationLogId = entry.id and target.departmentIdAtEvent in :departmentIds
                  and (
            """ + MATCH_LOG_FIELDS + """
                       or locate(:keyword, target.targetName) > 0)
            )
            order by entry.occurredAt desc, entry.id desc
            """)
    Page<OperationLog> searchByDepartmentScope(List<Long> departmentIds, String keyword, Pageable pageable);
}
