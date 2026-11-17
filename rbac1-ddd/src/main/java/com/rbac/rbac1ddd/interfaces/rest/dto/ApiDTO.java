package com.rbac.rbac1ddd.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 登记接口的请求体（ApiInput）。
 *
 * @param operationId 业务系统 API 文档中的 operationId，不含空白，不超过 128 个字符
 * @param httpMethod 请求方法：GET、POST、PUT、PATCH、DELETE、HEAD 或 OPTIONS
 * @param path 以 / 开头的路由模板，不含查询串、片段和空白，不超过 255 个字符
 */
public record ApiDTO(
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "\\S+")
    String operationId,
    @NotNull
    @Pattern(regexp = ApiDTO.HTTP_METHOD)
    String httpMethod,
    @NotNull
    @Size(max = 255)
    @Pattern(regexp = ApiDTO.PATH)
    String path) {

  /** 允许登记的请求方法。 */
  public static final String HTTP_METHOD = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS";

  /** 路由模板格式：以 / 开头，不含 ?、# 和空白。 */
  public static final String PATH = "^/[^?#\\s]*$";
}
