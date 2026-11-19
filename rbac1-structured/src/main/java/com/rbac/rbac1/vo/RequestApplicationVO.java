package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 可申请权限中的一个应用。
 *
 * @param id 应用 ID
 * @param name 应用名称
 * @param code 应用标识
 * @param groups 按菜单分组的可申请权限
 */
public record RequestApplicationVO(long id, String name, String code, List<RequestGroupVO> groups) {}
