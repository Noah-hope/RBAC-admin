package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.Department;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_department 表的数据访问接口：部门树由 Service 在内存中按上级 ID 组装。 */
public interface DepartmentMapper extends JpaRepository<Department, Long> {

  /**
   * 按 ID 升序读取全部部门，保证组装出的部门树和管理范围顺序稳定。
   *
   * @return 全部部门
   */
  List<Department> findAllByOrderByIdAsc();

  /** 只查询当前层的部门，关键词可匹配名称或对外部门代码。 */
  @Query(
      """
      select department from Department department
      where ((:parentId is null and department.parentId is null) or department.parentId = :parentId)
        and (:keyword = '' or locate(:keyword, department.name) > 0
             or locate(:keyword, cast(department.id as String)) > 0)
      order by department.id
      """)
  Page<Department> findDirectoryLevel(Long parentId, String keyword, Pageable pageable);

  /** 一次查出本页部门中实际有下级部门的 ID。 */
  @Query("select distinct department.parentId from Department department where department.parentId in :ids")
  List<Long> findDirectoryParentIds(Collection<Long> ids);
}
