package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.department.DepartmentNodeResult;
import java.util.List;

/**
 * 部门树节点（DepartmentNode）。
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param parentId 上级部门 ID，顶级部门为 null
 * @param children 直接下级部门，按 ID 升序
 */
public record DepartmentNodeVO(long id, String name, Long parentId, List<DepartmentNodeVO> children) {

  /** 由应用层结果递归转换。 */
  public static DepartmentNodeVO from(DepartmentNodeResult result) {
    return new DepartmentNodeVO(
        result.id(),
        result.name(),
        result.parentId(),
        result.children().stream().map(DepartmentNodeVO::from).toList());
  }
}
