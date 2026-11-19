package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.Api;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_api 表的数据访问接口：按应用读取已登记的接口，供接口管理和接口权限检查使用。 */
public interface ApiMapper extends JpaRepository<Api, Long> {

  /**
   * 按 ID 查找指定应用下的接口；接口不属于该应用时视为不存在，防止跨应用修改。
   *
   * @param id 接口 ID
   * @param applicationId 所属应用 ID
   * @return 属于该应用的接口
   */
  Optional<Api> findByIdAndApplicationId(long id, long applicationId);

  /**
   * 按应用内唯一的操作标识查找接口，用于按 operationId 检查接口权限。
   *
   * @param applicationId 应用 ID
   * @param operationId 操作标识
   * @return 匹配的接口
   */
  Optional<Api> findByApplicationIdAndOperationId(long applicationId, String operationId);

  /**
   * 按应用内唯一的请求方法和路由模板查找接口，用于按路由检查接口权限。
   *
   * @param applicationId 应用 ID
   * @param httpMethod 请求方法
   * @param path 路由模板
   * @return 匹配的接口
   */
  Optional<Api> findByApplicationIdAndHttpMethodAndPath(
      long applicationId, String httpMethod, String path);

  /**
   * 分页查询应用下的接口，关键字为空串时不过滤，否则匹配 operationId 或路由模板的子串，按 ID 升序。
   *
   * @param applicationId 应用 ID
   * @param keyword 关键字，空串表示不过滤
   * @param pageable 分页参数
   * @return 当前页的接口
   */
  @Query(
      "select a from Api a where a.applicationId=:applicationId and (:keyword='' or"
          + " locate(:keyword,a.operationId)>0 or locate(:keyword,a.path)>0) order by a.id")
  Page<Api> search(long applicationId, String keyword, Pageable pageable);
}
