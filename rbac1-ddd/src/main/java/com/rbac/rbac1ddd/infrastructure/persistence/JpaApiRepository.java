package com.rbac.rbac1ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.domain.api.Api;
import com.rbac.rbac1ddd.domain.api.ApiRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.ApiDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.ApiPo;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * 接口登记仓储的 JPA 实现：一个接口聚合对应 sys_api 的一行。
 *
 * <p>接口与权限的关联由权限仓储维护，这里只存取接口本身。
 */
@Repository
@RequiredArgsConstructor
class JpaApiRepository implements ApiRepository {

  /** 应用内 operationId 唯一约束名，与 V1__init_rbac1.sql 一致。 */
  private static final String OPERATION_CONSTRAINT = "uq_api_operation";

  /** 应用内请求方法与路由组合唯一约束名，与 V1__init_rbac1.sql 一致。 */
  private static final String ROUTE_CONSTRAINT = "uq_api_route";

  private final ApiDao apis;

  /** 读取指定应用下的接口登记。 */
  @Override
  public Optional<Api> findInApplication(long applicationId, long id) {
    return apis.findByIdAndApplicationId(id, applicationId).map(JpaApiRepository::toDomain);
  }

  /** 按 ID 批量读取接口登记，结果按 ID 升序。 */
  @Override
  public List<Api> findAllById(Collection<Long> ids) {
    return apis.findAllById(ids).stream()
        .sorted(Comparator.comparing(ApiPo::getId))
        .map(JpaApiRepository::toDomain)
        .toList();
  }

  /** 按关键字分页查询应用下的接口，页码从 1 开始。 */
  @Override
  public DomainPage<Api> search(long applicationId, String keyword, int page, int pageSize) {
    Page<ApiPo> found = apis.search(applicationId, keyword, PageRequest.of(page - 1, pageSize));
    return new DomainPage<>(
        found.getContent().stream().map(JpaApiRepository::toDomain).toList(),
        found.getTotalElements());
  }

  /**
   * 用 CloneFactory 拷贝后保存接口，新登记回填 ID；operationId 或路由重复时就地翻译为业务错误。
   *
   * <p>修改资料时 UPDATE 在事务提交时才执行，因此用 saveAndFlush 在当前方法内触发约束检查。
   */
  @Override
  public void save(Api api) {
    ApiPo po = api.getId() == null ? new ApiPo() : apis.findById(api.getId()).orElseThrow();
    CloneFactory.copy(po, api);
    try {
      apis.saveAndFlush(po);
    } catch (DataIntegrityViolationException error) {
      if (UniqueConstraints.violated(error, OPERATION_CONSTRAINT)
          || UniqueConstraints.violated(error, ROUTE_CONSTRAINT)) {
        throw new DomainException(
            DomainException.Reason.DUPLICATE, UniqueConstraints.GENERIC_DUPLICATE);
      }
      throw error;
    }
    if (api.getId() == null) {
      api.assignId(po.getId());
    }
  }

  /** 删除接口登记行。 */
  @Override
  public void delete(Api api) {
    apis.deleteById(api.getId());
  }

  /** 持久化对象 → 接口聚合。 */
  private static Api toDomain(ApiPo po) {
    return new Api(
        po.getId(), po.getApplicationId(), po.getOperationId(), po.getHttpMethod(), po.getPath());
  }
}
