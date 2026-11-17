package com.rbac.rbac1ddd.domain.department;

import java.util.Optional;

/** 部门仓储接口：按部门聚合存取，并能一次加载整棵部门树。 */
public interface DepartmentRepository {

  /**
   * 按 ID 读取部门聚合。
   *
   * @param id 部门 ID
   * @return 部门不存在时为空
   */
  Optional<Department> findById(long id);

  /**
   * 读取全部部门并组装成部门树。
   *
   * <p>管理范围需要计算"本部门及全部下级部门"，因此一次加载整棵树而不是逐级查询。
   *
   * @return 包含全部部门上下级关系的部门树
   */
  DepartmentTree loadTree();

  /**
   * 保存部门聚合：新部门插入并回填 ID，已有部门更新名称和上级。
   *
   * @param department 部门聚合
   */
  void save(Department department);
}
