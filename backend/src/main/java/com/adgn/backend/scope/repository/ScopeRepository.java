package com.adgn.backend.scope.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Uses the caller's RR/guarded RC transaction; exposes no write methods. */
@Transactional(propagation=Propagation.MANDATORY,readOnly=true)
public interface ScopeRepository extends Repository<ScopeEntity,String> {
    Optional<ScopeEntity> findById(String id);
    @Query("select s from ScopeReadEntity s where (:company is null or s.companyId=:company) and (:department is null or s.departmentId=:department) order by s.id")
    List<ScopeEntity> list(@Param("company")String company,@Param("department")String department,Pageable page);
    @Query("select count(s) from ScopeReadEntity s where (:company is null or s.companyId=:company) and (:department is null or s.departmentId=:department)")
    long count(@Param("company")String company,@Param("department")String department);
    @Query(value="SELECT system_id FROM scope_system WHERE scope_id=:scope ORDER BY system_id",nativeQuery=true)
    List<String> systems(@Param("scope")String scope);
}
