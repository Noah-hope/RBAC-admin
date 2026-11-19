package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 权限目录的响应体：全部应用及其权限。
 *
 * @param applications 各应用及其权限
 */
public record PermissionCatalogVO(List<CatalogApplicationVO> applications) {}
