package com.rbac.rbac1.dto;

import static com.rbac.rbac1.dto.Formats.MAX_ID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * 整体替换用户业务角色的请求体。
 *
 * @param roleIds 最终的业务角色 ID 列表，[] 表示清空，元素不重复
 */
public record ReplaceUserRolesDTO(
    @NotNull
    @UniqueElements
    List<@NotNull @Positive @Max(MAX_ID) Long> roleIds) {}
