package com.rbac.rbac1ddd.domain.api;

import com.rbac.rbac1ddd.domain.shared.DomainPage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 接口登记仓储接口：按接口聚合存取。 */
public interface ApiRepository {

  /**
   * 读取指定应用下的接口登记。
   *
   * @param applicationId 应用 ID
   * @param id 接口 ID
   * @return 接口不存在或不属于该应用时为空
   */
  Optional<Api> findInApplication(long applicationId, long id);

  /**
   * 按 ID 批量读取接口登记，不存在的 ID 被忽略。
   *
   * @param ids 接口 ID
   * @return 按 ID 升序排列的已登记接口
   */
  List<Api> findAllById(Collection<Long> ids);

  /**
   * 按关键字分页查询应用下的接口，关键字匹配 operationId 或路由，按 ID 升序。
   *
   * @param applicationId 应用 ID
   * @param keyword 关键字，空字符串表示不过滤
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页的接口和总数
   */
  DomainPage<Api> search(long applicationId, String keyword, int page, int pageSize);

  /**
   * 保存接口登记：新登记插入并回填 ID，已有登记更新资料。
   *
   * @param api 接口聚合
   * @throws com.rbac.rbac1ddd.domain.shared.DomainException operationId 或路由在应用内重复时为 DUPLICATE
   */
  void save(Api api);

  /**
   * 删除接口登记。调用前应先解除全部权限关联。
   *
   * @param api 接口聚合
   */
  void delete(Api api);
}
