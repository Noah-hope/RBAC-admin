package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 一个应用的菜单—按钮权限树。
 *
 * @param applicationId 应用 ID
 * @param applicationName 应用名称
 * @param menus 菜单及其按钮
 */
public record ApplicationPermissionsVO(
    long applicationId, String applicationName, List<MenuNodeVO> menus) {}
