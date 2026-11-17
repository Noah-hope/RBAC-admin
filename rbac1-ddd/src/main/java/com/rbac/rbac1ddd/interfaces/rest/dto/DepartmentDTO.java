package com.rbac.rbac1ddd.interfaces.rest.dto;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * 新增部门的请求体（DepartmentInput）。
 *
 * <p>parentId 必须出现在请求中，显式传 null 表示新增顶级部门；省略时视为请求不合法，与 rbac1-structured 相同。
 *
 * @param name 部门名称，必填且不超过 24 个字符
 * @param parentId 上级部门 ID，null 表示顶级部门
 */
public record DepartmentDTO(
    @NotEmpty
    @CodePointLength(max = 24)
    String name,
    @JsonProperty(required = true)
    @Positive
    @Max(MAX_ID)
    Long parentId) {}
