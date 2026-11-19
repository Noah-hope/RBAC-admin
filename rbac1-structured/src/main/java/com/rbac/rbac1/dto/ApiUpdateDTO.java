package com.rbac.rbac1.dto;

import cn.edu.xmu.clonefactory.CopyNotNullTo;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.rbac.rbac1.entity.Api;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 修改接口的请求体：只修改提交的字段，由 CloneFactory 的 copyNotNull 拷贝到 {@link Api}。
 *
 * <p>显式传 null 视为请求不合法。
 */
@CopyNotNullTo(Api.class)
@Getter
@Setter
@NoArgsConstructor
public class ApiUpdateDTO {

  /** 新 operationId。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Size(min = 1, max = 128)
  @Pattern(regexp = "\\S+")
  private String operationId;

  /** 新请求方法。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
  private String httpMethod;

  /** 新路由模板。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Size(max = 255)
  @Pattern(regexp = "^/[^?#\\s]*$")
  private String path;
}
