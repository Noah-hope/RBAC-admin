package com.rbac.rbac1ddd.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.rbac.rbac1ddd.interfaces.rest.Formats;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * 修改权限的请求体（PermissionUpdate），只修改出现的字段；显式传 null 视为请求不合法。
 *
 * <p>三项都不填时由应用层按业务规则拒绝，提示与 rbac1-structured 相同。
 */
@Getter
@Setter
@NoArgsConstructor
public class PermissionUpdateDTO {

  /** 新的完整接口关联，空列表表示清空关联。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @UniqueElements
  private List<@NotNull @Positive Long> apiIds;

  /** 新名称，1～24 个字符。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @CodePointLength(min = 1, max = 24)
  private String name;

  /** 新权限标识，支持「资源:操作」及课程短码。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @CodePointLength(max = 64)
  @Pattern(regexp = Formats.PERMISSION_CODE)
  private String code;
}
