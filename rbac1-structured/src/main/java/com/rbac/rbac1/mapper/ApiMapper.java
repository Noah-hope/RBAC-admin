package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.Api;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ApiMapper extends JpaRepository<Api, Long> {
  Optional<Api> findByIdAndApplicationId(long id, long applicationId);

  Optional<Api> findByApplicationIdAndOperationId(long applicationId, String operationId);

  Optional<Api> findByApplicationIdAndHttpMethodAndPath(
      long applicationId, String httpMethod, String path);

  @Query(
      "select a from Api a where a.applicationId=:applicationId and (:keyword='' or"
          + " locate(:keyword,a.operationId)>0 or locate(:keyword,a.path)>0) order by a.id")
  Page<Api> search(long applicationId, String keyword, Pageable pageable);
}
