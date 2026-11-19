package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 完整部门树的响应体。
 *
 * @param departments 顶级部门及其下级
 */
public record DepartmentTreeVO(List<DepartmentNodeVO> departments) {}
