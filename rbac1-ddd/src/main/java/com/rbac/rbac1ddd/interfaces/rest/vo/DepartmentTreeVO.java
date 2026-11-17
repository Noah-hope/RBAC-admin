package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.department.DepartmentNodeResult;
import java.util.List;

/**
 * 部门树响应体（DepartmentTree）。
 *
 * @param departments 根节点：总管理员为全部顶级部门，部门管理员为本部门
 */
public record DepartmentTreeVO(List<DepartmentNodeVO> departments) {

  /** 由应用层的根节点列表转换。 */
  public static DepartmentTreeVO from(List<DepartmentNodeResult> roots) {
    return new DepartmentTreeVO(roots.stream().map(DepartmentNodeVO::from).toList());
  }
}
