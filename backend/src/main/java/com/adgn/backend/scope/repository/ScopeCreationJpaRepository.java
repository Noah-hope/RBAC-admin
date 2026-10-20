package com.adgn.backend.scope.repository;

import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.*;

/** Mandatory existing transaction; no controller can open an unguarded save transaction. */
@Transactional(propagation=Propagation.MANDATORY)
public interface ScopeCreationJpaRepository extends Repository<ScopeCreationEntity,String> {
    ScopeCreationEntity saveAndFlush(ScopeCreationEntity scope);
    boolean existsById(String id);
}
