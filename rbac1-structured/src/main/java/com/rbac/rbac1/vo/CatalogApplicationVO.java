package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 权限目录中的一个应用。
 *
 * @param id 应用 ID
 * @param name 应用名称
 * @param code 应用标识
 * @param permissions 该应用的全部权限
 */
public record CatalogApplicationVO(
    long id, String name, String code, List<PermissionSummaryVO> permissions) {}
