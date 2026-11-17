package com.rbac.rbac1ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.ApplicationDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.ApplicationPo;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** 业务应用仓储的 JPA 实现：一个应用对应 sys_application 的一行。 */
@Repository
@RequiredArgsConstructor
class JpaBusinessApplicationRepository implements BusinessApplicationRepository {

  /** 应用名称唯一约束名，与 V1__init_rbac1.sql 一致。 */
  private static final String NAME_CONSTRAINT = "uq_application_name";

  /** 应用编码唯一约束名，与 V1__init_rbac1.sql 一致。 */
  private static final String CODE_CONSTRAINT = "uq_application_code";

  private final ApplicationDao applications;

  /** 按应用编码查找应用。 */
  @Override
  public Optional<BusinessApplication> findByCode(String code) {
    return applications.findByCode(code).map(JpaBusinessApplicationRepository::toDomain);
  }

  /** 按 ID 查找应用。 */
  @Override
  public Optional<BusinessApplication> findById(long id) {
    return applications.findById(id).map(JpaBusinessApplicationRepository::toDomain);
  }

  /** 按 ID 升序读取全部应用。 */
  @Override
  public List<BusinessApplication> findAll() {
    return applications.findAll(Sort.by("id")).stream()
        .map(JpaBusinessApplicationRepository::toDomain)
        .toList();
  }

  /** 按 ID 升序分页查询应用，页码从 1 开始。 */
  @Override
  public DomainPage<BusinessApplication> findPage(int page, int pageSize) {
    Page<ApplicationPo> found =
        applications.findAll(PageRequest.of(page - 1, pageSize, Sort.by("id")));
    return new DomainPage<>(
        found.getContent().stream().map(JpaBusinessApplicationRepository::toDomain).toList(),
        found.getTotalElements());
  }

  /**
   * 用 CloneFactory 拷贝后保存应用，新应用回填 ID；名称或编码重复时就地翻译为业务错误。
   *
   * <p>改名时 UPDATE 在事务提交时才执行，因此用 saveAndFlush 在当前方法内触发约束检查。
   */
  @Override
  public void save(BusinessApplication application) {
    ApplicationPo po =
        application.getId() == null
            ? new ApplicationPo()
            : applications.findById(application.getId()).orElseThrow();
    CloneFactory.copy(po, application);
    try {
      applications.saveAndFlush(po);
    } catch (DataIntegrityViolationException error) {
      if (UniqueConstraints.violated(error, NAME_CONSTRAINT)) {
        throw new DomainException(DomainException.Reason.DUPLICATE, "该应用名称已存在。");
      }
      if (UniqueConstraints.violated(error, CODE_CONSTRAINT)) {
        throw new DomainException(
            DomainException.Reason.DUPLICATE, UniqueConstraints.GENERIC_DUPLICATE);
      }
      throw error;
    }
    if (application.getId() == null) {
      application.assignId(po.getId());
    }
  }

  /** 持久化对象 → 应用聚合。 */
  private static BusinessApplication toDomain(ApplicationPo po) {
    return new BusinessApplication(po.getId(), po.getCode(), po.getName());
  }
}
