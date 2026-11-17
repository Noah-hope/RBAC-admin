package com.rbac.rbac1ddd.interfaces.rest.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * 新增应用的请求体（ApplicationCreate）。
 *
 * @param name 应用名称，必填且不超过 24 个字符
 * @param code 应用编码，小写字母开头，只含小写字母、数字、下划线和连字符，不超过 64 个字符；省略时由系统生成
 */
public record ApplicationCreateDTO(
    @NotEmpty
    @CodePointLength(max = 24)
    String name,
    @Pattern(regexp = "[a-z][a-z0-9_-]*")
    @CodePointLength(max = 64)
    String code) {}
