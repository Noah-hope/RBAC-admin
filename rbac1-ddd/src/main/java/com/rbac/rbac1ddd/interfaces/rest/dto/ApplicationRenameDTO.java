package com.rbac.rbac1ddd.interfaces.rest.dto;

import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * 修改应用名称的请求体（ApplicationInput）。
 *
 * @param name 新名称，必填且不超过 24 个字符
 */
public record ApplicationRenameDTO(
    @NotEmpty
    @CodePointLength(max = 24)
    String name) {}
