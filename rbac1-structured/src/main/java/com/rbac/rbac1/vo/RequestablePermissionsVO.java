package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 当前用户可申请权限的响应体。
 *
 * @param applications 按应用分组的可申请权限
 */
public record RequestablePermissionsVO(List<RequestApplicationVO> applications) {}
