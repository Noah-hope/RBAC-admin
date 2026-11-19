package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.DepartmentPo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_department 表的 Spring Data 访问接口。 */
public interface DepartmentDao extends JpaRepository<DepartmentPo, Long> {

  /** 只查询当前层的部门，关键词可匹配名称或对外部门代码。 */
  @Query(
      """
      select department from DepartmentPo department
      where ((:parentId is null and department.parentId is null) or department.parentId = :parentId)
        and (:keyword = '' or locate(:keyword, department.name) > 0
             or locate(:keyword, cast(department.id as String)) > 0)
      order by department.id
      """)
  Page<DepartmentPo> findDirectoryLevel(Long parentId, String keyword, Pageable pageable);

  /** 一次查出本页部门中实际有下级部门的 ID。 */
  @Query("select distinct department.parentId from DepartmentPo department where department.parentId in :ids")
  List<Long> findDirectoryParentIds(Collection<Long> ids);
}
