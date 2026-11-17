package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.ApiPo;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_api 表的 Spring Data 访问接口。 */
public interface ApiDao extends JpaRepository<ApiPo, Long> {

  /** 按应用编码、HTTP 方法和路径模板查找登记的接口路由。 */
  @Query(
      """
      select api from ApiPo api, ApplicationPo app
      where api.applicationId = app.id and app.code = :applicationCode
        and api.httpMethod = :httpMethod and api.path = :path
      """)
  Optional<ApiPo> findRoute(String applicationCode, String httpMethod, String path);

  /** 按应用编码和操作标识查找登记的接口。 */
  @Query(
      """
      select api from ApiPo api, ApplicationPo app
      where api.applicationId = app.id and app.code = :applicationCode
        and api.operationId = :operationId
      """)
  Optional<ApiPo> findOperation(String applicationCode, String operationId);

  /** 查询指定应用下的接口登记。 */
  Optional<ApiPo> findByIdAndApplicationId(long id, long applicationId);

  /** 按关键字分页查询应用下的接口，关键字匹配 operationId 或路径，空字符串不过滤，按 ID 升序。 */
  @Query(
      """
      select api from ApiPo api
      where api.applicationId = :applicationId
        and (:keyword = '' or locate(:keyword, api.operationId) > 0 or locate(:keyword, api.path) > 0)
      order by api.id
      """)
  Page<ApiPo> search(long applicationId, String keyword, Pageable pageable);
}
