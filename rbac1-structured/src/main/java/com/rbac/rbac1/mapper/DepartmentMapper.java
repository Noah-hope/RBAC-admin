package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_department 表的数据访问接口：部门树由 Service 在内存中按上级 ID 组装。 */
public interface DepartmentMapper extends JpaRepository<Department, Long> {

  /**
   * 按 ID 升序读取全部部门，保证组装出的部门树和管理范围顺序稳定。
   *
   * @return 全部部门
   */
  List<Department> findAllByOrderByIdAsc();
}
