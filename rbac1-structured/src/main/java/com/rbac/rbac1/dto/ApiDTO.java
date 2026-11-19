package com.rbac.rbac1.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.rbac.rbac1.entity.Api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 登记接口的请求体；三个字段与 {@link Api} 同名，由 CloneFactory 拷贝。 */
@CopyTo(Api.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiDTO {

  /** operationId，不含空白，不超过 128 个字符。 */
  @NotBlank
  @Size(max = 128)
  @Pattern(regexp = "\\S+")
  private String operationId;

  /** 请求方法。 */
  @NotNull
  @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
  private String httpMethod;

  /** 以 / 开头的路由模板，不含查询串和片段。 */
  @NotNull
  @Size(max = 255)
  @Pattern(regexp = "^/[^?#\\s]*$")
  private String path;
}
