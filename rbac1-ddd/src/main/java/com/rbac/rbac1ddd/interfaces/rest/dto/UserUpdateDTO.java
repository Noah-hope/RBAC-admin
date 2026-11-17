package com.rbac.rbac1ddd.interfaces.rest.dto;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * 修改用户的请求体（UserUpdate），只修改出现的字段，显式传 null 视为请求不合法。
 *
 * <p>全部不填时由应用层按业务规则拒绝，提示与 rbac1-structured 相同。
 */
@Getter
@Setter
@NoArgsConstructor
public class UserUpdateDTO {

  /** 新登录账号，1～32 个字符。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @CodePointLength(min = 1, max = 32)
  private String username;

  /** 新姓名，1～24 个字符。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @CodePointLength(min = 1, max = 24)
  private String name;

  /** 调动后的部门 ID，仅限总管理员。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Positive
  @Max(MAX_ID)
  private Long departmentId;

  /** 新账号状态。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Pattern(regexp = "ACTIVE|DISABLED")
  private String status;

  /** 任命（true）或撤销（false）部门管理员，仅限总管理员。 */
  @JsonSetter(nulls = Nulls.FAIL)
  private Boolean departmentAdministrator;
}
