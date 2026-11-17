package com.rbac.rbac1ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.domain.department.Department;
import com.rbac.rbac1ddd.domain.department.DepartmentRepository;
import com.rbac.rbac1ddd.domain.department.DepartmentTree;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.DepartmentDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.DepartmentPo;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 部门仓储的 JPA 实现：一个部门聚合对应 sys_department 的一行。 */
@Repository
@RequiredArgsConstructor
class JpaDepartmentRepository implements DepartmentRepository {
  private final DepartmentDao departments;

  /** 按 ID 读取部门。 */
  @Override
  public Optional<Department> findById(long id) {
    return departments.findById(id).map(JpaDepartmentRepository::toDomain);
  }

  /** 一次读取全部部门构建部门树。 */
  @Override
  public DepartmentTree loadTree() {
    return new DepartmentTree(
        departments.findAll().stream().map(JpaDepartmentRepository::toDomain).toList());
  }

  /** 用 CloneFactory 把部门拷贝到持久化对象后保存；新部门回填数据库生成的 ID。 */
  @Override
  public void save(Department department) {
    DepartmentPo po =
        department.getId() == null
            ? new DepartmentPo()
            : departments.findById(department.getId()).orElseThrow();
    DepartmentPo saved = departments.save(CloneFactory.copy(po, department));
    if (department.getId() == null) {
      department.assignId(saved.getId());
    }
  }

  /** 持久化对象 → 部门聚合。 */
  private static Department toDomain(DepartmentPo po) {
    return new Department(po.getId(), po.getName(), po.getParentId());
  }
}
