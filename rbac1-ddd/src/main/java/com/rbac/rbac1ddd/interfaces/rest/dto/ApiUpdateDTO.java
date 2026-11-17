package com.rbac.rbac1ddd.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 修改接口的请求体（ApiUpdate），只修改出现的字段；显式传 null 视为请求不合法。
 *
 * <p>三项都不填时由应用层按业务规则拒绝，提示与 rbac1-structured 相同。
 */
@Getter
@Setter
@NoArgsConstructor
public class ApiUpdateDTO {

  /** 新的 operationId。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Size(min = 1, max = 128)
  @Pattern(regexp = "\\S+")
  private String operationId;

  /** 新的请求方法。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Pattern(regexp = ApiDTO.HTTP_METHOD)
  private String httpMethod;

  /** 新的路由模板。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Size(max = 255)
  @Pattern(regexp = ApiDTO.PATH)
  private String path;
}
