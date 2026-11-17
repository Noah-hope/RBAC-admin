package com.rbac.rbac1ddd.interfaces.rest.dto;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * 保存单个用户业务角色的请求体。
 *
 * @param roleIds 用户最终拥有的业务角色 ID 列表，[] 表示撤销全部业务角色
 */
public record ReplaceUserRolesDTO(
    @NotNull
    @UniqueElements
    List<@NotNull @Positive @Max(MAX_ID) Long> roleIds) {}
