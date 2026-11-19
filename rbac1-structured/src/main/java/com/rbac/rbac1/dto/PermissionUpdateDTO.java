package com.rbac.rbac1.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
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
 * 修改权限的请求体，只修改出现的字段，显式传 null 视为请求不合法。
 *
 * <p>全部不填时由 Service 按业务规则拒绝。
 */
@Getter
@Setter
@NoArgsConstructor
public class PermissionUpdateDTO {

  /** 关联接口的完整 ID 列表，[] 表示解除全部关联。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @UniqueElements
  private List<@NotNull @Positive Long> apiIds;

  /** 新名称，1～24 个字符。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @CodePointLength(min = 1, max = 24)
  private String name;

  /** 新权限标识。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @CodePointLength(max = 64)
  @Pattern(regexp = Formats.PERMISSION_CODE)
  private String code;
}
