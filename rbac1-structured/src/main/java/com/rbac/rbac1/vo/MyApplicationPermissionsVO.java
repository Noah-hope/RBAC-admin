package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 当前用户在一个应用内的有效权限。
 *
 * @param applicationId 应用 ID
 * @param applicationName 应用名称
 * @param applicationCode 应用标识
 * @param menus 拥有的菜单及其按钮
 */
public record MyApplicationPermissionsVO(
    long applicationId, String applicationName, String applicationCode, List<MyMenuVO> menus) {}
