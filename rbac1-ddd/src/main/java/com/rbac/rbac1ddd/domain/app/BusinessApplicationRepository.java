package com.rbac.rbac1ddd.domain.app;

import com.rbac.rbac1ddd.domain.shared.DomainPage;
import java.util.List;
import java.util.Optional;

/** 业务应用仓储接口：按应用聚合存取。 */
public interface BusinessApplicationRepository {

  /**
   * 按应用编码查找已登记的应用。
   *
   * @param code 应用编码
   * @return 未登记时为空
   */
  Optional<BusinessApplication> findByCode(String code);

  /**
   * 按 ID 查找应用。
   *
   * @param id 应用 ID
   * @return 应用不存在时为空
   */
  Optional<BusinessApplication> findById(long id);

  /**
   * 按 ID 升序读取全部已登记应用，用于按应用汇总权限目录和「我的权限」。
   *
   * @return 全部应用
   */
  List<BusinessApplication> findAll();

  /**
   * 按 ID 升序分页查询应用。
   *
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页的应用和总数
   */
  DomainPage<BusinessApplication> findPage(int page, int pageSize);

  /**
   * 保存应用：新应用插入并回填 ID，已有应用更新名称。
   *
   * @param application 应用聚合
   * @throws com.rbac.rbac1ddd.domain.shared.DomainException 名称或编码已被其他应用使用时为 DUPLICATE
   */
  void save(BusinessApplication application);
}
