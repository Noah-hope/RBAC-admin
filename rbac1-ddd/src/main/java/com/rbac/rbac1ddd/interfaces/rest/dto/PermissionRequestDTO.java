package com.rbac.rbac1ddd.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 提交权限申请的请求体（PermissionRequestInput），申请人由令牌确定。
 *
 * @param permissionId 所申请的菜单或按钮权限 ID
 * @param reason 申请理由，不能为空白，不超过 500 个字符
 */
public record PermissionRequestDTO(
    @NotNull
    @Positive
    Long permissionId,
    @NotBlank
    @Size(max = 500)
    String reason) {}
