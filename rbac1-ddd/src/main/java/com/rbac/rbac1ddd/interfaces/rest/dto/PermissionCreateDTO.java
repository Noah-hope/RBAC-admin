package com.rbac.rbac1ddd.interfaces.rest.dto;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;

import com.rbac.rbac1ddd.interfaces.rest.Formats;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * 登记菜单或按钮权限的请求体（PermissionCreate）。
 *
 * @param name 权限名称，必填且不超过 24 个字符
 * @param code 权限标识，支持「资源:操作」及课程短码，不超过 64 个字符
 * @param type 权限类型：MENU 或 BUTTON
 * @param parentId 按钮所属菜单 ID，菜单省略或为 null
 * @param apiIds 关联的接口 ID，不能重复，可以省略
 */
public record PermissionCreateDTO(
    @NotEmpty
    @CodePointLength(max = 24)
    String name,
    @NotNull
    @CodePointLength(max = 64)
    @Pattern(regexp = Formats.PERMISSION_CODE)
    String code,
    @NotNull
    @Pattern(regexp = "MENU|BUTTON")
    String type,
    @Positive
    @Max(MAX_ID)
    Long parentId,
    @UniqueElements
    List<@NotNull @Positive Long> apiIds) {}
